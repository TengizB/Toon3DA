package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.PlayerInventory;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.WeaponConstants;

import java.util.function.IntConsumer;

/**
 * Renders the left panel interior: 3 ranged gun slots + 1 melee slot.
 *
 * Slot layout (bottom Y of each slot rect):
 *   Gun slot 1 (loadout slot 0)  — y = INV_WEAPON_GUN_SLOT_1_Y = 507
 *   Gun slot 2 (loadout slot 1)  — y = INV_WEAPON_GUN_SLOT_2_Y = 401
 *   Gun slot 3 (LOCKED)          — y = INV_WEAPON_GUN_SLOT_3_Y = 295
 *   Section divider               — y = INV_WEAPON_SECTION_DIVIDER_Y = 280
 *   Melee slot                    — y = INV_WEAPON_MELEE_SLOT_Y  = 145
 *
 * Data sources set via setPlayerInventory() / setWeaponHudRenderer() after construction.
 *
 * Package-private — owned and driven by InventoryOverlayRenderer.
 */
final class WeaponSlotsPanel implements Disposable {

    private enum SlotState { EMPTY, FILLED, ACTIVE, LOCKED }

    // -------------------------------------------------------------------------
    // Slot index constants
    // -------------------------------------------------------------------------
    private static final int GUN_SLOT_3_INDEX   = WeaponConstants.WEAPON_GUN_SLOT_LOCKED_INDEX;
    private static final int MELEE_SLOT_INDEX   = 3;
    private static final int TOTAL_SLOT_COUNT   = 4;

    // Bottom-Y of each slot rect, indexed by the slot indices above
    private static final float[] SLOT_Y = {
        ItemConstants.INV_WEAPON_GUN_SLOT_1_Y,
        ItemConstants.INV_WEAPON_GUN_SLOT_2_Y,
        ItemConstants.INV_WEAPON_GUN_SLOT_3_Y,
        ItemConstants.INV_WEAPON_MELEE_SLOT_Y
    };

    // -------------------------------------------------------------------------
    // Color palette — amber-on-steel, consistent with Part 1
    // -------------------------------------------------------------------------
    private static final Color SLOT_BG_NORMAL     = new Color(0.10f, 0.10f, 0.12f, 1f);
    private static final Color SLOT_BG_FILLED     = new Color(0.11f, 0.10f, 0.09f, 1f);
    private static final Color SLOT_BG_ACTIVE     = new Color(0.14f, 0.12f, 0.05f, 1f);
    private static final Color SLOT_BG_LOCKED     = new Color(0.08f, 0.08f, 0.09f, 1f);
    private static final Color BORDER_NORMAL      = new Color(0.22f, 0.22f, 0.30f, 1f);
    private static final Color BORDER_FILLED      = new Color(0.50f, 0.36f, 0.00f, 1f);
    private static final Color BORDER_ACTIVE      = new Color(1.00f, 0.72f, 0.00f, 1f);
    private static final Color BORDER_LOCKED      = new Color(0.20f, 0.20f, 0.22f, 1f);
    private static final Color BADGE_FILL         = new Color(1.00f, 0.72f, 0.00f, 0.90f);
    private static final Color BADGE_TEXT         = new Color(0.00f, 0.00f, 0.00f, 1.00f);
    private static final Color TEXT_PRIMARY       = new Color(1.00f, 0.75f, 0.10f, 1f);
    private static final Color TEXT_SECONDARY     = new Color(0.50f, 0.50f, 0.55f, 1f);
    private static final Color TEXT_DISABLED      = new Color(0.20f, 0.20f, 0.22f, 1f);
    private static final Color LOCK_COLOR         = new Color(0.20f, 0.20f, 0.22f, 1f);
    private static final Color DIVIDER_COLOR      = new Color(0.22f, 0.22f, 0.30f, 1f);
    private static final Color ACCENT_LINE_COLOR  = new Color(1.00f, 0.72f, 0.00f, 0.30f);

    // -------------------------------------------------------------------------
    // Shared rendering resources (owned by InventoryOverlayRenderer coordinator)
    // -------------------------------------------------------------------------
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;

    // -------------------------------------------------------------------------
    // Optional data sources — set after construction, null-safe at render time
    // -------------------------------------------------------------------------
    private PlayerInventory   playerInventory;
    private WeaponHudRenderer weaponHudRenderer;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private final IntConsumer onSlotTapped;

    WeaponSlotsPanel(Inventory itemInventory, ShapeRenderer shapeRenderer, SpriteBatch spriteBatch,
                     BitmapFont font, GlyphLayout glyphLayout, IntConsumer onSlotTapped) {
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.glyphLayout   = glyphLayout;
        this.onSlotTapped  = onSlotTapped;
        // itemInventory unused: weapon data comes from playerInventory / Loadout set post-construction.
    }

    // -------------------------------------------------------------------------
    // Setters — called by InventoryOverlayRenderer after dependent objects are ready
    // -------------------------------------------------------------------------

    void setPlayerInventory(PlayerInventory playerInventory) {
        this.playerInventory = playerInventory;
    }

    void setWeaponHudRenderer(WeaponHudRenderer weaponHudRenderer) {
        this.weaponHudRenderer = weaponHudRenderer;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    void clearSelection() {
        // No persistent selection state; method kept for API compatibility.
    }

    // -------------------------------------------------------------------------
    // Render — three passes: filled shapes, line shapes, sprites + text
    // -------------------------------------------------------------------------

    void render(OrthographicCamera camera, float animationClock) {
        renderPassFilled(camera, animationClock);
        renderPassLine(camera, animationClock);
        renderPassSprites(camera, animationClock);
    }

    // -------------------------------------------------------------------------
    // Touch
    // -------------------------------------------------------------------------

    boolean handleTouch(float worldX, float worldY) {
        for (int slotIndex = 0; slotIndex < TOTAL_SLOT_COUNT; slotIndex++) {
            float slotY = SLOT_Y[slotIndex];
            if (worldX >= ItemConstants.INV_WEAPON_SLOT_X
                    && worldX <= ItemConstants.INV_WEAPON_SLOT_X + ItemConstants.INV_WEAPON_SLOT_WIDTH
                    && worldY >= slotY
                    && worldY <= slotY + ItemConstants.INV_WEAPON_SLOT_HEIGHT) {
                SlotState state = getSlotState(slotIndex);
                if (state != SlotState.LOCKED && state != SlotState.EMPTY) {
                    onSlotTapped.accept(slotIndex);
                }
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Pass A — filled shapes
    // -------------------------------------------------------------------------

    private void renderPassFilled(OrthographicCamera camera, float animationClock) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int slotIndex = 0; slotIndex < TOTAL_SLOT_COUNT; slotIndex++) {
            SlotState state = getSlotState(slotIndex);
            float slotY = SLOT_Y[slotIndex];

            shapeRenderer.setColor(slotBgColor(state));
            shapeRenderer.rect(ItemConstants.INV_WEAPON_SLOT_X, slotY,
                               ItemConstants.INV_WEAPON_SLOT_WIDTH, ItemConstants.INV_WEAPON_SLOT_HEIGHT);

            if (state == SlotState.ACTIVE) {
                float badgeX = ItemConstants.INV_WEAPON_SLOT_X + ItemConstants.INV_WEAPON_SLOT_WIDTH
                               - ItemConstants.INV_WEAPON_ACTIVE_BADGE_WIDTH
                               - ItemConstants.INV_WEAPON_ACTIVE_BADGE_MARGIN;
                float badgeY = slotY + ItemConstants.INV_WEAPON_SLOT_HEIGHT
                               - ItemConstants.INV_WEAPON_ACTIVE_BADGE_HEIGHT
                               - ItemConstants.INV_WEAPON_ACTIVE_BADGE_MARGIN;
                shapeRenderer.setColor(BADGE_FILL);
                shapeRenderer.rect(badgeX, badgeY,
                                   ItemConstants.INV_WEAPON_ACTIVE_BADGE_WIDTH,
                                   ItemConstants.INV_WEAPON_ACTIVE_BADGE_HEIGHT);
            }

            if (state == SlotState.LOCKED) {
                float lockCenterX = ItemConstants.INV_WEAPON_SLOT_X + ItemConstants.INV_WEAPON_LOCK_CENTER_X_OFFSET;
                float lockCenterY = slotY + ItemConstants.INV_WEAPON_LOCK_CENTER_Y_OFFSET;
                shapeRenderer.setColor(LOCK_COLOR);
                shapeRenderer.rect(lockCenterX - ItemConstants.INV_WEAPON_LOCK_BODY_WIDTH  / 2f,
                                   lockCenterY - ItemConstants.INV_WEAPON_LOCK_BODY_HEIGHT / 2f,
                                   ItemConstants.INV_WEAPON_LOCK_BODY_WIDTH,
                                   ItemConstants.INV_WEAPON_LOCK_BODY_HEIGHT);
            }
        }

        shapeRenderer.end();
    }

    // -------------------------------------------------------------------------
    // Pass B — line shapes (borders, divider, accent lines, shackle arc)
    // -------------------------------------------------------------------------

    private void renderPassLine(OrthographicCamera camera, float animationClock) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        for (int slotIndex = 0; slotIndex < TOTAL_SLOT_COUNT; slotIndex++) {
            SlotState state = getSlotState(slotIndex);
            float slotX = ItemConstants.INV_WEAPON_SLOT_X;
            float slotY = SLOT_Y[slotIndex];
            float slotW = ItemConstants.INV_WEAPON_SLOT_WIDTH;
            float slotH = ItemConstants.INV_WEAPON_SLOT_HEIGHT;

            if (state == SlotState.ACTIVE) {
                float pulseAlpha = ItemConstants.INV_WEAPON_ACTIVE_PULSE_MIN
                        + (1f - ItemConstants.INV_WEAPON_ACTIVE_PULSE_MIN) * 0.5f
                        * (1f + MathUtils.sin(animationClock * MathUtils.PI2
                                              * ItemConstants.INV_WEAPON_ACTIVE_PULSE_HZ));
                shapeRenderer.setColor(BORDER_ACTIVE.r, BORDER_ACTIVE.g, BORDER_ACTIVE.b, pulseAlpha);
                shapeRenderer.rect(slotX, slotY, slotW, slotH);
            } else if (state == SlotState.LOCKED) {
                shapeRenderer.setColor(BORDER_LOCKED);
                drawDashedRect(slotX, slotY, slotW, slotH,
                               ItemConstants.INV_WEAPON_LOCKED_DASH_LENGTH,
                               ItemConstants.INV_WEAPON_LOCKED_GAP_LENGTH);

                // Shackle arc — top semi-circle above lock body
                float lockCenterX  = slotX + ItemConstants.INV_WEAPON_LOCK_CENTER_X_OFFSET;
                float shackleBaseY = slotY + ItemConstants.INV_WEAPON_LOCK_CENTER_Y_OFFSET
                                     + ItemConstants.INV_WEAPON_LOCK_BODY_HEIGHT / 2f;
                shapeRenderer.setColor(LOCK_COLOR);
                drawSemiCircleArc(lockCenterX, shackleBaseY, ItemConstants.INV_WEAPON_LOCK_SHACKLE_RADIUS);
            } else {
                shapeRenderer.setColor(state == SlotState.FILLED ? BORDER_FILLED : BORDER_NORMAL);
                shapeRenderer.rect(slotX, slotY, slotW, slotH);
            }
        }

        // Section divider — dashed horizontal line between gun slots and melee slot
        shapeRenderer.setColor(DIVIDER_COLOR);
        drawDashedHorizontalLine(ItemConstants.INV_WEAPON_SLOT_X,
                                 ItemConstants.INV_WEAPON_SECTION_DIVIDER_Y,
                                 ItemConstants.INV_WEAPON_SLOT_WIDTH,
                                 ItemConstants.INV_WEAPON_DIVIDER_DASH_LENGTH,
                                 ItemConstants.INV_WEAPON_DIVIDER_GAP_LENGTH);

        // Section label accent lines — thin amber horizontal lines to the right of each label
        shapeRenderer.setColor(ACCENT_LINE_COLOR);
        float rangedAccentX = ItemConstants.INV_WEAPON_SLOT_X + ItemConstants.INV_WEAPON_RANGED_ACCENT_X_OFFSET;
        float rangedAccentY = ItemConstants.INV_WEAPON_RANGED_LABEL_Y + ItemConstants.INV_WEAPON_ACCENT_LINE_Y_OFFSET;
        shapeRenderer.line(rangedAccentX, rangedAccentY,
                           ItemConstants.INV_WEAPON_SLOT_X + ItemConstants.INV_WEAPON_SLOT_WIDTH,
                           rangedAccentY);

        float meleeAccentX = ItemConstants.INV_WEAPON_SLOT_X + ItemConstants.INV_WEAPON_MELEE_ACCENT_X_OFFSET;
        float meleeAccentY = ItemConstants.INV_WEAPON_MELEE_LABEL_Y + ItemConstants.INV_WEAPON_ACCENT_LINE_Y_OFFSET;
        shapeRenderer.line(meleeAccentX, meleeAccentY,
                           ItemConstants.INV_WEAPON_SLOT_X + ItemConstants.INV_WEAPON_SLOT_WIDTH,
                           meleeAccentY);

        shapeRenderer.end();
    }

    // -------------------------------------------------------------------------
    // Pass C — sprites and text
    // -------------------------------------------------------------------------

    private void renderPassSprites(OrthographicCamera camera, float animationClock) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        // Section labels
        font.setColor(TEXT_SECONDARY);
        font.draw(spriteBatch, "RANGED",
                  ItemConstants.INV_WEAPON_SLOT_X,
                  ItemConstants.INV_WEAPON_RANGED_LABEL_Y + ItemConstants.INV_WEAPON_SECTION_LABEL_H);
        font.draw(spriteBatch, "MELEE",
                  ItemConstants.INV_WEAPON_SLOT_X,
                  ItemConstants.INV_WEAPON_MELEE_LABEL_Y + ItemConstants.INV_WEAPON_SECTION_LABEL_H);

        for (int slotIndex = 0; slotIndex < TOTAL_SLOT_COUNT; slotIndex++) {
            SlotState state  = getSlotState(slotIndex);
            float     slotY  = SLOT_Y[slotIndex];
            float     slotX  = ItemConstants.INV_WEAPON_SLOT_X;
            float     slotW  = ItemConstants.INV_WEAPON_SLOT_WIDTH;
            float     slotH  = ItemConstants.INV_WEAPON_SLOT_HEIGHT;

            switch (state) {
                case EMPTY:
                    glyphLayout.setText(font, "— EMPTY —");
                    font.setColor(TEXT_DISABLED);
                    font.draw(spriteBatch, "— EMPTY —",
                              slotX + (slotW - glyphLayout.width)  / 2f,
                              slotY + (slotH + glyphLayout.height) / 2f);
                    break;

                case LOCKED:
                    drawLockedSlotText(slotX, slotY, slotW);
                    break;

                case FILLED:
                case ACTIVE:
                    Weapon weapon = getWeaponForSlot(slotIndex);
                    if (weapon != null) {
                        drawWeaponThumbnail(weapon, slotY);
                        drawInfoZone(weapon, slotIndex, slotY, state);
                    }
                    if (state == SlotState.ACTIVE) {
                        drawActiveBadgeText(slotX, slotY);
                    }
                    break;
            }
        }

        spriteBatch.end();
    }

    // -------------------------------------------------------------------------
    // Private — slot data helpers
    // -------------------------------------------------------------------------

    private SlotState getSlotState(int slotIndex) {
        if (slotIndex == GUN_SLOT_3_INDEX) return SlotState.LOCKED;
        Weapon weapon = getWeaponForSlot(slotIndex);
        if (weapon == null) return SlotState.EMPTY;
        return isSlotActive(slotIndex) ? SlotState.ACTIVE : SlotState.FILLED;
    }

    private Weapon getWeaponForSlot(int slotIndex) {
        if (playerInventory == null) return null;
        if (slotIndex == MELEE_SLOT_INDEX) return playerInventory.getMeleeWeapon();
        return playerInventory.getLoadout().getSlot(slotIndex);
    }

    private boolean isSlotActive(int slotIndex) {
        if (playerInventory == null) return false;
        if (slotIndex == MELEE_SLOT_INDEX) return playerInventory.isMeleeSelected();
        if (playerInventory.isMeleeSelected()) return false;
        return playerInventory.getLoadout().getActiveSlotIndex() == slotIndex;
    }

    private static Color slotBgColor(SlotState state) {
        switch (state) {
            case FILLED: return SLOT_BG_FILLED;
            case ACTIVE: return SLOT_BG_ACTIVE;
            case LOCKED: return SLOT_BG_LOCKED;
            default:     return SLOT_BG_NORMAL;
        }
    }

    // -------------------------------------------------------------------------
    // Private — sprite and text drawing helpers
    // -------------------------------------------------------------------------

    private void drawWeaponThumbnail(Weapon weapon, float slotY) {
        if (weaponHudRenderer == null) return;
        Texture texture = weaponHudRenderer.getWeaponTexture(weapon);
        if (texture == null) return;

        // Sprite-sheet weapons (e.g. the Chaingun rotation frames) pack several frames side by side;
        // the thumbnail shows only the first frame, so measure and sample one frame's width.
        int   frameColumns = weaponHudRenderer.getWeaponTextureFrameColumns(weapon);
        int   frameWidth   = texture.getWidth() / frameColumns;
        float srcWidth   = frameWidth;
        float srcHeight  = texture.getHeight();
        if (srcWidth <= 0f || srcHeight <= 0f) return;
        float dstWidth   = ItemConstants.INV_WEAPON_SPRITE_ZONE_WIDTH;
        float dstHeight  = ItemConstants.INV_WEAPON_SLOT_HEIGHT;
        float scale      = Math.min(dstWidth / srcWidth, dstHeight / srcHeight);
        float drawWidth  = srcWidth  * scale;
        float drawHeight = srcHeight * scale;
        float drawX      = ItemConstants.INV_WEAPON_SLOT_X + (dstWidth  - drawWidth)  / 2f;
        float drawY      = slotY                           + (dstHeight - drawHeight) / 2f;

        spriteBatch.draw(texture, drawX, drawY, drawWidth, drawHeight,
                         0, 0, frameWidth, texture.getHeight(), false, false);
    }

    private void drawInfoZone(Weapon weapon, int slotIndex, float slotY, SlotState state) {
        float textX    = ItemConstants.INV_WEAPON_SLOT_X + ItemConstants.INV_WEAPON_INFO_ZONE_X_OFFSET;
        float textY    = slotY + ItemConstants.INV_WEAPON_SLOT_HEIGHT - ItemConstants.INV_WEAPON_INFO_TOP_MARGIN;
        float lineStep = ItemConstants.INV_WEAPON_INFO_LINE_STEP;
        boolean isMelee = (slotIndex == MELEE_SLOT_INDEX);

        // Weapon name — amber when active, secondary otherwise
        font.setColor(state == SlotState.ACTIVE ? TEXT_PRIMARY : TEXT_SECONDARY);
        font.draw(spriteBatch, weapon.getDisplayName(), textX, textY);
        textY -= lineStep;

        // Ammo type
        font.setColor(TEXT_SECONDARY);
        String ammoLabel = isMelee ? "Melee"
                : (weapon.getAmmoType() != null ? weapon.getAmmoType().getDisplayName() : "—");
        font.draw(spriteBatch, ammoLabel, textX, textY);
        textY -= lineStep;

        // Clip / max clip
        String clipLabel = isMelee ? "—"
                : weapon.getShotsInClip() + " / " + weapon.getEffectiveClipSize();
        font.draw(spriteBatch, clipLabel, textX, textY);
        textY -= lineStep;

        // Ammo reserve
        String reserveLabel;
        if (isMelee) {
            reserveLabel = "Unlimited";
        } else {
            int reserve = weapon.getReserveAmmo();
            reserveLabel = (reserve < 0) ? "—" : "Ammo: " + reserve;
        }
        font.draw(spriteBatch, reserveLabel, textX, textY);
    }

    private void drawActiveBadgeText(float slotX, float slotY) {
        float badgeX = slotX + ItemConstants.INV_WEAPON_SLOT_WIDTH
                       - ItemConstants.INV_WEAPON_ACTIVE_BADGE_WIDTH
                       - ItemConstants.INV_WEAPON_ACTIVE_BADGE_MARGIN;
        float badgeY = slotY + ItemConstants.INV_WEAPON_SLOT_HEIGHT
                       - ItemConstants.INV_WEAPON_ACTIVE_BADGE_HEIGHT
                       - ItemConstants.INV_WEAPON_ACTIVE_BADGE_MARGIN;
        glyphLayout.setText(font, "ACTIVE");
        font.setColor(BADGE_TEXT);
        font.draw(spriteBatch, "ACTIVE",
                  badgeX + (ItemConstants.INV_WEAPON_ACTIVE_BADGE_WIDTH  - glyphLayout.width)  / 2f,
                  badgeY + (ItemConstants.INV_WEAPON_ACTIVE_BADGE_HEIGHT + glyphLayout.height) / 2f);
    }

    private void drawLockedSlotText(float slotX, float slotY, float slotW) {
        float lockCenterX = slotX + ItemConstants.INV_WEAPON_LOCK_CENTER_X_OFFSET;

        // "LOCKED" centered below lock body
        glyphLayout.setText(font, "LOCKED");
        font.setColor(TEXT_DISABLED);
        font.draw(spriteBatch, "LOCKED",
                  lockCenterX - glyphLayout.width / 2f,
                  slotY + ItemConstants.INV_WEAPON_LOCK_LABEL_Y_OFFSET);

        // Unlock hint text
        String hintText = "Unlock at Level " + ItemConstants.INV_WEAPON_LOCKED_UNLOCK_LEVEL;
        glyphLayout.setText(font, hintText);
        font.draw(spriteBatch, hintText,
                  lockCenterX - glyphLayout.width / 2f,
                  slotY + ItemConstants.INV_WEAPON_LOCK_HINT_Y_OFFSET);
    }

    // -------------------------------------------------------------------------
    // Private — dashed line helpers (all called within ShapeRenderer.Line begin/end)
    // -------------------------------------------------------------------------

    private void drawDashedRect(float x, float y, float width, float height,
                                float dashLength, float gapLength) {
        drawDashedHorizontalLine(x,         y,          width,  dashLength, gapLength);
        drawDashedVerticalLine  (x + width, y,          height, dashLength, gapLength);
        drawDashedHorizontalLine(x + width, y + height, -width, dashLength, gapLength);
        drawDashedVerticalLine  (x,         y + height, -height, dashLength, gapLength);
    }

    private void drawDashedHorizontalLine(float startX, float y, float length,
                                          float dashLength, float gapLength) {
        float sign      = length >= 0f ? 1f : -1f;
        float remaining = Math.abs(length);
        float pos       = startX;
        boolean drawing = true;
        while (remaining > 0f) {
            float segLen = Math.min(drawing ? dashLength : gapLength, remaining);
            float endPos = pos + sign * segLen;
            if (drawing) shapeRenderer.line(pos, y, endPos, y);
            pos       = endPos;
            remaining -= segLen;
            drawing   = !drawing;
        }
    }

    private void drawDashedVerticalLine(float x, float startY, float length,
                                        float dashLength, float gapLength) {
        float sign      = length >= 0f ? 1f : -1f;
        float remaining = Math.abs(length);
        float pos       = startY;
        boolean drawing = true;
        while (remaining > 0f) {
            float segLen = Math.min(drawing ? dashLength : gapLength, remaining);
            float endPos = pos + sign * segLen;
            if (drawing) shapeRenderer.line(x, pos, x, endPos);
            pos       = endPos;
            remaining -= segLen;
            drawing   = !drawing;
        }
    }

    /** Draws a top semi-circle (0 to π radians) as line segments. */
    private void drawSemiCircleArc(float centerX, float centerY, float radius) {
        int   segments = 14;
        float angleStep = MathUtils.PI / segments;
        float previousX = centerX + radius;
        float previousY = centerY;
        for (int seg = 1; seg <= segments; seg++) {
            float angle    = seg * angleStep;
            float currentX = centerX + radius * MathUtils.cos(angle);
            float currentY = centerY + radius * MathUtils.sin(angle);
            shapeRenderer.line(previousX, previousY, currentX, currentY);
            previousX = currentX;
            previousY = currentY;
        }
    }

    // -------------------------------------------------------------------------
    // Dispose
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        // Shared resources owned by InventoryOverlayRenderer coordinator — not disposed here.
    }
}
