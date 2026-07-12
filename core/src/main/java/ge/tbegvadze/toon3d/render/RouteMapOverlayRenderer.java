package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;

import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;

import java.nio.IntBuffer;
import ge.tbegvadze.toon3d.render.routeicons.IconPainter;
import ge.tbegvadze.toon3d.render.routeicons.IconPainterRegistry;
import ge.tbegvadze.toon3d.render.routeicons.RegionCrestPainter;
import ge.tbegvadze.toon3d.render.routeicons.RegionCrestPainterRegistry;
import ge.tbegvadze.toon3d.render.routeicons.RouteGlyphs;
import ge.tbegvadze.toon3d.render.routeicons.RouteIconBootstrap;
import ge.tbegvadze.toon3d.route.DangerTier;
import ge.tbegvadze.toon3d.route.MysteryOutcome;
import ge.tbegvadze.toon3d.route.NodeStatus;
import ge.tbegvadze.toon3d.route.NodeTypeDefinition;
import ge.tbegvadze.toon3d.route.NodeTypeRegistry;
import ge.tbegvadze.toon3d.route.RouteMap;
import ge.tbegvadze.toon3d.route.RouteNode;
import ge.tbegvadze.toon3d.route.RouteNodeType;
import ge.tbegvadze.toon3d.route.RouteRegion;
import ge.tbegvadze.toon3d.route.RouteRegistries;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The "FACILITY NAV" console overlay (route-map order-4). A full-screen, procedurally-drawn UAC
 * navigation terminal shown over the frozen 3D world during {@code RunPhase.ROUTE_SELECT}: a
 * holographic branching schematic on a grimy CRT bezel. Every pixel is ShapeRenderer / BitmapFont —
 * no textures, no image files (the product-owner constraint).
 *
 * <p><b>Glow without a FrameBuffer.</b> The idea sketches the node/conduit bloom as an additive
 * FrameBuffer pass, but this project deliberately keeps all per-frame FrameBuffer work out of the
 * game loop: {@code FrameBuffer.end()} resets the GL viewport and would corrupt the {@link
 * com.badlogic.gdx.utils.viewport.FitViewport} letterbox for the rest of the frame (see the note in
 * {@code WeaponHudRenderer}). So the bloom is faked with concentric additive-blended discs drawn
 * straight through this renderer's single {@link ShapeRenderer} — crisp, viewport-safe, allocation-free.
 *
 * <p><b>Order boundaries.</b> This file owns the LOOK. Touch hit-testing / gesture handling is
 * order-6, and the real per-type node icon painters are order-5 (a small placeholder glyph is drawn
 * here meanwhile). Because there is no touch selection yet, the console auto-focuses the first
 * candidate and, after {@link RouteMapConstants#AUTO_SELECT_HOLD_SECONDS}, drives its own COMMIT
 * timeline; {@code World} watches {@link #isReadyForSelection()} / {@link #isCommitFinished()} and
 * fires the existing {@code RouteMapOverlay} present/commit pipeline.
 *
 * <p>Zero per-frame allocation: the whole layout (node positions, scales, alphas, truncated labels,
 * the region banner, the depth readout) is computed once in {@link #present(RouteMap)};
 * {@link #render(OrthographicCamera)} only reads it and advances float timers.
 */
public final class RouteMapOverlayRenderer implements Renderable, Disposable {

    private enum Phase { INACTIVE, OPENING, IDLE, COMMIT, DONE }

    /**
     * Outcome of a discrete tap (order-6), returned to {@code World} so it can fire the matching
     * out-of-renderer feedback (console-thunk shake, haptics) and drive the CANCEL/back-out policy.
     * Focus, engage-arming and invalid flashes are handled inside this renderer; the enum only tells
     * World what just happened.
     */
    public enum PointerResult { NONE, FOCUS_CHANGED, ENGAGED, INVALID, FRAMING_TOGGLED, CANCEL }

    private static final String TITLE_TEXT   = "FACILITY NAV  —  SELECT VECTOR";
    private static final String CANCEL_TEXT  = "◀ CANCEL";
    private static final String ENGAGE_TEXT  = "ENGAGE ▶";
    private static final String UAC_STENCIL  = "UAC//NAV";
    private static final String LOCK_GLYPH   = "◇";
    private static final String UNKNOWN_REGION = "UNKNOWN SECTOR";
    private static final String TOGGLE_MAP_TEXT    = "MAP";
    private static final String TOGGLE_DETAIL_TEXT = "DETAIL";
    private static final String SCANNER_TEXT       = "SCANNER ACTIVE";

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   layout;                 // used only outside render()
    private final RouteMapColorPalette palette;
    private final NodeTypeRegistry     nodeTypes;
    private final IconPainterRegistry       iconPainters;   // order-5: per-type node icon painters
    private final RegionCrestPainterRegistry regionCrests;  // order-5: per-region title-plate crests

    private final float worldWidth  = Constants.WORLD_WIDTH;
    private final float worldHeight = Constants.WORLD_HEIGHT;
    private final float viewportCenterX = Constants.WORLD_WIDTH / 2f;

    // ---- Pre-allocated per-node layout (filled in present, read in render) --
    private final int      capacity   = RouteMapConstants.MAP_MAX_DISPLAY_NODES;
    private final RouteNode[] nodeRef  = new RouteNode[capacity];
    private final float[]  centerX     = new float[capacity];
    private final float[]  centerY     = new float[capacity];
    private final float[]  baseScale   = new float[capacity];
    private final float[]  baseAlpha   = new float[capacity];
    private final int[]    layerRow    = new int[capacity];   // -1 history .. LOOKAHEAD (0 = current)
    private final String[] labelText   = new String[capacity];
    private final float[]  labelX      = new float[capacity];
    private int displayCount = 0;

    // ---- Selection / commit state ------------------------------------------
    private RouteNode focusNode;        // auto-focused candidate (order-6 makes this touch-driven)
    private RouteNode committedNode;
    private Phase   phase = Phase.INACTIVE;
    private float   overlayTimeSeconds = 0f;   // free-running clock for idle loops
    private float   phaseTimeSeconds   = 0f;   // resets on each phase change
    private boolean redAlert = false;

    // ---- Interaction / UX state (order-6) — kept here, NOT in RouteMap -------
    private RouteMap map;                       // cached so a framing toggle can re-lay out
    private boolean  forcedChoice;              // single forced pick (BOSS / REGION_GATE): auto-focused
    private String   engageText = RouteMapConstants.ENGAGE_TEXT_DEFAULT;
    private float    engageLabelXDynamic;
    private float    lastFocusTapTime = -10f;   // for the optional double-tap-to-engage shortcut
    private RouteNode lastTapNode;

    // Vertical pan of the map viewport (planning ahead); 0 = decision-layer home framing.
    private float    panY;
    private float    panVelocityY;
    private float    minPanY;                   // most-negative pan that frames the topmost row
    private float    lastDeltaTime = 1f / 60f;  // captured in update() for drag-velocity estimation
    private boolean  dragging;
    private boolean  mapFraming;                // false = DETAIL (default), true = MAP (fit region)

    // Invalid-tap red flash (a rect + countdown), so a mis-tap is never a silent no-response.
    private float    invalidFlashTimer;
    private float    invalidFlashX, invalidFlashY, invalidFlashWidth, invalidFlashHeight;
    private float    invalidFlashRadius;

    // Long-range scanner reveal (relic hook — order-14 triggers it).
    private boolean  scannerActive;
    private float    scanSweepTimer;            // counts down while the un-ghost sweep plays

    // ---- Precomputed header / legend text ----------------------------------
    private float  titleBaselineX;
    private String depthText   = "";
    private float  depthTextX;
    private String regionText  = "";
    private float  regionTextX;
    private Color  regionTint;
    private String regionThemeId;                  // order-5: selects the title-plate crest painter
    private String legendText  = "";
    private float  legendTextX;
    private float  cancelLabelX, cancelLabelY, engageLabelX, engageLabelY;
    private float  toggleLabelXMap, toggleLabelXDetail, toggleLabelY;

    // ---- Per-conduit scratch (no allocation in render) ---------------------
    private float conduitStartX, conduitStartY, conduitControlX, conduitControlY, conduitEndX, conduitEndY;

    // ---- Map-band scissor scratch (no allocation in render) ----------------
    // The scrolling map content (glow / conduits / cards / icons / labels) is clipped to the map
    // viewport band so a panned-away or bottom-most row can never bleed over the title plate, the
    // confirm bar, or the hazard trim (the reported "nodes overlay the UI when scrolled" issue).
    private final Rectangle mapClipArea    = new Rectangle();
    private final Rectangle mapClipScissor = new Rectangle();
    // Holds the live GL viewport rect (glGetIntegerv) so the scissor is computed against the actual
    // FitViewport letterbox sub-rectangle, not the full window — otherwise the clip misaligns on any
    // aspect ratio that pillar/letterboxes (e.g. a 19.5:9 phone).
    private final IntBuffer glViewportBuffer = BufferUtils.newIntBuffer(16);

    public RouteMapOverlayRenderer() {
        shapes  = new ShapeRenderer();
        // The icon/crest painters switch Filled<->Line mid-batch via shapes.set(...) (see
        // RouteIconSupport.fill/stroke). That call requires autoShapeType; without it the first
        // painter throws "autoShapeType must be enabled" the instant the console opens on portal entry.
        shapes.setAutoShapeType(true);
        batch   = new SpriteBatch();
        font    = new BitmapFont();
        layout  = new GlyphLayout();
        palette = new RouteMapColorPalette();
        nodeTypes = RouteRegistries.nodeTypes();
        iconPainters = new IconPainterRegistry();
        RouteIconBootstrap.registerIconPainters(iconPainters);
        regionCrests = new RegionCrestPainterRegistry();
        RouteIconBootstrap.registerRegionCrests(regionCrests);
        font.getData().markupEnabled = false;
        regionTint = palette.holoCyan;
        precomputeStaticText();
    }

    /** Title + confirm-bar label positions never move, so they are laid out once. */
    private void precomputeStaticText() {
        font.getData().setScale(RouteMapConstants.TITLE_TEXT_SCALE);
        layout.setText(font, TITLE_TEXT);
        titleBaselineX = viewportCenterX - layout.width / 2f;

        float barMidY = (RouteMapConstants.CONFIRM_BAR_BOTTOM_Y + RouteMapConstants.CONFIRM_BAR_TOP_Y) / 2f;
        float cancelLeft = RouteMapConstants.CONFIRM_BUTTON_MARGIN;
        float engageLeft = worldWidth - RouteMapConstants.CONFIRM_BUTTON_MARGIN
                         - RouteMapConstants.CONFIRM_BUTTON_WIDTH;
        font.getData().setScale(RouteMapConstants.CONFIRM_LABEL_SCALE);
        layout.setText(font, CANCEL_TEXT);
        cancelLabelX = cancelLeft + RouteMapConstants.CONFIRM_BUTTON_WIDTH / 2f - layout.width / 2f;
        cancelLabelY = barMidY + layout.height / 2f;
        layout.setText(font, ENGAGE_TEXT);
        engageLabelX = engageLeft + RouteMapConstants.CONFIRM_BUTTON_WIDTH / 2f - layout.width / 2f;
        engageLabelY = barMidY + layout.height / 2f;

        // MAP / DETAIL toggle labels — both fixed strings, laid out once so render() never touches
        // the shared GlyphLayout (keeps the render path allocation-free).
        font.getData().setScale(RouteMapConstants.FRAMING_TOGGLE_LABEL_SCALE);
        float toggleCenterX = toggleLeft() + RouteMapConstants.FRAMING_TOGGLE_WIDTH / 2f;
        float toggleCenterY = toggleBottom() + RouteMapConstants.FRAMING_TOGGLE_HEIGHT / 2f;
        layout.setText(font, TOGGLE_MAP_TEXT);
        toggleLabelXMap = toggleCenterX - layout.width / 2f;
        layout.setText(font, TOGGLE_DETAIL_TEXT);
        toggleLabelXDetail = toggleCenterX - layout.width / 2f;
        toggleLabelY = toggleCenterY + layout.height / 2f;
    }

    // -------------------------------------------------------------------------
    // Presentation (order-3 present/commit contract; the visual side of it)
    // -------------------------------------------------------------------------

    /**
     * Boots the console for a fresh route decision: reads the map's current node, the selectable next
     * layer, and the distant layers for the perspective fade, then lays every visible node out. Does
     * NOT mutate route state — selection/commit flow back through {@code World.commitRouteNode}.
     */
    public void present(RouteMap map) {
        this.map = map;
        displayCount = 0;
        focusNode = null;
        committedNode = null;
        phase = Phase.OPENING;
        overlayTimeSeconds = 0f;
        phaseTimeSeconds = 0f;
        // Reset all interaction state — a fresh decision starts un-panned in DETAIL framing.
        panY = 0f;
        panVelocityY = 0f;
        dragging = false;
        mapFraming = false;
        invalidFlashTimer = 0f;
        scanSweepTimer = 0f;
        lastTapNode = null;
        lastFocusTapTime = -10f;
        if (map == null || map.getCurrent() == null) {
            return;
        }

        applyRevealPolicy(map);

        // Two-step safety: a normal branch starts with NO focus (the player must tap a card first).
        // A FORCED convergence (one candidate, or an all-BOSS layer) is auto-focused so the single
        // deliberate ENGAGE still reads as a beat without a redundant focus tap.
        List<RouteNode> candidates = map.getSelectableNext();
        forcedChoice = candidates.size() == 1 || map.isBossNext();
        focusNode = forcedChoice && !candidates.isEmpty() ? candidates.get(0) : null;
        resolveEngageText(candidates);

        buildLayout(map);
        buildHeaderText(map);
    }

    /**
     * v1 fog policy ({@link RouteMapConstants#REVEAL_CURRENT_REGION}): un-ghost every node in the
     * CURRENT region so the act is a real plan, while the NEXT region stays '?' until its REGION_GATE
     * is crossed. MYSTERY nodes still render as '?' by icon (their payload is the surprise, order-9).
     */
    private void applyRevealPolicy(RouteMap map) {
        if (!RouteMapConstants.REVEAL_CURRENT_REGION) {
            return;
        }
        RouteNode current = map.getCurrent();
        List<RouteRegion> regions = map.getRegions();
        for (int index = 0; index < regions.size(); index++) {
            if (regions.get(index).containsDepth(current.depth)) {
                map.revealRegion(index);
                return;
            }
        }
    }

    /** Picks the ENGAGE-button copy: forced BOSS / REGION_GATE get their own convergence verbs. */
    private void resolveEngageText(List<RouteNode> candidates) {
        engageText = RouteMapConstants.ENGAGE_TEXT_DEFAULT;
        if (forcedChoice && !candidates.isEmpty()) {
            RouteNodeType type = candidates.get(0).type;
            if (type == RouteNodeType.BOSS) {
                engageText = RouteMapConstants.ENGAGE_TEXT_BOSS;
            } else if (type == RouteNodeType.REGION_GATE) {
                engageText = RouteMapConstants.ENGAGE_TEXT_REGION_GATE;
            }
        }
        font.getData().setScale(RouteMapConstants.CONFIRM_LABEL_SCALE);
        layout.setText(font, engageText);
        float engageLeft = worldWidth - RouteMapConstants.CONFIRM_BUTTON_MARGIN
                         - RouteMapConstants.CONFIRM_BUTTON_WIDTH;
        engageLabelXDynamic = engageLeft + RouteMapConstants.CONFIRM_BUTTON_WIDTH / 2f - layout.width / 2f;
    }

    /** Locates the current layer, then lays out a window of rows around it (history .. lookahead). */
    private void buildLayout(RouteMap map) {
        List<List<RouteNode>> layers = map.getLayers();
        RouteNode current = map.getCurrent();

        int currentLayerIndex = -1;
        for (int index = 0; index < layers.size() && currentLayerIndex < 0; index++) {
            if (layers.get(index).contains(current)) {
                currentLayerIndex = index;
            }
        }
        if (currentLayerIndex < 0) {
            return;
        }

        boolean hasHistory = currentLayerIndex > 0;
        int firstLayer = hasHistory ? currentLayerIndex - 1 : currentLayerIndex;
        int lastLayer  = Math.min(layers.size() - 1,
                                  currentLayerIndex + RouteMapConstants.MAP_LOOKAHEAD_LAYERS);

        // MAP framing compresses the whole schematic so more of the region fits at once; DETAIL keeps
        // the roomy default. Applied to both the row gap and every card scale.
        float framing = mapFraming ? RouteMapConstants.MAP_FRAMING_COMPRESSION : 1f;
        float layerGap = RouteMapConstants.MAP_LAYER_GAP * framing;
        float topRowCenterY = RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y;   // tracks the highest laid row
        float topRowScale   = 1f;

        for (int layerIndex = firstLayer; layerIndex <= lastLayer; layerIndex++) {
            int rowFromBottom = layerIndex - firstLayer;
            int relativeRow   = layerIndex - currentLayerIndex;   // -1 .. LOOKAHEAD
            float rowY = RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y + RouteMapConstants.MAP_BOTTOM_PAD
                       + rowFromBottom * layerGap;
            float scale = scaleForRow(relativeRow) * framing;
            float alpha = alphaForRow(relativeRow);
            topRowCenterY = rowY;
            topRowScale   = scale;

            List<RouteNode> row = new ArrayList<>(layers.get(layerIndex));
            row.sort(Comparator.comparingInt(node -> node.laneIndex));
            int nodesInRow = row.size();
            float spacing  = RouteMapConstants.MAP_LANE_GAP * scale;
            float rowSpan  = (nodesInRow - 1) * spacing;
            float startX   = viewportCenterX - rowSpan / 2f;

            for (int columnIndex = 0; columnIndex < nodesInRow; columnIndex++) {
                if (displayCount >= capacity) break;
                RouteNode node = row.get(columnIndex);
                int slot = displayCount++;
                nodeRef[slot]   = node;
                centerX[slot]   = startX + columnIndex * spacing;
                centerY[slot]   = rowY;
                baseScale[slot] = scale;
                baseAlpha[slot] = alpha;
                layerRow[slot]  = relativeRow;
                buildNodeLabel(slot, node, scale);
            }
        }

        // Pan clamp: the most-negative pan needed to bring the topmost row fully into view (0 if the
        // whole map already fits, so pan is a no-op). Home framing is panY == 0.
        float topRowTop = topRowCenterY + RouteMapConstants.NODE_CARD_HEIGHT * topRowScale / 2f;
        minPanY = Math.min(0f,
                (RouteMapConstants.MAP_VIEWPORT_TOP_Y - RouteMapConstants.PAN_TOP_MARGIN) - topRowTop);
    }

    private float scaleForRow(int relativeRow) {
        if (relativeRow <= 1) return 1f;
        return Math.max(RouteMapConstants.DISTANT_MIN_SCALE,
                        1f - (relativeRow - 1) * RouteMapConstants.DISTANT_SCALE_STEP);
    }

    private float alphaForRow(int relativeRow) {
        if (relativeRow == -1) return RouteMapConstants.HISTORY_ALPHA;
        if (relativeRow <= 1)  return 1f;
        return Math.max(RouteMapConstants.DISTANT_MIN_ALPHA,
                        1f - (relativeRow - 1) * RouteMapConstants.DISTANT_ALPHA_STEP);
    }

    /** Resolves and truncates a node's label to the card width, centring it (fog shows a '?'). */
    private void buildNodeLabel(int slot, RouteNode node, float scale) {
        String name;
        if (!node.revealed) {
            name = "?";
        } else {
            NodeTypeDefinition definition = nodeTypes.get(node.type);
            name = node.resolvedLabel != null ? node.resolvedLabel : definition.displayName();
        }
        float maxWidth = RouteMapConstants.NODE_CARD_WIDTH * scale - 14f;
        String truncated = truncateToWidth(name, RouteMapConstants.NODE_LABEL_SCALE, maxWidth);
        labelText[slot] = truncated;
        font.getData().setScale(RouteMapConstants.NODE_LABEL_SCALE);
        layout.setText(font, truncated);
        labelX[slot] = centerX[slot] - layout.width / 2f;
    }

    /** Builds the depth readout, region banner (+ its tint), and the focused node's legend hint. */
    private void buildHeaderText(RouteMap map) {
        RouteNode current = map.getCurrent();
        depthText = "DEPTH " + current.depth + "  ->  " + (current.depth + 1);
        font.getData().setScale(RouteMapConstants.DEPTH_TEXT_SCALE);
        layout.setText(font, depthText);
        depthTextX = worldWidth - 40f - layout.width;

        RouteRegion region = null;
        for (RouteRegion candidate : map.getRegions()) {
            if (candidate.containsDepth(current.depth)) {
                region = candidate;
                break;
            }
        }
        regionText = region != null ? region.displayName() : UNKNOWN_REGION;
        regionThemeId = region != null ? region.themeId() : null;
        regionTint = region != null ? palette.regionTint(region.themeId()) : palette.holoCyan;
        regionTextX = RouteMapConstants.REGION_TEXT_LEFT_X;   // cleared of the crest badge (order-5)

        String hint;
        if (focusNode == null) {
            hint = "";
        } else if (!focusNode.revealed) {
            hint = "Unknown vector. Contents obscured.";
        } else if (focusNode.type == RouteNodeType.MYSTERY && scannerActive) {
            // Order-9: a scanned MYSTERY reveals its category TONE (informed gambling) — never the
            // exact outcome, which is rolled only at entry. Derived from the same fixed node seed.
            hint = MysteryOutcome.scanToneLabel(focusNode.nodeSeed);
        } else {
            NodeTypeDefinition definition = nodeTypes.get(focusNode.type);
            hint = focusNode.resolvedHint != null ? focusNode.resolvedHint : definition.hintLine();
        }
        legendText = hint;
        font.getData().setScale(RouteMapConstants.LEGEND_TEXT_SCALE);
        layout.setText(font, legendText);
        legendTextX = viewportCenterX - layout.width / 2f;
    }

    private String truncateToWidth(String text, float scale, float maxWidth) {
        font.getData().setScale(scale);
        layout.setText(font, text);
        if (layout.width <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        for (int length = text.length() - 1; length > 0; length--) {
            String candidate = text.substring(0, length) + ellipsis;
            layout.setText(font, candidate);
            if (layout.width <= maxWidth) {
                return candidate;
            }
        }
        return ellipsis;
    }

    // -------------------------------------------------------------------------
    // Timeline (advanced by World's paused ROUTE_SELECT branch)
    // -------------------------------------------------------------------------

    /** Advances the OPENING -> IDLE -> COMMIT -> DONE timeline plus the interaction physics. */
    public void update(float deltaTime) {
        if (phase == Phase.INACTIVE || phase == Phase.DONE) {
            return;
        }
        lastDeltaTime = Math.max(1f / 240f, deltaTime);
        overlayTimeSeconds += deltaTime;
        phaseTimeSeconds   += deltaTime;
        if (phase == Phase.OPENING && phaseTimeSeconds >= RouteMapConstants.OPENING_SECONDS) {
            phase = Phase.IDLE;
            phaseTimeSeconds = 0f;
        } else if (phase == Phase.COMMIT && phaseTimeSeconds >= RouteMapConstants.COMMIT_SECONDS) {
            phase = Phase.DONE;
        }

        if (invalidFlashTimer > 0f) {
            invalidFlashTimer = Math.max(0f, invalidFlashTimer - deltaTime);
        }
        if (scanSweepTimer > 0f) {
            scanSweepTimer = Math.max(0f, scanSweepTimer - deltaTime);
        }
        updatePan(deltaTime);
    }

    /** Momentum + spring/clamp/snap-back for the vertical map pan (only while browsing, phase IDLE). */
    private void updatePan(float deltaTime) {
        if (dragging) {
            return;   // the finger owns the pan; physics resumes on release
        }
        panY += panVelocityY * deltaTime;
        // Exponential-ish momentum decay.
        panVelocityY -= panVelocityY * RouteMapConstants.PAN_MOMENTUM_DECAY * deltaTime;
        if (Math.abs(panVelocityY) < RouteMapConstants.PAN_MOMENTUM_MIN_SPEED) {
            panVelocityY = 0f;
        }
        // Spring toward the valid range; snap all the way home when released near the decision layers.
        float target = MathUtils.clamp(panY, minPanY, 0f);
        if (Math.abs(panY) < RouteMapConstants.PAN_SNAP_BACK_THRESHOLD) {
            target = 0f;
        }
        float ease = Math.min(1f, RouteMapConstants.PAN_SNAP_BACK_SPEED * deltaTime);
        panY = GameMath.lerp(panY, target, ease);
    }

    /**
     * Retired auto-driver (order-4 had no touch, order-6 does): selection is now a deliberate tap on
     * ENGAGE, never a timer. Kept returning {@code false} so any legacy caller can never auto-commit.
     */
    public boolean isReadyForSelection() {
        return false;
    }

    /** The candidate the console is highlighting (order-6 will let touch move this). */
    public RouteNode getFocusNode() {
        return focusNode;
    }

    /** Kicks off the COMMIT flare for the chosen node. */
    public void beginCommit(RouteNode chosen) {
        committedNode = chosen;
        phase = Phase.COMMIT;
        phaseTimeSeconds = 0f;
    }

    /** True when the COMMIT flare has played out and World may fire the actual node commit. */
    public boolean isCommitFinished() {
        return phase == Phase.DONE;
    }

    public RouteNode getCommittedNode() {
        return committedNode;
    }

    /** Ties the bezel trim into the facility red-alert state for continuity. */
    public void setRedAlert(boolean redAlert) {
        this.redAlert = redAlert;
    }

    // -------------------------------------------------------------------------
    // Touch interaction (order-6) — coords are ALREADY unprojected to world space by World.
    // -------------------------------------------------------------------------

    /** Records a pointer-down for the pan gesture; a tap is decided by World on release. */
    public void onPointerDown(float worldX, float worldY) {
        dragging = false;
        panVelocityY = 0f;
    }

    /**
     * Applies a vertical drag delta to the pan (World passes the frame-to-frame Y change once the
     * finger has moved past the drag threshold). Clamped with a little overscroll slack; the delta
     * also feeds the fling-momentum estimate. Horizontal drag is ignored (the map only pans vertically).
     */
    public void panByDrag(float deltaY) {
        if (!RouteMapConstants.MAP_PAN_ENABLED) {
            return;
        }
        dragging = true;
        panY = MathUtils.clamp(panY + deltaY,
                minPanY - RouteMapConstants.PAN_MAX_OVERSCROLL,
                RouteMapConstants.PAN_MAX_OVERSCROLL);
        // Instantaneous velocity estimate for the release fling.
        panVelocityY = deltaY / lastDeltaTime;
    }

    /** Ends a drag; the last drag velocity now decays as momentum (see {@link #updatePan}). */
    public void onDragReleased() {
        dragging = false;
    }

    /**
     * Handles a discrete tap (no drag). Returns what happened so World can fire the out-of-renderer
     * feedback (thunk shake) and honour the CANCEL policy. Ignored unless the console is IDLE — during
     * OPENING and COMMIT the input is locked (mirrors PlayerController's animation lock) so a double
     * engage can never double-descend.
     */
    public PointerResult onTap(float worldX, float worldY) {
        if (phase != Phase.IDLE) {
            return PointerResult.NONE;
        }

        // ENGAGE — gated: only a focused node can be engaged; otherwise it flashes invalid.
        if (pointInButton(worldX, worldY, engageButtonLeft())) {
            if (focusNode != null) {
                engageFocused();
                return PointerResult.ENGAGED;
            }
            flashInvalidButton(engageButtonLeft());
            return PointerResult.INVALID;
        }

        // CANCEL — policy in RouteMapConstants.ALLOW_PORTAL_BACKOUT (default: no true cancel).
        if (pointInButton(worldX, worldY, RouteMapConstants.CONFIRM_BUTTON_MARGIN)) {
            return RouteMapConstants.ALLOW_PORTAL_BACKOUT ? PointerResult.CANCEL : PointerResult.NONE;
        }

        // MAP / DETAIL framing toggle.
        if (pointInRect(worldX, worldY, toggleLeft(), toggleBottom(),
                        RouteMapConstants.FRAMING_TOGGLE_WIDTH, RouteMapConstants.FRAMING_TOGGLE_HEIGHT)) {
            toggleFraming();
            return PointerResult.FRAMING_TOGGLED;
        }

        // Node cards: a selectable candidate focuses (or double-tap engages); anything else is invalid.
        int candidateSlot = hitTestCandidate(worldX, worldY);
        if (candidateSlot >= 0) {
            RouteNode node = nodeRef[candidateSlot];
            if (RouteMapConstants.DOUBLE_TAP_ENGAGE && node == focusNode
                    && overlayTimeSeconds - lastFocusTapTime <= RouteMapConstants.DOUBLE_TAP_WINDOW_SECONDS) {
                engageFocused();
                return PointerResult.ENGAGED;
            }
            setFocus(node);
            lastTapNode = node;
            lastFocusTapTime = overlayTimeSeconds;
            return PointerResult.FOCUS_CHANGED;
        }

        int otherSlot = hitTestAnyNode(worldX, worldY);
        if (otherSlot >= 0) {
            flashInvalidNode(otherSlot);
            return PointerResult.INVALID;
        }

        // Tap on empty space clears the focus (forced choices stay locked in).
        if (!forcedChoice) {
            focusNode = null;
            buildHeaderText(map);
        }
        return PointerResult.NONE;
    }

    private void engageFocused() {
        buzz(RouteMapConstants.HAPTIC_ENGAGE_MS);
        // AUDIO HOOK: heavier "commit / power-down" flare blip would fire here.
        beginCommit(focusNode);
    }

    private void setFocus(RouteNode node) {
        if (node == focusNode) {
            return;
        }
        focusNode = node;
        buzz(RouteMapConstants.HAPTIC_FOCUS_MS);
        // AUDIO HOOK: soft "select" blip would fire here.
        buildHeaderText(map);   // slides the focused node's hint into the legend strip
    }

    private void toggleFraming() {
        mapFraming = !mapFraming;
        panY = 0f;
        panVelocityY = 0f;
        displayCount = 0;
        buildLayout(map);
        buildHeaderText(map);
        buzz(RouteMapConstants.HAPTIC_FOCUS_MS);
    }

    /** Red border flash + dull haptic buzz for a mis-tap; never a silent no-response (mobile clarity). */
    private void flashInvalidNode(int slot) {
        float scale = baseScale[slot];
        invalidFlashWidth  = RouteMapConstants.NODE_CARD_WIDTH * scale;
        invalidFlashHeight = RouteMapConstants.NODE_CARD_HEIGHT * scale;
        invalidFlashX = centerX[slot] - invalidFlashWidth / 2f;
        invalidFlashY = drawCenterY(slot) - invalidFlashHeight / 2f;
        invalidFlashRadius = RouteMapConstants.NODE_CARD_CORNER_RADIUS * scale;
        invalidFlashTimer = RouteMapConstants.INVALID_FLASH_SECONDS;
        buzz(RouteMapConstants.HAPTIC_INVALID_MS);
    }

    private void flashInvalidButton(float buttonLeft) {
        invalidFlashX = buttonLeft;
        invalidFlashY = RouteMapConstants.CONFIRM_BAR_BOTTOM_Y + 6f;
        invalidFlashWidth  = RouteMapConstants.CONFIRM_BUTTON_WIDTH;
        invalidFlashHeight = RouteMapConstants.CONFIRM_BAR_TOP_Y - RouteMapConstants.CONFIRM_BAR_BOTTOM_Y - 12f;
        invalidFlashRadius = 6f;
        invalidFlashTimer = RouteMapConstants.INVALID_FLASH_SECONDS;
        buzz(RouteMapConstants.HAPTIC_INVALID_MS);
    }

    /**
     * Optional Android haptic, flag-gated so the desktop dev build no-ops safely. A missing/denied
     * VIBRATE permission (or any backend that rejects the call) must never crash a tap on the route
     * map — this is cosmetic feedback, not gameplay-critical, so failures are swallowed silently.
     */
    private void buzz(int milliseconds) {
        if (!RouteMapConstants.HAPTICS_ENABLED || Gdx.input == null) {
            return;
        }
        try {
            Gdx.input.vibrate(milliseconds);
        } catch (Exception ignored) {
            // Never let cosmetic haptic feedback crash node selection (e.g. missing VIBRATE permission).
        }
    }

    // ---- Hit-testing (padded thumb rects; computed from the cached layout) --

    /** Returns the slot of a tapped AVAILABLE candidate card, or -1. Padded to a thumb-min target. */
    private int hitTestCandidate(float worldX, float worldY) {
        for (int index = 0; index < displayCount; index++) {
            RouteNode node = nodeRef[index];
            if ((node.status != NodeStatus.AVAILABLE && !isBossTestClickable(node)) || !isNodeDrawable(index)) {
                continue;
            }
            if (pointInPaddedCard(worldX, worldY, index)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * TEMPORARY TESTING helper ({@link RouteMapConstants#BOSS_TEST_ALWAYS_CLICKABLE}): treats a drawn
     * BOSS node as a tappable candidate even when it is not a legal AVAILABLE next pick, so the boss
     * fight can be reached from any route-select screen. Returns {@code false} for every node when the
     * debug flag is off, leaving the normal selection rules untouched.
     */
    private boolean isBossTestClickable(RouteNode node) {
        return RouteMapConstants.BOSS_TEST_ALWAYS_CLICKABLE
                && node != null
                && node.type == RouteNodeType.BOSS;
    }

    /** Returns the slot of ANY tapped drawn card (for invalid feedback on non-selectable taps), or -1. */
    private int hitTestAnyNode(float worldX, float worldY) {
        for (int index = 0; index < displayCount; index++) {
            if (isNodeDrawable(index) && pointInPaddedCard(worldX, worldY, index)) {
                return index;
            }
        }
        return -1;
    }

    private boolean pointInPaddedCard(float worldX, float worldY, int index) {
        float scale = baseScale[index];
        float width  = Math.max(RouteMapConstants.NODE_CARD_WIDTH * scale, RouteMapConstants.MIN_NODE_HIT_SIZE)
                     + 2f * RouteMapConstants.NODE_HIT_PADDING;
        float height = Math.max(RouteMapConstants.NODE_CARD_HEIGHT * scale, RouteMapConstants.MIN_NODE_HIT_SIZE)
                     + 2f * RouteMapConstants.NODE_HIT_PADDING;
        return pointInRect(worldX, worldY, centerX[index] - width / 2f,
                           drawCenterY(index) - height / 2f, width, height);
    }

    private boolean pointInButton(float worldX, float worldY, float buttonLeft) {
        float bottom = RouteMapConstants.CONFIRM_BAR_BOTTOM_Y + 6f;
        float height = RouteMapConstants.CONFIRM_BAR_TOP_Y - RouteMapConstants.CONFIRM_BAR_BOTTOM_Y - 12f;
        return pointInRect(worldX, worldY, buttonLeft, bottom, RouteMapConstants.CONFIRM_BUTTON_WIDTH, height);
    }

    private static boolean pointInRect(float pointX, float pointY,
                                       float left, float bottom, float width, float height) {
        return pointX >= left && pointX <= left + width
            && pointY >= bottom && pointY <= bottom + height;
    }

    private float engageButtonLeft() {
        return worldWidth - RouteMapConstants.CONFIRM_BUTTON_MARGIN - RouteMapConstants.CONFIRM_BUTTON_WIDTH;
    }

    private float toggleLeft() {
        return worldWidth - RouteMapConstants.FRAMING_TOGGLE_MARGIN - RouteMapConstants.FRAMING_TOGGLE_WIDTH;
    }

    private float toggleBottom() {
        return RouteMapConstants.MAP_VIEWPORT_TOP_Y - RouteMapConstants.FRAMING_TOGGLE_MARGIN
             - RouteMapConstants.FRAMING_TOGGLE_HEIGHT;
    }

    // ---- Long-range scanner reveal (relic hook, order-14) -------------------

    /**
     * Turns the "SCANNER ACTIVE" readout on/off. The relic that actually holds/consumes a scanner is
     * defined by the meta/relic idea (order-14); order-6 only provides this plumbing + the visual.
     */
    public void setScannerActive(boolean active) {
        this.scannerActive = active;
    }

    /**
     * Reveals the NEXT region early (FTL long-range-scanner fantasy) and plays the scan-sweep wipe that
     * un-ghosts the newly revealed cards. No-op unless the scanner feature is compiled in. Re-lays out
     * so the freshly revealed types are drawn.
     */
    public void triggerScanReveal() {
        if (!RouteMapConstants.SCANNER_REVEAL_ENABLED || map == null || map.getCurrent() == null) {
            return;
        }
        int currentRegion = -1;
        List<RouteRegion> regions = map.getRegions();
        for (int index = 0; index < regions.size(); index++) {
            if (regions.get(index).containsDepth(map.getCurrent().depth)) {
                currentRegion = index;
                break;
            }
        }
        if (currentRegion < 0) {
            return;   // No region contains the current depth — cannot determine the "next" one.
        }
        int nextRegion = currentRegion + 1;
        if (nextRegion < regions.size()) {
            map.revealRegion(nextRegion);
            scannerActive = true;
            scanSweepTimer = RouteMapConstants.SCAN_SWEEP_SECONDS;
            displayCount = 0;
            buildLayout(map);
            buildHeaderText(map);
        }
    }

    // -------------------------------------------------------------------------
    // Render — the eight ordered passes
    // -------------------------------------------------------------------------

    @Override
    public void render(OrthographicCamera camera) {
        if (phase == Phase.INACTIVE) {
            return;
        }
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        drawScrim();                 // 1. world scrim
        drawScreenPanel();           // 1b. opaque backdrop over the map band (stops world bleed)
        drawBezel();                 // 2. bezel / chrome / hazard trim
        drawRegionCrestPass();       // 2b. region crest badge on the title plate (order-5)

        // Passes 3-6 + the node labels are the SCROLLING map content: clipped to the map viewport band
        // so a panned or bottom-most row is hard-cut at the chrome instead of overlaying it.
        boolean clipped = beginMapClip(camera);
        drawGlowPass();              // 3. additive hologram bloom (node halos + conduit cores)
        drawConduitPass();           // 4. curved conduits + travelling pulses
        drawNodePass();              // 5. card bodies + borders + state decor + risk pips
        drawIconPass();              // 6. procedural per-type node icons + affix tags (order-5)
        drawNodeLabels();            // 7a. per-node labels (clipped with the cards they belong to)
        endMapClip(clipped);

        drawInvalidFlashPass();      // 6b. red mis-tap flash (order-6) — node OR confirm-bar button
        drawScanSweepPass();         // 6c. long-range scanner sweep wipe (order-6)
        drawChromeText();            // 7b. title / depth / region / legend hint / confirm labels
        drawCrtPass();               // 8. scanlines + vignette + flicker + sync flash (all off now)

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Enables a scissor clip to the map viewport band ([0,worldWidth] × [bottom,top]) for the scrolling
     * map content. Returns whether the clip was actually pushed (false only if the band mapped to a
     * zero-area rect, in which case nothing needs drawing — callers still call {@link #endMapClip} which
     * no-ops). Uses pre-allocated Rectangles so the render path stays allocation-free.
     */
    private boolean beginMapClip(OrthographicCamera camera) {
        mapClipArea.set(0f, RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y, worldWidth,
                        RouteMapConstants.MAP_VIEWPORT_TOP_Y - RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y);
        // Read the actual GL viewport (the FitViewport's letterboxed sub-rect) so the projected scissor
        // lines up on pillar/letterboxed aspect ratios instead of assuming a full-window viewport.
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, glViewportBuffer);
        float viewportX      = glViewportBuffer.get(0);
        float viewportY      = glViewportBuffer.get(1);
        float viewportWidth  = glViewportBuffer.get(2);
        float viewportHeight = glViewportBuffer.get(3);
        ScissorStack.calculateScissors(camera, viewportX, viewportY, viewportWidth, viewportHeight,
                                       batch.getTransformMatrix(), mapClipArea, mapClipScissor);
        return ScissorStack.pushScissors(mapClipScissor);
    }

    private void endMapClip(boolean clipped) {
        if (clipped) {
            ScissorStack.popScissors();
        }
    }

    // ---- Pass 1: scrim ------------------------------------------------------

    private void drawScrim() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(palette.scrimNavy.r, palette.scrimNavy.g, palette.scrimNavy.b,
                        RouteMapConstants.OVERLAY_SCRIM_ALPHA);
        shapes.rect(0f, 0f, worldWidth, worldHeight);
        shapes.end();
    }

    // ---- Pass 1b: opaque map backdrop --------------------------------------

    /**
     * Fills the map viewport band with a FULLY-OPAQUE dark backdrop so the frozen 3D world never ghosts
     * through the schematic (at the old 0.90 alpha ~10% of the weapon/HUD text and walls still bled in
     * and washed out the cards — the "screen-like effect" the player asked to remove). Drawn with NORMAL
     * alpha blending AFTER the scrim and BEFORE the additive glow pass so the hologram bloom still adds
     * on top of a consistent dark backdrop.
     */
    private void drawScreenPanel() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        setShape(palette.screenBlack, RouteMapConstants.MAP_SCREEN_FILL_ALPHA);
        shapes.rect(0f, RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y, worldWidth,
                    RouteMapConstants.MAP_VIEWPORT_TOP_Y - RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y);
        shapes.end();
    }

    // ---- Pass 2: bezel / chrome --------------------------------------------

    private void drawBezel() {
        Color trim = trimColor();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // Title plate + confirm-bar bands (brushed steel). The legend strip band was retired; its hint
        // line now sits in the confirm bar's centre gap.
        setShape(palette.steel, 0.92f);
        shapes.rect(0f, RouteMapConstants.TITLE_PLATE_BOTTOM_Y, worldWidth,
                    RouteMapConstants.TITLE_PLATE_TOP_Y - RouteMapConstants.TITLE_PLATE_BOTTOM_Y);
        setShape(palette.steel, 0.95f);
        shapes.rect(0f, RouteMapConstants.CONFIRM_BAR_BOTTOM_Y, worldWidth,
                    RouteMapConstants.CONFIRM_BAR_TOP_Y - RouteMapConstants.CONFIRM_BAR_BOTTOM_Y);

        // Confirm-bar buttons. CANCEL is a dim no-op affordance; ENGAGE is GATED — dim/disabled until
        // a node is focused, then it lights and ready-pulses (order-6 two-step selection safety).
        float barBottom = RouteMapConstants.CONFIRM_BAR_BOTTOM_Y + 6f;
        float barHeight = RouteMapConstants.CONFIRM_BAR_TOP_Y - RouteMapConstants.CONFIRM_BAR_BOTTOM_Y - 12f;
        setShape(palette.holoDim, 0.45f);
        shapes.rect(RouteMapConstants.CONFIRM_BUTTON_MARGIN, barBottom,
                    RouteMapConstants.CONFIRM_BUTTON_WIDTH, barHeight);
        float engageAlpha;
        if (focusNode == null) {
            engageAlpha = RouteMapConstants.ENGAGE_DISABLED_ALPHA;
        } else {
            float pulse = 0.5f + 0.5f * MathUtils.sin(overlayTimeSeconds * RouteMapConstants.ENGAGE_PULSE_SPEED);
            engageAlpha = RouteMapConstants.ENGAGE_ENABLED_ALPHA + 0.28f * pulse;
        }
        setShape(palette.holoCyan, engageAlpha);
        shapes.rect(engageButtonLeft(), barBottom, RouteMapConstants.CONFIRM_BUTTON_WIDTH, barHeight);

        // MAP / DETAIL framing toggle (top-right of the map viewport).
        setShape(mapFraming ? palette.holoCyan : palette.holoDim, 0.4f);
        shapes.rect(toggleLeft(), toggleBottom(),
                    RouteMapConstants.FRAMING_TOGGLE_WIDTH, RouteMapConstants.FRAMING_TOGGLE_HEIGHT);

        // Hazard chevron trim along the top and bottom of the map viewport. The bottom trim sits on
        // top of the confirm bar, filling the gap up to the (lowered) map viewport bottom edge.
        drawHazardTrim(RouteMapConstants.TITLE_PLATE_BOTTOM_Y - RouteMapConstants.HAZARD_TRIM_HEIGHT, trim);
        drawHazardTrim(RouteMapConstants.CONFIRM_BAR_TOP_Y, trim);

        // Rivets in the plate corners.
        setShape(palette.rivet, 0.9f);
        float inset = RouteMapConstants.RIVET_INSET;
        shapes.circle(inset, RouteMapConstants.TITLE_PLATE_BOTTOM_Y + inset, RouteMapConstants.RIVET_RADIUS);
        shapes.circle(worldWidth - inset, RouteMapConstants.TITLE_PLATE_BOTTOM_Y + inset, RouteMapConstants.RIVET_RADIUS);
        shapes.circle(inset, RouteMapConstants.TITLE_PLATE_TOP_Y - inset, RouteMapConstants.RIVET_RADIUS);
        shapes.circle(worldWidth - inset, RouteMapConstants.TITLE_PLATE_TOP_Y - inset, RouteMapConstants.RIVET_RADIUS);
        shapes.end();

        // Frame border (line pass) — the screen edge + band separators, trim-tinted.
        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(RouteMapConstants.FRAME_BORDER_WIDTH);
        setShape(trim, 0.8f);
        shapes.rect(2f, 2f, worldWidth - 4f, worldHeight - 4f);
        setShape(trim, 0.5f);
        shapes.line(0f, RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y, worldWidth, RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y);
        shapes.line(0f, RouteMapConstants.MAP_VIEWPORT_TOP_Y, worldWidth, RouteMapConstants.MAP_VIEWPORT_TOP_Y);
        // MAP / DETAIL toggle outline.
        setShape(mapFraming ? palette.holoCyan : palette.textDim, 0.85f);
        shapes.rect(toggleLeft(), toggleBottom(),
                    RouteMapConstants.FRAMING_TOGGLE_WIDTH, RouteMapConstants.FRAMING_TOGGLE_HEIGHT);
        Gdx.gl.glLineWidth(1f);
        shapes.end();
    }

    /** A row of filled parallelograms forming a diagonal hazard stripe. */
    private void drawHazardTrim(float bottomY, Color trim) {
        float width = RouteMapConstants.HAZARD_CHEVRON_WIDTH;
        float height = RouteMapConstants.HAZARD_TRIM_HEIGHT;
        float skew = height;
        boolean lit = true;
        for (float leftX = 0f; leftX < worldWidth; leftX += width) {
            if (lit) {
                setShape(trim, 0.55f);
            } else {
                setShape(palette.steel, 0.7f);
            }
            lit = !lit;
            // parallelogram as two triangles
            shapes.triangle(leftX, bottomY, leftX + width, bottomY, leftX + width + skew, bottomY + height);
            shapes.triangle(leftX, bottomY, leftX + width + skew, bottomY + height, leftX + skew, bottomY + height);
        }
    }

    // ---- Pass 3: additive glow ---------------------------------------------

    private void drawGlowPass() {
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int index = 0; index < displayCount; index++) {
            RouteNode node = nodeRef[index];
            if (!isNodeDrawable(index)) continue;
            boolean glows = node.status == NodeStatus.AVAILABLE || node.status == NodeStatus.CURRENT;
            if (!glows) continue;
            Color accent = accentFor(node);
            float boost = 1f;
            if (node == focusNode) {
                float pulse = 0.5f + 0.5f * MathUtils.sin(overlayTimeSeconds * RouteMapConstants.FOCUS_PULSE_SPEED);
                boost = RouteMapConstants.FOCUS_GLOW_BOOST * (0.85f + 0.15f * pulse);
            }
            if (phase == Phase.COMMIT && node == committedNode) {
                boost = RouteMapConstants.FOCUS_GLOW_BOOST * 1.6f;
            }
            drawGlowDisc(centerX[index], drawCenterY(index),
                         RouteMapConstants.GLOW_RADIUS * drawScale(index), accent, effectiveAlpha(index) * boost);
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Concentric additive rings, brightest at the core, faking a soft bloom without a FrameBuffer. */
    private void drawGlowDisc(float centerXValue, float centerYValue, float radius, Color color, float alpha) {
        int rings = RouteMapConstants.GLOW_RING_COUNT;
        for (int ring = 0; ring < rings; ring++) {
            float ringFraction = (rings - ring) / (float) rings;   // outer -> small, inner -> 1
            float ringRadius = radius * ringFraction;
            shapes.setColor(color.r, color.g, color.b, RouteMapConstants.GLOW_RING_ALPHA * alpha);
            shapes.circle(centerXValue, centerYValue, ringRadius);
        }
    }

    // ---- Pass 4: conduits + pulses -----------------------------------------

    private void drawConduitPass() {
        // Line cores first.
        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(RouteMapConstants.CONDUIT_WIDTH);
        for (int index = 0; index < displayCount; index++) {
            RouteNode source = nodeRef[index];
            if (!isNodeDrawable(index)) continue;
            for (RouteNode target : source.outgoing) {
                int targetSlot = displayIndexOf(target);
                if (targetSlot < 0 || !isNodeDrawable(targetSlot)) continue;
                if (layerRow[targetSlot] <= layerRow[index]) continue;   // forward edges only
                drawConduitLine(index, targetSlot, source, target);
            }
        }
        Gdx.gl.glLineWidth(1f);
        shapes.end();

        // Travelling pulse dots (additive) over the hot conduits.
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int index = 0; index < displayCount; index++) {
            RouteNode source = nodeRef[index];
            if (!isNodeDrawable(index)) continue;
            boolean hot = source.status == NodeStatus.CURRENT || source.status == NodeStatus.AVAILABLE;
            if (!hot) continue;
            for (RouteNode target : source.outgoing) {
                int targetSlot = displayIndexOf(target);
                if (targetSlot < 0 || !isNodeDrawable(targetSlot)) continue;
                if (layerRow[targetSlot] <= layerRow[index]) continue;
                drawConduitPulse(index, targetSlot, source);
            }
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void prepareConduit(int sourceSlot, int targetSlot) {
        float sourceHalf = RouteMapConstants.NODE_CARD_HEIGHT * drawScale(sourceSlot) / 2f;
        float targetHalf = RouteMapConstants.NODE_CARD_HEIGHT * drawScale(targetSlot) / 2f;
        conduitStartX = centerX[sourceSlot];
        conduitStartY = drawCenterY(sourceSlot) + sourceHalf;
        conduitEndX   = centerX[targetSlot];
        conduitEndY   = drawCenterY(targetSlot) - targetHalf;
        float sign = (conduitEndX == conduitStartX) ? 0f : (conduitEndX > conduitStartX ? 1f : -1f);
        conduitControlX = (conduitStartX + conduitEndX) / 2f + sign * RouteMapConstants.CONDUIT_CURVE_OFFSET;
        conduitControlY = (conduitStartY + conduitEndY) / 2f;
    }

    private void drawConduitLine(int sourceSlot, int targetSlot, RouteNode source, RouteNode target) {
        prepareConduit(sourceSlot, targetSlot);
        boolean historySpine = source.status == NodeStatus.VISITED
                             && (target.status == NodeStatus.CURRENT || target.status == NodeStatus.VISITED);
        boolean hot = source.status == NodeStatus.CURRENT || source.status == NodeStatus.AVAILABLE;
        boolean committedEdge = phase == Phase.COMMIT
                             && source.status == NodeStatus.CURRENT && target == committedNode;

        Color color;
        float alpha;
        if (committedEdge) {
            color = palette.holoCyan;
            float flare = 0.6f + 0.4f * MathUtils.sin(overlayTimeSeconds * 24f);
            alpha = 1f * flare;
        } else if (historySpine) {
            color = palette.holoCyan;
            alpha = RouteMapConstants.HISTORY_SPINE_ALPHA;
        } else if (hot) {
            color = accentFor(source);
            alpha = 0.85f;
        } else {
            color = palette.holoDim;
            alpha = RouteMapConstants.CONDUIT_DIM_ALPHA;
        }
        alpha *= flicker();

        int segments = RouteMapConstants.CONDUIT_SEGMENTS;
        float previousX = conduitStartX;
        float previousY = conduitStartY;
        shapes.setColor(color.r, color.g, color.b, alpha);
        for (int step = 1; step <= segments; step++) {
            float parameter = step / (float) segments;
            float pointX = GameMath.quadraticBezier(conduitStartX, conduitControlX, conduitEndX, parameter);
            float pointY = GameMath.quadraticBezier(conduitStartY, conduitControlY, conduitEndY, parameter);
            shapes.line(previousX, previousY, pointX, pointY);
            previousX = pointX;
            previousY = pointY;
        }
    }

    private void drawConduitPulse(int sourceSlot, int targetSlot, RouteNode source) {
        prepareConduit(sourceSlot, targetSlot);
        float parameter = (overlayTimeSeconds * RouteMapConstants.PULSE_SPEED) % 1f;
        float pointX = GameMath.quadraticBezier(conduitStartX, conduitControlX, conduitEndX, parameter);
        float pointY = GameMath.quadraticBezier(conduitStartY, conduitControlY, conduitEndY, parameter);
        Color accent = accentFor(source);
        float alpha = 0.9f * flicker();
        shapes.setColor(accent.r, accent.g, accent.b, alpha);
        shapes.circle(pointX, pointY, RouteMapConstants.PULSE_DOT_RADIUS);
        shapes.setColor(accent.r, accent.g, accent.b, alpha * 0.4f);
        shapes.circle(pointX, pointY, RouteMapConstants.PULSE_DOT_RADIUS * 2f);
    }

    // ---- Pass 5: node cards -------------------------------------------------

    private void drawNodePass() {
        // Bodies + risk pips (filled). Icons are their own pass (order-5) so painters can freely
        // switch fill/line without disturbing the card body/border passes.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int index = 0; index < displayCount; index++) {
            if (!isNodeDrawable(index)) continue;
            drawCardBody(index);
            drawRiskPips(index);
            drawStateDecorFilled(index);
        }
        shapes.end();

        // Borders + reticle + selection ring (line).
        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(RouteMapConstants.NODE_CARD_BORDER_WIDTH);
        for (int index = 0; index < displayCount; index++) {
            if (!isNodeDrawable(index)) continue;
            drawCardBorder(index);
            drawStateDecorLine(index);
        }
        Gdx.gl.glLineWidth(1f);
        shapes.end();
    }

    // ---- Pass 2b: region crest ---------------------------------------------

    /** Draws the current region's heraldic crest badge on the title plate (order-5). */
    private void drawRegionCrestPass() {
        RegionCrestPainter crest = regionCrests.get(regionThemeId);
        float crestCenterY = (RouteMapConstants.TITLE_PLATE_BOTTOM_Y
                            + RouteMapConstants.TITLE_PLATE_TOP_Y) / 2f;
        crestTint.set(regionTint.r, regionTint.g, regionTint.b, 0.95f * flicker());
        Gdx.gl.glLineWidth(RouteMapConstants.ICON_LINE_WIDTH);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        crest.paint(shapes, RouteMapConstants.CREST_CENTER_X, crestCenterY,
                    RouteMapConstants.CREST_SIZE, crestTint, overlayTimeSeconds);
        shapes.set(ShapeRenderer.ShapeType.Filled);
        shapes.end();
        Gdx.gl.glLineWidth(1f);
    }

    // ---- Pass 6: procedural node icons (order-5) ---------------------------

    /**
     * Draws each visible node's procedural icon via its registered {@link IconPainter} (order-5),
     * plus an affix corner-tag for any node carrying a {@code NodeAffix} (order-9). Un-revealed nodes
     * always draw the MYSTERY "?" icon regardless of their hidden real type (the fog rule). Painters
     * switch fill/line via {@code shapes.set(...)} inside this single begin()/end().
     */
    private void drawIconPass() {
        Gdx.gl.glLineWidth(RouteMapConstants.ICON_LINE_WIDTH);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int index = 0; index < displayCount; index++) {
            if (!isNodeDrawable(index)) continue;
            drawNodeIcon(index);
            drawAffixTag(index);
        }
        shapes.set(ShapeRenderer.ShapeType.Filled);
        shapes.end();
        Gdx.gl.glLineWidth(1f);
    }

    // ---- Pass 6b: invalid-tap red flash (order-6) --------------------------

    /** A short, fading red border around the last mis-tapped target — never a silent no-response. */
    private void drawInvalidFlashPass() {
        if (invalidFlashTimer <= 0f) {
            return;
        }
        float alpha = RouteMapConstants.INVALID_FLASH_ALPHA
                    * (invalidFlashTimer / RouteMapConstants.INVALID_FLASH_SECONDS);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(RouteMapConstants.NODE_CARD_BORDER_WIDTH + 1.5f);
        setShape(palette.dangerRed, alpha);
        drawRoundedRectOutline(invalidFlashX, invalidFlashY, invalidFlashWidth, invalidFlashHeight,
                               invalidFlashRadius);
        Gdx.gl.glLineWidth(1f);
        shapes.end();
    }

    // ---- Pass 6c: long-range scanner sweep (order-6) -----------------------

    /** A bright horizontal wipe rising up the viewport as the scanner un-ghosts the next region. */
    private void drawScanSweepPass() {
        if (scanSweepTimer <= 0f) {
            return;
        }
        float progress = 1f - scanSweepTimer / RouteMapConstants.SCAN_SWEEP_SECONDS;
        float sweepY = GameMath.lerp(RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y,
                                     RouteMapConstants.MAP_VIEWPORT_TOP_Y, progress);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(palette.holoCyan.r, palette.holoCyan.g, palette.holoCyan.b, 0.85f);
        shapes.rect(0f, sweepY - 2f, worldWidth, 4f);
        shapes.setColor(palette.holoCyan.r, palette.holoCyan.g, palette.holoCyan.b, 0.18f);
        shapes.rect(0f, RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y, worldWidth,
                    sweepY - RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y);
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Resolves and paints one node's icon; the master opacity is folded into the accent's alpha. */
    private void drawNodeIcon(int index) {
        RouteNode node = nodeRef[index];
        float scale = drawScale(index);
        float iconSize = iconSizeFor(node) * scale;
        float iconCenterY = drawCenterY(index)
                          + RouteMapConstants.NODE_CARD_HEIGHT * scale * RouteMapConstants.ICON_CENTER_Y_FRACTION;
        float alpha = effectiveAlpha(index) / baseAlpha(index);

        // Fog rule: an un-revealed node always shows the MYSTERY '?' icon (order-9 reveal drives this).
        String iconPainterId;
        Color accent;
        if (!node.revealed) {
            iconPainterId = "icon_mystery";
            accent = palette.accentFor("mystery");
        } else {
            NodeTypeDefinition definition = nodeTypes.get(node.type);
            iconPainterId = definition.iconPainterId();
            accent = (node.status == NodeStatus.LOCKED || node.status == NodeStatus.BYPASSED)
                   ? palette.lockedAccent() : accentFor(node);
        }
        IconPainter painter = iconPainters.get(iconPainterId);
        iconTint.set(accent.r, accent.g, accent.b, MathUtils.clamp(alpha, 0f, 1f));
        painter.paint(shapes, batch, centerX[index], iconCenterY, iconSize, iconTint, overlayTimeSeconds);
    }

    /** Convergence set-pieces (BOSS / REGION_GATE) draw a slightly larger icon than normal nodes. */
    private float iconSizeFor(RouteNode node) {
        if (node.type == null) return RouteMapConstants.ICON_BOX_SIZE;
        switch (node.type) {
            case BOSS:        return RouteMapConstants.ICON_BOX_SIZE * RouteMapConstants.ICON_BOSS_SIZE_SCALE;
            case REGION_GATE: return RouteMapConstants.ICON_BOX_SIZE * RouteMapConstants.ICON_GATE_SIZE_SCALE;
            default:          return RouteMapConstants.ICON_BOX_SIZE;
        }
    }

    /** A tiny accent diamond in the card's top-left corner when the node carries an affix (order-9). */
    private void drawAffixTag(int index) {
        RouteNode node = nodeRef[index];
        if (node.affix == null || !node.revealed) return;
        float scale = drawScale(index);
        float width = RouteMapConstants.NODE_CARD_WIDTH * scale;
        float height = RouteMapConstants.NODE_CARD_HEIGHT * scale;
        float tagX = centerX[index] - width / 2f + RouteMapConstants.AFFIX_TAG_INSET * scale;
        float tagY = drawCenterY(index) + height / 2f - RouteMapConstants.AFFIX_TAG_INSET * scale;
        float alpha = effectiveAlpha(index) / baseAlpha(index);
        RouteGlyphs.affixTag(shapes, tagX, tagY, RouteMapConstants.AFFIX_TAG_SIZE * scale,
                             palette.hazardAmber, 0.95f * alpha);
    }

    private void drawCardBody(int index) {
        RouteNode node = nodeRef[index];
        float scale = drawScale(index);
        float width = RouteMapConstants.NODE_CARD_WIDTH * scale;
        float height = RouteMapConstants.NODE_CARD_HEIGHT * scale;
        float left = centerX[index] - width / 2f;
        float bottom = drawCenterY(index) - height / 2f;
        Color body = cardBaseColor(node);
        setShape(body, RouteMapConstants.NODE_CARD_BODY_ALPHA * effectiveAlpha(index) / baseAlpha(index));
        drawRoundedRect(left, bottom, width, height, RouteMapConstants.NODE_CARD_CORNER_RADIUS * scale);
    }

    private void drawCardBorder(int index) {
        RouteNode node = nodeRef[index];
        float scale = drawScale(index);
        float width = RouteMapConstants.NODE_CARD_WIDTH * scale;
        float height = RouteMapConstants.NODE_CARD_HEIGHT * scale;
        float left = centerX[index] - width / 2f;
        float bottom = drawCenterY(index) - height / 2f;
        float borderAlpha = (node.status == NodeStatus.CURRENT || node.status == NodeStatus.AVAILABLE) ? 1f : 0.7f;
        setShape(cardBaseColor(node), borderAlpha * effectiveAlpha(index) / baseAlpha(index));
        drawRoundedRectOutline(left, bottom, width, height, RouteMapConstants.NODE_CARD_CORNER_RADIUS * scale);
    }

    /** 1-4 dot risk meter in the card's bottom-right corner (colour ramp green->amber->red). */
    private void drawRiskPips(int index) {
        RouteNode node = nodeRef[index];
        if (node.status == NodeStatus.LOCKED || !node.revealed) return;
        NodeTypeDefinition definition = nodeTypes.get(node.type);
        DangerTier tier = definition.dangerTier();
        float scale = drawScale(index);
        float width = RouteMapConstants.NODE_CARD_WIDTH * scale;
        float height = RouteMapConstants.NODE_CARD_HEIGHT * scale;
        float rightX = centerX[index] + width / 2f - RouteMapConstants.RISK_PIP_INSET * scale;
        float pipY = drawCenterY(index) - height / 2f + RouteMapConstants.RISK_PIP_INSET * scale;
        float radius = RouteMapConstants.RISK_PIP_RADIUS * scale;
        float step = (RouteMapConstants.RISK_PIP_RADIUS * 2f + RouteMapConstants.RISK_PIP_GAP) * scale;
        float alpha = effectiveAlpha(index) / baseAlpha(index);
        RouteGlyphs.riskPips(shapes, rightX, pipY, radius, step,
                             palette.pipCount(tier), palette.pipColor(tier), palette.holoDim,
                             0.95f * alpha, 0.35f * alpha);
    }

    private void drawStateDecorFilled(int index) {
        RouteNode node = nodeRef[index];
        if (node.status == NodeStatus.VISITED) {
            float scale = drawScale(index);
            float alpha = 0.5f * effectiveAlpha(index) / baseAlpha(index);
            RouteGlyphs.checkGlyph(shapes, centerX[index], drawCenterY(index), 34f * scale,
                                   palette.medGreen, alpha, 3f * scale);
        }
    }

    private void drawStateDecorLine(int index) {
        RouteNode node = nodeRef[index];
        float alpha = effectiveAlpha(index) / baseAlpha(index);
        if (node.status == NodeStatus.CURRENT) {
            // rotating "YOU ARE HERE" diamond reticle (shared glyph)
            float radius = RouteMapConstants.RETICLE_RADIUS * drawScale(index);
            float spin = overlayTimeSeconds * RouteMapConstants.RETICLE_SPIN_SPEED;
            RouteGlyphs.hereReticle(shapes, centerX[index], drawCenterY(index), radius, spin,
                                    palette.holoCyan, 0.9f * alpha);
        } else if (node == focusNode && (node.status == NodeStatus.AVAILABLE || isBossTestClickable(node))) {
            // selection ring + pulse
            float scale = drawScale(index);
            float width = RouteMapConstants.NODE_CARD_WIDTH * scale + RouteMapConstants.SELECTION_RING_PAD * 2f;
            float height = RouteMapConstants.NODE_CARD_HEIGHT * scale + RouteMapConstants.SELECTION_RING_PAD * 2f;
            float left = centerX[index] - width / 2f;
            float bottom = drawCenterY(index) - height / 2f;
            float pulse = 0.6f + 0.4f * MathUtils.sin(overlayTimeSeconds * RouteMapConstants.FOCUS_PULSE_SPEED);
            setShape(palette.holoCyan, pulse * alpha);
            drawRoundedRectOutline(left, bottom, width, height,
                                   RouteMapConstants.NODE_CARD_CORNER_RADIUS * scale + RouteMapConstants.SELECTION_RING_PAD);
        }
    }

    // ---- Pass 7a: node labels (clipped to the map band with their cards) ----

    private void drawNodeLabels() {
        batch.begin();
        font.getData().setScale(RouteMapConstants.NODE_LABEL_SCALE);
        for (int index = 0; index < displayCount; index++) {
            if (!isNodeDrawable(index)) continue;
            RouteNode node = nodeRef[index];
            float scale = drawScale(index);
            float labelBaselineY = drawCenterY(index) - RouteMapConstants.NODE_CARD_HEIGHT * scale * 0.22f;
            Color labelColor = node.status == NodeStatus.LOCKED ? palette.textDim : palette.textBright;
            float alpha = effectiveAlpha(index) / baseAlpha(index);
            // drop shadow
            setFont(palette.scrimNavy, 0.8f * alpha);
            font.draw(batch, labelText[index], labelX[index] + 1.5f, labelBaselineY - 1.5f);
            setFont(labelColor, alpha);
            font.draw(batch, labelText[index], labelX[index], labelBaselineY);
        }
        batch.end();
    }

    // ---- Pass 7b: chrome text (unclipped — lives on the bezel, never scrolls) --

    private void drawChromeText() {
        batch.begin();

        font.getData().setScale(RouteMapConstants.TITLE_TEXT_SCALE);
        setFont(palette.textBright, 1f);
        float titleY = (RouteMapConstants.TITLE_PLATE_BOTTOM_Y + RouteMapConstants.TITLE_PLATE_TOP_Y) / 2f + 8f;
        font.draw(batch, TITLE_TEXT, titleBaselineX, titleY);

        font.getData().setScale(RouteMapConstants.REGION_TEXT_SCALE);
        setFont(regionTint, 1f);
        font.draw(batch, regionText, regionTextX, titleY);

        font.getData().setScale(RouteMapConstants.DEPTH_TEXT_SCALE);
        setFont(palette.textDim, 1f);
        font.draw(batch, depthText, depthTextX, titleY);

        // UAC stencil on the title plate.
        font.getData().setScale(0.7f);
        setFont(palette.textDim, 0.55f);
        font.draw(batch, UAC_STENCIL, regionTextX, RouteMapConstants.TITLE_PLATE_BOTTOM_Y + 20f);

        // Legend hint — now rendered in the confirm bar's centre gap (between CANCEL and ENGAGE), since
        // the dedicated legend strip band was retired to give the map more vertical room.
        if (!legendText.isEmpty()) {
            font.getData().setScale(RouteMapConstants.LEGEND_TEXT_SCALE);
            float legendY = (RouteMapConstants.CONFIRM_BAR_BOTTOM_Y + RouteMapConstants.CONFIRM_BAR_TOP_Y) / 2f + 6f;
            setFont(regionTint, 0.95f);
            font.draw(batch, legendText, legendTextX, legendY);
        }

        // Confirm-bar labels. ENGAGE copy is dynamic (forced-node verbs) and dims when un-armed.
        font.getData().setScale(RouteMapConstants.CONFIRM_LABEL_SCALE);
        setFont(palette.textDim, 0.9f);
        font.draw(batch, CANCEL_TEXT, cancelLabelX, cancelLabelY);
        setFont(palette.holoCyan, focusNode != null ? 1f : 0.4f);
        font.draw(batch, engageText, engageLabelXDynamic, engageLabelY);

        // MAP / DETAIL framing toggle label (positions precomputed — no GlyphLayout in render()).
        font.getData().setScale(RouteMapConstants.FRAMING_TOGGLE_LABEL_SCALE);
        setFont(mapFraming ? palette.holoCyan : palette.textBright, 0.95f);
        font.draw(batch, mapFraming ? TOGGLE_DETAIL_TEXT : TOGGLE_MAP_TEXT,
                  mapFraming ? toggleLabelXDetail : toggleLabelXMap, toggleLabelY);

        // Long-range scanner readout (relic hook, order-6) — top-left inside the map band.
        if (scannerActive) {
            setFont(palette.holoCyan, 0.85f);
            font.draw(batch, SCANNER_TEXT, RouteMapConstants.REGION_TEXT_LEFT_X,
                      RouteMapConstants.MAP_VIEWPORT_TOP_Y - 10f);
        }

        batch.end();
    }

    // ---- Pass 8: CRT --------------------------------------------------------

    private void drawCrtPass() {
        // Every CRT layer is disabled (alphas zeroed in RouteMapConstants) per the player's request to
        // remove the "screen-like effect" entirely. Each block is alpha-guarded, so the whole pass
        // no-ops at the current values while staying re-enableable from constants alone. Bail early when
        // nothing is drawable so the pass never opens an empty ShapeRenderer batch.
        boolean anyScanlines = RouteMapConstants.SCANLINE_ALPHA > 0f;
        boolean anyVignette  = RouteMapConstants.VIGNETTE_ALPHA > 0f;
        boolean anySyncFlash = RouteMapConstants.SYNC_FLASH_ALPHA > 0f;
        if (!anyScanlines && !anyVignette && !anySyncFlash) {
            return;
        }

        float viewportBottom = RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y;
        float viewportTop    = RouteMapConstants.MAP_VIEWPORT_TOP_Y;

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Scanlines — dark 1px lines scrolling upward across the map viewport only.
        if (anyScanlines) {
            float scroll = (overlayTimeSeconds * RouteMapConstants.SCANLINE_SCROLL_SPEED)
                         % RouteMapConstants.SCANLINE_SPACING;
            shapes.setColor(0f, 0f, 0f, RouteMapConstants.SCANLINE_ALPHA);
            for (float lineY = viewportBottom + scroll; lineY < viewportTop; lineY += RouteMapConstants.SCANLINE_SPACING) {
                shapes.rect(0f, lineY, worldWidth, 1f);
            }
        }

        // Vignette — darkened edge bands around the viewport. Zeroed: the bands painted over the
        // top/bottom node rows and outer lanes (the reported "black border cutting the cards"); the
        // opaque backdrop now supplies the display feel. Pass skipped when alpha <= 0.
        if (anyVignette) {
            float band = RouteMapConstants.VIGNETTE_BAND;
            shapes.setColor(0f, 0f, 0f, RouteMapConstants.VIGNETTE_ALPHA);
            shapes.rect(0f, viewportBottom, band, viewportTop - viewportBottom);
            shapes.rect(worldWidth - band, viewportBottom, band, viewportTop - viewportBottom);
            shapes.rect(0f, viewportTop - band, worldWidth, band);
            shapes.rect(0f, viewportBottom, worldWidth, band);
        }

        // Rare sync flash across the whole hologram.
        if (anySyncFlash) {
            float syncPhase = overlayTimeSeconds % RouteMapConstants.SYNC_FLASH_INTERVAL;
            if (syncPhase < 0.06f) {
                shapes.setColor(palette.holoCyan.r, palette.holoCyan.g, palette.holoCyan.b,
                                RouteMapConstants.SYNC_FLASH_ALPHA);
                shapes.rect(0f, viewportBottom, worldWidth, viewportTop - viewportBottom);
            }
        }

        shapes.end();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** During the OPENING boot sweep, a node only appears once the scan line has passed its Y. */
    private boolean isRevealedBySweep(int index) {
        if (phase != Phase.OPENING) return true;
        float sweepFraction = Math.min(1f, phaseTimeSeconds / RouteMapConstants.OPENING_SECONDS);
        float sweepY = GameMath.lerp(RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y,
                                     RouteMapConstants.MAP_VIEWPORT_TOP_Y, sweepFraction);
        return centerY[index] <= sweepY;
    }

    /** Focus / commit scale-up folded onto the node's base perspective scale. */
    private float drawScale(int index) {
        float scale = baseScale[index];
        RouteNode node = nodeRef[index];
        if (node == focusNode && (node.status == NodeStatus.AVAILABLE || isBossTestClickable(node))
                && phase != Phase.COMMIT) {
            scale *= RouteMapConstants.FOCUS_SCALE;
        }
        if (phase == Phase.COMMIT && node == committedNode) {
            float grow = 1f + 0.08f * Math.min(1f, phaseTimeSeconds / RouteMapConstants.COMMIT_SECONDS);
            scale *= grow;
        }
        return scale;
    }

    /** Node centre Y including the vertical pan offset and the idle bob (AVAILABLE cards only). */
    private float drawCenterY(int index) {
        float y = centerY[index] + panY;
        RouteNode node = nodeRef[index];
        if (node.status == NodeStatus.AVAILABLE && phase == Phase.IDLE) {
            y += RouteMapConstants.NODE_BOB_AMPLITUDE
               * MathUtils.sin(overlayTimeSeconds * RouteMapConstants.NODE_BOB_SPEED + index);
        }
        return y;
    }

    /**
     * Whether a node is drawn this frame: revealed by the boot sweep AND its panned centre sits within
     * the map viewport band (plus a small margin) so panned-away rows don't bleed over the chrome.
     */
    private boolean isNodeDrawable(int index) {
        if (!isRevealedBySweep(index)) {
            return false;
        }
        float y = drawCenterY(index);
        return y >= RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y - RouteMapConstants.MAP_CULL_MARGIN_BOTTOM
            && y <= RouteMapConstants.MAP_VIEWPORT_TOP_Y + RouteMapConstants.MAP_CULL_MARGIN_TOP;
    }

    private float baseAlpha(int index) {
        // Guard against div-by-zero when normalising element alphas back to 0..1.
        return Math.max(0.0001f, baseAlpha[index]);
    }

    /** The node's on-screen alpha: base perspective fade × flicker × commit dimming of non-chosen. */
    private float effectiveAlpha(int index) {
        float alpha = baseAlpha[index] * flicker();
        if (phase == Phase.COMMIT) {
            RouteNode node = nodeRef[index];
            if (node != committedNode) {
                float dim = 1f - Math.min(1f, phaseTimeSeconds / RouteMapConstants.COMMIT_SECONDS);
                alpha *= dim;
            }
        }
        return alpha;
    }

    /** Whole-hologram alpha jitter (~2%) plus the brief sync brighten. Bezel is unaffected. */
    private float flicker() {
        float noise = MathUtils.sin(overlayTimeSeconds * RouteMapConstants.FLICKER_SPEED)
                    * MathUtils.sin(overlayTimeSeconds * RouteMapConstants.FLICKER_SPEED * 0.37f);
        return 1f - RouteMapConstants.FLICKER_AMPLITUDE * (0.5f + 0.5f * noise);
    }

    private Color accentFor(RouteNode node) {
        NodeTypeDefinition definition = nodeTypes.get(node.type);
        return palette.accentFor(definition.accentColorId());
    }

    private Color cardBaseColor(RouteNode node) {
        switch (node.status) {
            case LOCKED:
            case BYPASSED:
                return palette.lockedAccent();
            default:
                return accentFor(node);
        }
    }

    /** Red-alert pulses the trim between amber and red; otherwise the current region's tint. */
    private Color trimColor() {
        if (redAlert) {
            float pulse = 0.5f + 0.5f * MathUtils.sin(overlayTimeSeconds * RouteMapConstants.RED_ALERT_PULSE_SPEED);
            scratchTrim.set(
                GameMath.lerp(palette.hazardAmber.r, palette.dangerRed.r, pulse),
                GameMath.lerp(palette.hazardAmber.g, palette.dangerRed.g, pulse),
                GameMath.lerp(palette.hazardAmber.b, palette.dangerRed.b, pulse),
                1f);
            return scratchTrim;
        }
        return regionTint;
    }

    private final Color scratchTrim = new Color();
    private final Color iconTint  = new Color();   // order-5: node accent + folded master alpha
    private final Color crestTint = new Color();   // order-5: region tint + folded master alpha

    private int displayIndexOf(RouteNode node) {
        for (int index = 0; index < displayCount; index++) {
            if (nodeRef[index] == node) return index;
        }
        return -1;
    }

    /** Filled rounded rectangle: centre rect + two side rects + four corner quarter-discs. */
    private void drawRoundedRect(float left, float bottom, float width, float height, float radius) {
        float clampedRadius = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rect(left + clampedRadius, bottom, width - 2f * clampedRadius, height);
        shapes.rect(left, bottom + clampedRadius, clampedRadius, height - 2f * clampedRadius);
        shapes.rect(left + width - clampedRadius, bottom + clampedRadius, clampedRadius, height - 2f * clampedRadius);
        shapes.arc(left + clampedRadius, bottom + clampedRadius, clampedRadius, 180f, 90f);
        shapes.arc(left + width - clampedRadius, bottom + clampedRadius, clampedRadius, 270f, 90f);
        shapes.arc(left + width - clampedRadius, bottom + height - clampedRadius, clampedRadius, 0f, 90f);
        shapes.arc(left + clampedRadius, bottom + height - clampedRadius, clampedRadius, 90f, 90f);
    }

    /** Rounded-rectangle outline (line mode): four edges + four corner arcs. */
    private void drawRoundedRectOutline(float left, float bottom, float width, float height, float radius) {
        float clampedRadius = Math.min(radius, Math.min(width, height) / 2f);
        shapes.line(left + clampedRadius, bottom, left + width - clampedRadius, bottom);
        shapes.line(left + clampedRadius, bottom + height, left + width - clampedRadius, bottom + height);
        shapes.line(left, bottom + clampedRadius, left, bottom + height - clampedRadius);
        shapes.line(left + width, bottom + clampedRadius, left + width, bottom + height - clampedRadius);
        shapes.arc(left + clampedRadius, bottom + clampedRadius, clampedRadius, 180f, 90f);
        shapes.arc(left + width - clampedRadius, bottom + clampedRadius, clampedRadius, 270f, 90f);
        shapes.arc(left + width - clampedRadius, bottom + height - clampedRadius, clampedRadius, 0f, 90f);
        shapes.arc(left + clampedRadius, bottom + height - clampedRadius, clampedRadius, 90f, 90f);
    }

    private void setShape(Color color, float alpha) {
        shapes.setColor(color.r, color.g, color.b, MathUtils.clamp(alpha, 0f, 1f));
    }

    private void setFont(Color color, float alpha) {
        font.setColor(color.r, color.g, color.b, MathUtils.clamp(alpha, 0f, 1f));
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
