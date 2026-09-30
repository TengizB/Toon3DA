package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.util.HudConstants;

/**
 * Shared "LV n" weapon-level tag rules (balance-overhaul order-1, R14 a/d): the label string, the
 * colour a weapon's level earns against the floor THREAT LEVEL, and the procedural arrow glyph.
 * Stateless apart from the pre-built label table, so nothing allocates while drawing.
 */
public final class WeaponLevelTag {

    /** L equals d (or the weapon is level-gap exempt): the caller's normal text colour. */
    public static final int STANDING_NORMAL = 0;
    /** L = d - 1: amber. */
    public static final int STANDING_AMBER  = 1;
    /** L <= d - 2: red with a down arrow. */
    public static final int STANDING_RED    = 2;
    /** L > d: green with an up arrow. */
    public static final int STANDING_GREEN  = 3;

    private static final String[] LABELS = buildLabels();

    private WeaponLevelTag() { }

    private static String[] buildLabels() {
        String[] labels = new String[HudConstants.HUD_LEVEL_TAG_CACHE_MAX + 1];
        for (int level = 0; level < labels.length; level++) {
            labels[level] = HudConstants.HUD_LEVEL_TAG_PREFIX + level;
        }
        return labels;
    }

    /** "LV n" — a cached string for ordinary levels. */
    public static String label(int level) {
        if (level >= 0 && level < LABELS.length) return LABELS[level];
        return HudConstants.HUD_LEVEL_TAG_PREFIX + level;
    }

    /** Classifies a level gap (weapon level minus floor threat level; 0 for the exempt Fist). */
    public static int standingForGap(int levelGap) {
        if (levelGap > 0) return STANDING_GREEN;
        if (levelGap <= -HudConstants.HUD_LEVEL_RED_GAP) return STANDING_RED;
        if (levelGap <= -HudConstants.HUD_LEVEL_AMBER_GAP) return STANDING_AMBER;
        return STANDING_NORMAL;
    }

    /** Classifies a weapon against the floor threat level. */
    public static int standing(Weapon weapon, int threatLevel) {
        return standingForGap(weapon.getLevelStanding(threatLevel));
    }

    /** Writes the tag colour for a standing into {@code out}; normal standing copies {@code normal}. */
    public static Color colorFor(int standing, Color normal, Color out) {
        switch (standing) {
            case STANDING_AMBER:
                return out.set(HudConstants.HUD_LEVEL_AMBER_R, HudConstants.HUD_LEVEL_AMBER_G,
                               HudConstants.HUD_LEVEL_AMBER_B, 1f);
            case STANDING_RED:
                return out.set(HudConstants.HUD_LEVEL_RED_R, HudConstants.HUD_LEVEL_RED_G,
                               HudConstants.HUD_LEVEL_RED_B, 1f);
            case STANDING_GREEN:
                return out.set(HudConstants.HUD_LEVEL_GREEN_R, HudConstants.HUD_LEVEL_GREEN_G,
                               HudConstants.HUD_LEVEL_GREEN_B, 1f);
            default:
                return out.set(normal);
        }
    }

    /** True when the standing carries an arrow glyph. */
    public static boolean hasGlyph(int standing) {
        return standing == STANDING_RED || standing == STANDING_GREEN;
    }

    /**
     * Draws the 3-stroke arrow (stem + two head strokes) in a WIDTH x HEIGHT box whose bottom-left is
     * ({@code boxX}, {@code boxY}). Down arrow for RED, up arrow for GREEN. Must be called between
     * {@code begin(Line)} and {@code end()} with the colour already set.
     */
    public static void drawGlyph(ShapeRenderer shapes, float boxX, float boxY, int standing) {
        float width   = HudConstants.HUD_LEVEL_CHEVRON_WIDTH;
        float height  = HudConstants.HUD_LEVEL_CHEVRON_HEIGHT;
        float centerX = boxX + width / 2f;
        float tipY    = standing == STANDING_RED ? boxY : boxY + height;
        float tailY   = standing == STANDING_RED ? boxY + height : boxY;
        // Head strokes start half the box height back from the tip, at the box's left/right edges.
        float headY   = tipY + (tailY - tipY) / 2f;
        shapes.line(centerX, tailY, centerX, tipY);
        shapes.line(boxX, headY, centerX, tipY);
        shapes.line(boxX + width, headY, centerX, tipY);
    }
}
