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
import ge.tbegvadze.toon3d.entity.WeaponTier;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.HudConstants;
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
 * Visual identity order 5 (R10, R2): a filled slot is a UiKit CARD inked by its weapon's tier colour (the
 * equipped slot is the SELECTED card, so its brackets take that ink too); an empty slot is an IDLE plate and
 * the locked slot a DISABLED one. Section rules are UiKit dividers.
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

    /** Section labels ("RANGED" / "MELEE"); their rules start just past the label's measured width. */
    private static final String RANGED_LABEL = "RANGED";
    private static final String MELEE_LABEL  = "MELEE";

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
    /** The floor THREAT LEVEL (= depth, >= 1) the LV tags are coloured against (R14 d). */
    private int               threatLevel = 1;
    // Per-slot arrow glyph placement recorded by the text pass and drawn by the following line pass.
    private final int[]   glyphStanding = new int[TOTAL_SLOT_COUNT];
    private final float[] glyphX        = new float[TOTAL_SLOT_COUNT];
    private final float[] glyphY        = new float[TOTAL_SLOT_COUNT];
    private final Color   tagColor      = new Color();
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

    void setThreatLevel(int threatLevel) {
        this.threatLevel = Math.max(1, threatLevel);
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
        renderPassFilled(camera);
        renderPassSprites(camera);
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
    // Pass A — one Filled pass: slot cards / plates, badge, lock icon, section rules
    // -------------------------------------------------------------------------

    private void renderPassFilled(OrthographicCamera camera) {
        // Section rules start past their labels' TYPE_LABEL width, measured before the shape pass begins.
        font.getData().setScale(ArtDirection.TYPE_LABEL);
        glyphLayout.setText(font, RANGED_LABEL);
        float rangedRuleX = ItemConstants.INV_WEAPON_SLOT_X + glyphLayout.width + ItemConstants.INV_WEAPON_SECTION_RULE_GAP;
        glyphLayout.setText(font, MELEE_LABEL);
        float meleeRuleX = ItemConstants.INV_WEAPON_SLOT_X + glyphLayout.width + ItemConstants.INV_WEAPON_SECTION_RULE_GAP;
        font.getData().setScale(ArtDirection.TYPE_CAPTION);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int slotIndex = 0; slotIndex < TOTAL_SLOT_COUNT; slotIndex++) {
            SlotState state = getSlotState(slotIndex);
            float slotX = ItemConstants.INV_WEAPON_SLOT_X;
            float slotY = SLOT_Y[slotIndex];
            float slotW = ItemConstants.INV_WEAPON_SLOT_WIDTH;
            float slotH = ItemConstants.INV_WEAPON_SLOT_HEIGHT;

            if (state == SlotState.FILLED || state == SlotState.ACTIVE) {
                // R2: the card's frame takes the weapon's tier colour; the equipped one is SELECTED.
                WeaponTier tier = getWeaponForSlot(slotIndex).getTier();
                if (tier == null) tier = WeaponTier.COMMON;
                UiKit.card(shapeRenderer, slotX, slotY, slotW, slotH, tier.colorRed, tier.colorGreen, tier.colorBlue,
                           state == SlotState.ACTIVE ? PlateState.SELECTED : PlateState.IDLE);
            } else {
                UiKit.plate(shapeRenderer, slotX, slotY, slotW, slotH,
                            state == SlotState.LOCKED ? PlateState.DISABLED : PlateState.IDLE,
                            ItemConstants.INV_PANEL_ALPHA,
                            ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);
            }

            if (state == SlotState.ACTIVE) {
                float badgeX = slotX + slotW
                               - ItemConstants.INV_WEAPON_ACTIVE_BADGE_WIDTH
                               - ItemConstants.INV_WEAPON_ACTIVE_BADGE_MARGIN;
                float badgeY = slotY + slotH
                               - ItemConstants.INV_WEAPON_ACTIVE_BADGE_HEIGHT
                               - ItemConstants.INV_WEAPON_ACTIVE_BADGE_MARGIN;
                shapeRenderer.setColor(ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN,
                                       ArtDirection.SUIT_AMBER_BLUE, 1f);
                shapeRenderer.rect(badgeX, badgeY,
                                   ItemConstants.INV_WEAPON_ACTIVE_BADGE_WIDTH,
                                   ItemConstants.INV_WEAPON_ACTIVE_BADGE_HEIGHT);
            }

            if (state == SlotState.LOCKED) {
                drawLockIcon(slotX + ItemConstants.INV_WEAPON_LOCK_CENTER_X_OFFSET,
                             slotY + ItemConstants.INV_WEAPON_LOCK_CENTER_Y_OFFSET);
            }
        }

        // Section divider between the gun slots and the melee slot, and the rules beside each section label
        UiKit.divider(shapeRenderer, ItemConstants.INV_WEAPON_SLOT_X, ItemConstants.INV_WEAPON_SECTION_DIVIDER_Y,
                      ItemConstants.INV_WEAPON_SLOT_WIDTH,
                      ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f);
        float slotRightX = ItemConstants.INV_WEAPON_SLOT_X + ItemConstants.INV_WEAPON_SLOT_WIDTH;
        float rangedRuleY = ItemConstants.INV_WEAPON_RANGED_LABEL_Y + ItemConstants.INV_WEAPON_ACCENT_LINE_Y_OFFSET;
        UiKit.divider(shapeRenderer, rangedRuleX, rangedRuleY, slotRightX - rangedRuleX,
                      ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN, ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);
        float meleeRuleY = ItemConstants.INV_WEAPON_MELEE_LABEL_Y + ItemConstants.INV_WEAPON_ACCENT_LINE_Y_OFFSET;
        UiKit.divider(shapeRenderer, meleeRuleX, meleeRuleY, slotRightX - meleeRuleX,
                      ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN, ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);

        shapeRenderer.end();
    }

    /**
     * The locked slot's padlock (an icon): a filled body and a shackle arc of short thick segments, in
     * UI_TEXT_DISABLED. Filled-pass only.
     */
    private void drawLockIcon(float lockCenterX, float lockCenterY) {
        shapeRenderer.setColor(ArtDirection.UI_TEXT_DISABLED_RED, ArtDirection.UI_TEXT_DISABLED_GREEN,
                               ArtDirection.UI_TEXT_DISABLED_BLUE, 1f);
        shapeRenderer.rect(lockCenterX - ItemConstants.INV_WEAPON_LOCK_BODY_WIDTH  / 2f,
                           lockCenterY - ItemConstants.INV_WEAPON_LOCK_BODY_HEIGHT / 2f,
                           ItemConstants.INV_WEAPON_LOCK_BODY_WIDTH,
                           ItemConstants.INV_WEAPON_LOCK_BODY_HEIGHT);
        float shackleBaseY = lockCenterY + ItemConstants.INV_WEAPON_LOCK_BODY_HEIGHT / 2f;
        float radius = ItemConstants.INV_WEAPON_LOCK_SHACKLE_RADIUS;
        float angleStep = MathUtils.PI / ItemConstants.INV_WEAPON_LOCK_SHACKLE_SEGMENTS;
        float previousX = lockCenterX + radius;
        float previousY = shackleBaseY;
        for (int segment = 1; segment <= ItemConstants.INV_WEAPON_LOCK_SHACKLE_SEGMENTS; segment++) {
            float angle    = segment * angleStep;
            float currentX = lockCenterX + radius * MathUtils.cos(angle);
            float currentY = shackleBaseY + radius * MathUtils.sin(angle);
            shapeRenderer.rectLine(previousX, previousY, currentX, currentY,
                                   ItemConstants.INV_WEAPON_LOCK_SHACKLE_THICKNESS);
            previousX = currentX;
            previousY = currentY;
        }
    }

    // -------------------------------------------------------------------------
    // Pass C — sprites and text
    // -------------------------------------------------------------------------

    private void renderPassSprites(OrthographicCamera camera) {
        for (int slotIndex = 0; slotIndex < TOTAL_SLOT_COUNT; slotIndex++) {
            glyphStanding[slotIndex] = WeaponLevelTag.STANDING_NORMAL;
        }
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        // Section labels (R6: TYPE_LABEL, secondary)
        font.getData().setScale(ArtDirection.TYPE_LABEL);
        setSecondary();
        font.draw(spriteBatch, RANGED_LABEL,
                  ItemConstants.INV_WEAPON_SLOT_X,
                  ItemConstants.INV_WEAPON_RANGED_LABEL_Y + ItemConstants.INV_WEAPON_SECTION_LABEL_H);
        font.draw(spriteBatch, MELEE_LABEL,
                  ItemConstants.INV_WEAPON_SLOT_X,
                  ItemConstants.INV_WEAPON_MELEE_LABEL_Y + ItemConstants.INV_WEAPON_SECTION_LABEL_H);
        // Slot text is dense data in a fixed 182-unit info zone: TYPE_CAPTION keeps it inside the card.
        font.getData().setScale(ArtDirection.TYPE_CAPTION);

        for (int slotIndex = 0; slotIndex < TOTAL_SLOT_COUNT; slotIndex++) {
            SlotState state  = getSlotState(slotIndex);
            float     slotY  = SLOT_Y[slotIndex];
            float     slotX  = ItemConstants.INV_WEAPON_SLOT_X;
            float     slotW  = ItemConstants.INV_WEAPON_SLOT_WIDTH;
            float     slotH  = ItemConstants.INV_WEAPON_SLOT_HEIGHT;

            switch (state) {
                case EMPTY:
                    glyphLayout.setText(font, "— EMPTY —");
                    setDisabled();
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
        renderPassGlyphs(camera);
    }

    /** Pass D — the LV tags' arrow glyphs, placed by the text pass (R14 d). */
    private void renderPassGlyphs(OrthographicCamera camera) {
        boolean anyGlyph = false;
        for (int slotIndex = 0; slotIndex < TOTAL_SLOT_COUNT; slotIndex++) {
            if (WeaponLevelTag.hasGlyph(glyphStanding[slotIndex])) { anyGlyph = true; break; }
        }
        if (!anyGlyph) return;
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (int slotIndex = 0; slotIndex < TOTAL_SLOT_COUNT; slotIndex++) {
            int standing = glyphStanding[slotIndex];
            if (!WeaponLevelTag.hasGlyph(standing)) continue;
            tagColor.set(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN,
                         ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
            WeaponLevelTag.colorFor(standing, tagColor, tagColor);
            shapeRenderer.setColor(tagColor);
            WeaponLevelTag.drawGlyph(shapeRenderer, glyphX[slotIndex], glyphY[slotIndex], standing);
        }
        shapeRenderer.end();
    }

    // -------------------------------------------------------------------------
    // Private — slot data helpers
    // -------------------------------------------------------------------------

    private SlotState getSlotState(int slotIndex) {
        // The third gun slot is locked unless a QUARTERMASTER build opened it (balance-overhaul order 5).
        if (slotIndex == GUN_SLOT_3_INDEX
                && (playerInventory == null || playerInventory.getLoadout().isSlotLocked(slotIndex))) {
            return SlotState.LOCKED;
        }
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

        // Weapon name — primary (R6); the stats under it are secondary
        font.setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN,
                      ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
        font.draw(spriteBatch, weapon.getDisplayName(), textX, textY);
        glyphLayout.setText(font, weapon.getDisplayName());
        float tagX = textX + glyphLayout.width + ItemConstants.INV_WEAPON_LEVEL_TAG_GAP;

        // "LV n" after the name, coloured by the gap to the floor threat level (R14 d).
        int standing = WeaponLevelTag.standing(weapon, threatLevel);
        tagColor.set(ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                     ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
        WeaponLevelTag.colorFor(standing, tagColor, tagColor);
        font.setColor(tagColor);
        String tagLabel = WeaponLevelTag.label(weapon.getWeaponLevel());
        font.draw(spriteBatch, tagLabel, tagX, textY);
        if (WeaponLevelTag.hasGlyph(standing)) {
            glyphLayout.setText(font, tagLabel);
            glyphStanding[slotIndex] = standing;
            glyphX[slotIndex] = tagX + glyphLayout.width + HudConstants.HUD_LEVEL_TAG_CHEVRON_GAP;
            glyphY[slotIndex] = textY + ItemConstants.INV_WEAPON_LEVEL_GLYPH_LIFT;
        }
        textY -= lineStep;

        // Ammo type
        setSecondary();
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
        font.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN,
                      ArtDirection.UI_INK_BLACK_BLUE, 1f);
        font.draw(spriteBatch, "ACTIVE",
                  badgeX + (ItemConstants.INV_WEAPON_ACTIVE_BADGE_WIDTH  - glyphLayout.width)  / 2f,
                  badgeY + (ItemConstants.INV_WEAPON_ACTIVE_BADGE_HEIGHT + glyphLayout.height) / 2f);
    }

    private void drawLockedSlotText(float slotX, float slotY, float slotW) {
        float lockCenterX = slotX + ItemConstants.INV_WEAPON_LOCK_CENTER_X_OFFSET;

        // "LOCKED" centered below lock body
        glyphLayout.setText(font, "LOCKED");
        setDisabled();
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

    private void setSecondary() {
        font.setColor(ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                      ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
    }

    private void setDisabled() {
        font.setColor(ArtDirection.UI_TEXT_DISABLED_RED, ArtDirection.UI_TEXT_DISABLED_GREEN,
                      ArtDirection.UI_TEXT_DISABLED_BLUE, 1f);
    }

    // -------------------------------------------------------------------------
    // Dispose
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        // Shared resources owned by InventoryOverlayRenderer coordinator — not disposed here.
    }
}
