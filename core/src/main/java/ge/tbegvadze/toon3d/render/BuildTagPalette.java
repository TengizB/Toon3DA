package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import ge.tbegvadze.toon3d.entity.DamageClass;
import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.util.ProgressionConstants;

import java.util.EnumMap;

/**
 * The chip colour of every {@link BuildTag} (balance-overhaul order 5, CP5). One table, indexed by
 * {@code BuildTag.ordinal()}, built once: the six weapon tags borrow the order-3 damage-class colour of one
 * representative class (so a CLOSE QUARTERS chip is the SPREAD orange the weapon glyphs already wear), and the
 * three tags that own no weapon class read {@link ProgressionConstants}. Never a switch.
 */
public final class BuildTagPalette {

    private static final Color[] COLORS = new Color[BuildTag.values().length];

    static {
        EnumMap<BuildTag, DamageClass> representativeClass = new EnumMap<>(BuildTag.class);
        representativeClass.put(BuildTag.CLOSE_QUARTERS, DamageClass.SPREAD);
        representativeClass.put(BuildTag.MARKSMAN,       DamageClass.BALLISTIC);
        representativeClass.put(BuildTag.PYRO,           DamageClass.FIRE);
        representativeClass.put(BuildTag.DEMOLITION,     DamageClass.EXPLOSIVE);
        representativeClass.put(BuildTag.ENERGY,         DamageClass.ENERGY);
        representativeClass.put(BuildTag.BLADE,          DamageClass.BLADE);
        for (BuildTag tag : BuildTag.values()) {
            DamageClass damageClass = representativeClass.get(tag);
            if (damageClass != null) {
                COLORS[tag.ordinal()] = new Color(damageClass.colorRed(), damageClass.colorGreen(),
                        damageClass.colorBlue(), 1f);
            }
        }
        COLORS[BuildTag.BULWARK.ordinal()] = new Color(ProgressionConstants.BUILD_TAG_BULWARK_RED,
                ProgressionConstants.BUILD_TAG_BULWARK_GREEN, ProgressionConstants.BUILD_TAG_BULWARK_BLUE, 1f);
        COLORS[BuildTag.VITALITY.ordinal()] = new Color(ProgressionConstants.BUILD_TAG_VITALITY_RED,
                ProgressionConstants.BUILD_TAG_VITALITY_GREEN, ProgressionConstants.BUILD_TAG_VITALITY_BLUE, 1f);
        COLORS[BuildTag.SCAVENGER.ordinal()] = new Color(ProgressionConstants.BUILD_TAG_SCAVENGER_RED,
                ProgressionConstants.BUILD_TAG_SCAVENGER_GREEN, ProgressionConstants.BUILD_TAG_SCAVENGER_BLUE, 1f);
    }

    private BuildTagPalette() {}

    /** The shared (read-only, never mutate) chip colour of a tag. */
    public static Color colorOf(BuildTag tag) {
        return COLORS[tag.ordinal()];
    }
}
