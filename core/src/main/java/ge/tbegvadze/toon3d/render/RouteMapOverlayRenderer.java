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

import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.route.DangerTier;
import ge.tbegvadze.toon3d.route.NodeStatus;
import ge.tbegvadze.toon3d.route.NodeTypeDefinition;
import ge.tbegvadze.toon3d.route.NodeTypeRegistry;
import ge.tbegvadze.toon3d.route.RouteMap;
import ge.tbegvadze.toon3d.route.RouteNode;
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

    private static final String TITLE_TEXT   = "FACILITY NAV  —  SELECT VECTOR";
    private static final String CANCEL_TEXT  = "◀ CANCEL";
    private static final String ENGAGE_TEXT  = "ENGAGE ▶";
    private static final String UAC_STENCIL  = "UAC//NAV";
    private static final String LOCK_GLYPH   = "◇";
    private static final String UNKNOWN_REGION = "UNKNOWN SECTOR";

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   layout;                 // used only outside render()
    private final RouteMapColorPalette palette;
    private final NodeTypeRegistry     nodeTypes;

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

    // ---- Precomputed header / legend text ----------------------------------
    private float  titleBaselineX;
    private String depthText   = "";
    private float  depthTextX;
    private String regionText  = "";
    private float  regionTextX;
    private Color  regionTint;
    private String legendText  = "";
    private float  legendTextX;
    private float  cancelLabelX, cancelLabelY, engageLabelX, engageLabelY;

    // ---- Per-conduit scratch (no allocation in render) ---------------------
    private float conduitStartX, conduitStartY, conduitControlX, conduitControlY, conduitEndX, conduitEndY;

    public RouteMapOverlayRenderer() {
        shapes  = new ShapeRenderer();
        batch   = new SpriteBatch();
        font    = new BitmapFont();
        layout  = new GlyphLayout();
        palette = new RouteMapColorPalette();
        nodeTypes = RouteRegistries.nodeTypes();
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
        displayCount = 0;
        focusNode = null;
        committedNode = null;
        phase = Phase.OPENING;
        overlayTimeSeconds = 0f;
        phaseTimeSeconds = 0f;
        if (map == null || map.getCurrent() == null) {
            return;
        }

        List<RouteNode> candidates = map.getSelectableNext();
        focusNode = candidates.isEmpty() ? null : candidates.get(0);

        buildLayout(map);
        buildHeaderText(map);
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

        for (int layerIndex = firstLayer; layerIndex <= lastLayer; layerIndex++) {
            int rowFromBottom = layerIndex - firstLayer;
            int relativeRow   = layerIndex - currentLayerIndex;   // -1 .. LOOKAHEAD
            float rowY = RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y + RouteMapConstants.MAP_BOTTOM_PAD
                       + rowFromBottom * RouteMapConstants.MAP_LAYER_GAP;
            float scale = scaleForRow(relativeRow);
            float alpha = alphaForRow(relativeRow);

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
        regionTint = region != null ? palette.regionTint(region.themeId()) : palette.holoCyan;
        regionTextX = 40f;

        String hint;
        if (focusNode == null) {
            hint = "";
        } else if (!focusNode.revealed) {
            hint = "Unknown vector. Contents obscured.";
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

    /** Advances the OPENING -> IDLE -> COMMIT -> DONE timeline. */
    public void update(float deltaTime) {
        if (phase == Phase.INACTIVE || phase == Phase.DONE) {
            return;
        }
        overlayTimeSeconds += deltaTime;
        phaseTimeSeconds   += deltaTime;
        if (phase == Phase.OPENING && phaseTimeSeconds >= RouteMapConstants.OPENING_SECONDS) {
            phase = Phase.IDLE;
            phaseTimeSeconds = 0f;
        } else if (phase == Phase.COMMIT && phaseTimeSeconds >= RouteMapConstants.COMMIT_SECONDS) {
            phase = Phase.DONE;
        }
    }

    /** True once the map has been shown long enough that the auto-driver should pick (order-6: a tap). */
    public boolean isReadyForSelection() {
        return phase == Phase.IDLE
            && focusNode != null
            && phaseTimeSeconds >= RouteMapConstants.AUTO_SELECT_HOLD_SECONDS;
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
        drawBezel();                 // 2. bezel / chrome / hazard trim
        drawGlowPass();              // 3. additive hologram bloom (node halos + conduit cores)
        drawConduitPass();           // 4. curved conduits + travelling pulses
        drawNodePass();              // 5. card bodies + borders + state decor + risk pips + icons
        drawTextPass();              // 7. title / depth / region / labels / legend / confirm labels
        drawCrtPass();               // 8. scanlines + vignette + flicker + sync flash

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // ---- Pass 1: scrim ------------------------------------------------------

    private void drawScrim() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(palette.scrimNavy.r, palette.scrimNavy.g, palette.scrimNavy.b,
                        RouteMapConstants.OVERLAY_SCRIM_ALPHA);
        shapes.rect(0f, 0f, worldWidth, worldHeight);
        shapes.end();
    }

    // ---- Pass 2: bezel / chrome --------------------------------------------

    private void drawBezel() {
        Color trim = trimColor();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // Title plate + confirm-bar + legend bands (brushed steel).
        setShape(palette.steel, 0.92f);
        shapes.rect(0f, RouteMapConstants.TITLE_PLATE_BOTTOM_Y, worldWidth,
                    RouteMapConstants.TITLE_PLATE_TOP_Y - RouteMapConstants.TITLE_PLATE_BOTTOM_Y);
        shapes.rect(0f, RouteMapConstants.LEGEND_STRIP_BOTTOM_Y, worldWidth,
                    RouteMapConstants.LEGEND_STRIP_TOP_Y - RouteMapConstants.LEGEND_STRIP_BOTTOM_Y);
        setShape(palette.steel, 0.95f);
        shapes.rect(0f, RouteMapConstants.CONFIRM_BAR_BOTTOM_Y, worldWidth,
                    RouteMapConstants.CONFIRM_BAR_TOP_Y - RouteMapConstants.CONFIRM_BAR_BOTTOM_Y);

        // Confirm-bar buttons (visual targets; touch is order-6).
        float barBottom = RouteMapConstants.CONFIRM_BAR_BOTTOM_Y + 6f;
        float barHeight = RouteMapConstants.CONFIRM_BAR_TOP_Y - RouteMapConstants.CONFIRM_BAR_BOTTOM_Y - 12f;
        setShape(palette.holoDim, 0.45f);
        shapes.rect(RouteMapConstants.CONFIRM_BUTTON_MARGIN, barBottom,
                    RouteMapConstants.CONFIRM_BUTTON_WIDTH, barHeight);
        setShape(palette.holoCyan, 0.30f);
        shapes.rect(worldWidth - RouteMapConstants.CONFIRM_BUTTON_MARGIN - RouteMapConstants.CONFIRM_BUTTON_WIDTH,
                    barBottom, RouteMapConstants.CONFIRM_BUTTON_WIDTH, barHeight);

        // Hazard chevron trim along the top and bottom of the map viewport.
        drawHazardTrim(RouteMapConstants.TITLE_PLATE_BOTTOM_Y - RouteMapConstants.HAZARD_TRIM_HEIGHT, trim);
        drawHazardTrim(RouteMapConstants.LEGEND_STRIP_TOP_Y, trim);

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
            if (!isRevealedBySweep(index)) continue;
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
            if (!isRevealedBySweep(index)) continue;
            for (RouteNode target : source.outgoing) {
                int targetSlot = displayIndexOf(target);
                if (targetSlot < 0 || !isRevealedBySweep(targetSlot)) continue;
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
            if (!isRevealedBySweep(index)) continue;
            boolean hot = source.status == NodeStatus.CURRENT || source.status == NodeStatus.AVAILABLE;
            if (!hot) continue;
            for (RouteNode target : source.outgoing) {
                int targetSlot = displayIndexOf(target);
                if (targetSlot < 0 || !isRevealedBySweep(targetSlot)) continue;
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
        // Bodies + icons + risk pips (filled).
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int index = 0; index < displayCount; index++) {
            if (!isRevealedBySweep(index)) continue;
            drawCardBody(index);
            drawNodeIconPlaceholder(index);
            drawRiskPips(index);
            drawStateDecorFilled(index);
        }
        shapes.end();

        // Borders + reticle + selection ring (line).
        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(RouteMapConstants.NODE_CARD_BORDER_WIDTH);
        for (int index = 0; index < displayCount; index++) {
            if (!isRevealedBySweep(index)) continue;
            drawCardBorder(index);
            drawStateDecorLine(index);
        }
        Gdx.gl.glLineWidth(1f);
        shapes.end();
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

    /** Placeholder node glyph — order-5 replaces this with the real procedural IconPainter. */
    private void drawNodeIconPlaceholder(int index) {
        RouteNode node = nodeRef[index];
        float scale = drawScale(index);
        float iconCenterY = drawCenterY(index) + RouteMapConstants.NODE_CARD_HEIGHT * scale * 0.18f;
        float radius = 20f * scale;
        Color accent = node.status == NodeStatus.LOCKED ? palette.lockedAccent() : accentFor(node);
        float alpha = effectiveAlpha(index) / baseAlpha(index);
        setShape(accent, 0.85f * alpha);
        // simple diamond
        shapes.triangle(centerX[index], iconCenterY + radius, centerX[index] - radius, iconCenterY,
                        centerX[index] + radius, iconCenterY);
        shapes.triangle(centerX[index], iconCenterY - radius, centerX[index] - radius, iconCenterY,
                        centerX[index] + radius, iconCenterY);
    }

    /** 1-4 dot risk meter in the card's bottom-right corner (colour ramp green->amber->red). */
    private void drawRiskPips(int index) {
        RouteNode node = nodeRef[index];
        if (node.status == NodeStatus.LOCKED || !node.revealed) return;
        NodeTypeDefinition definition = nodeTypes.get(node.type);
        DangerTier tier = definition.dangerTier();
        int pips = palette.pipCount(tier);
        Color pipColor = palette.pipColor(tier);
        float scale = drawScale(index);
        float width = RouteMapConstants.NODE_CARD_WIDTH * scale;
        float height = RouteMapConstants.NODE_CARD_HEIGHT * scale;
        float rightX = centerX[index] + width / 2f - RouteMapConstants.RISK_PIP_INSET * scale;
        float pipY = drawCenterY(index) - height / 2f + RouteMapConstants.RISK_PIP_INSET * scale;
        float radius = RouteMapConstants.RISK_PIP_RADIUS * scale;
        float step = (RouteMapConstants.RISK_PIP_RADIUS * 2f + RouteMapConstants.RISK_PIP_GAP) * scale;
        float alpha = effectiveAlpha(index) / baseAlpha(index);
        for (int pip = 0; pip < RouteMapConstants.RISK_PIP_MAX; pip++) {
            float pipX = rightX - pip * step;
            if (pip < pips) {
                setShape(pipColor, 0.95f * alpha);
            } else {
                setShape(palette.holoDim, 0.35f * alpha);
            }
            shapes.circle(pipX, pipY, radius);
        }
    }

    private void drawStateDecorFilled(int index) {
        RouteNode node = nodeRef[index];
        if (node.status == NodeStatus.VISITED) {
            // small check mark tint over the body
            float scale = drawScale(index);
            float checkX = centerX[index];
            float checkY = drawCenterY(index);
            setShape(palette.medGreen, 0.5f * effectiveAlpha(index) / baseAlpha(index));
            shapes.rectLine(checkX - 14f * scale, checkY, checkX - 4f * scale, checkY - 10f * scale, 3f * scale);
            shapes.rectLine(checkX - 4f * scale, checkY - 10f * scale, checkX + 16f * scale, checkY + 14f * scale, 3f * scale);
        }
    }

    private void drawStateDecorLine(int index) {
        RouteNode node = nodeRef[index];
        float alpha = effectiveAlpha(index) / baseAlpha(index);
        if (node.status == NodeStatus.CURRENT) {
            // rotating "YOU ARE HERE" diamond reticle
            float radius = RouteMapConstants.RETICLE_RADIUS * drawScale(index);
            float spin = overlayTimeSeconds * RouteMapConstants.RETICLE_SPIN_SPEED;
            setShape(palette.holoCyan, 0.9f * alpha);
            drawRotatedDiamond(centerX[index], drawCenterY(index), radius, spin);
        } else if (node == focusNode && node.status == NodeStatus.AVAILABLE) {
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

    private void drawRotatedDiamond(float centerXValue, float centerYValue, float radius, float angleRadians) {
        float previousX = 0f, previousY = 0f;
        for (int corner = 0; corner <= 4; corner++) {
            float cornerAngle = angleRadians + corner * MathUtils.HALF_PI;
            float pointX = centerXValue + radius * MathUtils.cos(cornerAngle);
            float pointY = centerYValue + radius * MathUtils.sin(cornerAngle);
            if (corner > 0) {
                shapes.line(previousX, previousY, pointX, pointY);
            }
            previousX = pointX;
            previousY = pointY;
        }
    }

    // ---- Pass 7: text -------------------------------------------------------

    private void drawTextPass() {
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

        // Node labels.
        font.getData().setScale(RouteMapConstants.NODE_LABEL_SCALE);
        for (int index = 0; index < displayCount; index++) {
            if (!isRevealedBySweep(index)) continue;
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

        // Legend hint.
        if (!legendText.isEmpty()) {
            font.getData().setScale(RouteMapConstants.LEGEND_TEXT_SCALE);
            float legendY = (RouteMapConstants.LEGEND_STRIP_BOTTOM_Y + RouteMapConstants.LEGEND_STRIP_TOP_Y) / 2f + 6f;
            setFont(regionTint, 0.95f);
            font.draw(batch, legendText, legendTextX, legendY);
        }

        // Confirm-bar labels.
        font.getData().setScale(RouteMapConstants.CONFIRM_LABEL_SCALE);
        setFont(palette.textDim, 0.9f);
        font.draw(batch, CANCEL_TEXT, cancelLabelX, cancelLabelY);
        setFont(palette.holoCyan, 1f);
        font.draw(batch, ENGAGE_TEXT, engageLabelX, engageLabelY);

        batch.end();
    }

    // ---- Pass 8: CRT --------------------------------------------------------

    private void drawCrtPass() {
        float viewportBottom = RouteMapConstants.MAP_VIEWPORT_BOTTOM_Y;
        float viewportTop    = RouteMapConstants.MAP_VIEWPORT_TOP_Y;

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Scanlines — dark 1px lines scrolling upward across the map viewport only.
        float scroll = (overlayTimeSeconds * RouteMapConstants.SCANLINE_SCROLL_SPEED)
                     % RouteMapConstants.SCANLINE_SPACING;
        shapes.setColor(0f, 0f, 0f, RouteMapConstants.SCANLINE_ALPHA);
        for (float lineY = viewportBottom + scroll; lineY < viewportTop; lineY += RouteMapConstants.SCANLINE_SPACING) {
            shapes.rect(0f, lineY, worldWidth, 1f);
        }

        // Vignette — darkened edge bands around the viewport.
        float band = RouteMapConstants.VIGNETTE_BAND;
        shapes.setColor(0f, 0f, 0f, RouteMapConstants.VIGNETTE_ALPHA);
        shapes.rect(0f, viewportBottom, band, viewportTop - viewportBottom);
        shapes.rect(worldWidth - band, viewportBottom, band, viewportTop - viewportBottom);
        shapes.rect(0f, viewportTop - band, worldWidth, band);
        shapes.rect(0f, viewportBottom, worldWidth, band);

        // Flicker + rare sync flash across the whole hologram.
        float syncPhase = overlayTimeSeconds % RouteMapConstants.SYNC_FLASH_INTERVAL;
        if (syncPhase < 0.06f) {
            shapes.setColor(palette.holoCyan.r, palette.holoCyan.g, palette.holoCyan.b,
                            RouteMapConstants.SYNC_FLASH_ALPHA);
            shapes.rect(0f, viewportBottom, worldWidth, viewportTop - viewportBottom);
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
        if (node == focusNode && node.status == NodeStatus.AVAILABLE && phase != Phase.COMMIT) {
            scale *= RouteMapConstants.FOCUS_SCALE;
        }
        if (phase == Phase.COMMIT && node == committedNode) {
            float grow = 1f + 0.08f * Math.min(1f, phaseTimeSeconds / RouteMapConstants.COMMIT_SECONDS);
            scale *= grow;
        }
        return scale;
    }

    /** Node centre Y including the idle bob (AVAILABLE cards only, while idling). */
    private float drawCenterY(int index) {
        float y = centerY[index];
        RouteNode node = nodeRef[index];
        if (node.status == NodeStatus.AVAILABLE && phase == Phase.IDLE) {
            y += RouteMapConstants.NODE_BOB_AMPLITUDE
               * MathUtils.sin(overlayTimeSeconds * RouteMapConstants.NODE_BOB_SPEED + index);
        }
        return y;
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
