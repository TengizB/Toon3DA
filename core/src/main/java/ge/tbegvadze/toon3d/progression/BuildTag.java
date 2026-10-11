package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.entity.DamageClass;

import java.util.EnumSet;

/**
 * THE BUILD TAGS (balance-overhaul order 5, rule B1) — the nine styles a run can lean into. Every
 * {@link Perk} and {@link Keystone} carries exactly one tag; the level-up offer rules (B3) read a carried
 * weapon's tag through {@link #forDamageClass} to mark a perk as SYNERGY, and the R-PERK / R-CARRY audit
 * values a perk on its tag's niche.
 *
 * <p>The DamageClass -> tag mapping is DATA on each constant ({@link #damageClasses}), read by a loop —
 * never a switch — so a new damage class is mapped by adding it to one constant's set. Six tags own a
 * weapon class; BULWARK, SCAVENGER and VITALITY own none (they are played with any gun). Chip colours are
 * a render concern and do not live here.
 *
 * <p>Headless: no LibGDX imports.
 */
public enum BuildTag {

    /** SPREAD — the shotguns. */
    CLOSE_QUARTERS("CLOSE QUARTERS", EnumSet.of(DamageClass.SPREAD)),
    /** BALLISTIC and RAIL — the rifles, the chaingun and the railgun. */
    MARKSMAN("MARKSMAN", EnumSet.of(DamageClass.BALLISTIC, DamageClass.RAIL)),
    /** FIRE — the Incinerator. */
    PYRO("PYRO", EnumSet.of(DamageClass.FIRE)),
    /** EXPLOSIVE — the Grenade Launcher (and barrels). */
    DEMOLITION("DEMOLITION", EnumSet.of(DamageClass.EXPLOSIVE)),
    /** ENERGY — the Plasma Rifle and the Arc Cannon. */
    ENERGY("ENERGY", EnumSet.of(DamageClass.ENERGY)),
    /** The guard stance, riposte and armour — no weapon class. */
    BULWARK("BULWARK", EnumSet.noneOf(DamageClass.class)),
    /** BLADE and BLUNT — the melee weapons. */
    BLADE("BLADE", EnumSet.of(DamageClass.BLADE, DamageClass.BLUNT)),
    /** Supply, shop and carriers — no weapon class. */
    SCAVENGER("SCAVENGER", EnumSet.noneOf(DamageClass.class)),
    /** Max HP, heals and the last-chance save — no weapon class. */
    VITALITY("VITALITY", EnumSet.noneOf(DamageClass.class));

    private final String displayName;
    private final EnumSet<DamageClass> damageClasses;

    BuildTag(String displayName, EnumSet<DamageClass> damageClasses) {
        this.displayName   = displayName;
        this.damageClasses = damageClasses;
    }

    /** UI text for the tag chip, e.g. "CLOSE QUARTERS". */
    public String displayName() {
        return displayName;
    }

    /** True when weapons of this damage class belong to this tag. */
    public boolean ownsDamageClass(DamageClass damageClass) {
        return damageClass != null && damageClasses.contains(damageClass);
    }

    /** True when this tag owns at least one weapon class (an offence tag). */
    public boolean isWeaponTag() {
        return !damageClasses.isEmpty();
    }

    /**
     * The tag a weapon of the given damage class belongs to (B1), or null for a null class. Looked up by
     * a loop over the per-constant data, never a switch.
     */
    public static BuildTag forDamageClass(DamageClass damageClass) {
        if (damageClass == null) return null;
        for (BuildTag tag : values()) {
            if (tag.damageClasses.contains(damageClass)) return tag;
        }
        return null;
    }
}
