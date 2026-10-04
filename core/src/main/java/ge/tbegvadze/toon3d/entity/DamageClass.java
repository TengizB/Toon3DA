package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * The DAMAGE CLASS a weapon deals (balance-overhaul order 3, rule M1) — one axis of the matchup
 * table, the other being {@link ge.tbegvadze.toon3d.enemy.EnemyTrait}. Every concrete
 * {@link Weapon} declares its class as data through {@link Weapon#damageClass()}; damage code looks
 * the multiplier up in {@link MatchupTable} by ordinal and never switches on the class.
 *
 * <p><b>Ordinal order is load-bearing:</b> it is the column order of every
 * {@code BalanceConfig.MATCHUP_ROW_*} array. Append new classes at the end and widen every row.
 *
 * <p>The constant itself is the GLYPH ID: the render layer keys its pre-built procedural glyph
 * (bullet, spread fan, bolt, rail line, flame, blast star, blade, hammer) by this constant. The
 * colour is carried as three floats so this enum stays headless (no LibGDX import); renderers build
 * their {@code Color} from {@link #colorRed()}/{@link #colorGreen()}/{@link #colorBlue()} once.
 */
public enum DamageClass {

    /** Assault Rifle, Chaingun — the reliable generalist, resisted by armour. */
    BALLISTIC(WeaponConstants.DAMAGE_CLASS_COLOR_BALLISTIC_RED,
              WeaponConstants.DAMAGE_CLASS_COLOR_BALLISTIC_GREEN,
              WeaponConstants.DAMAGE_CLASS_COLOR_BALLISTIC_BLUE),
    /** Shotgun, Double-Barrel Shotgun — point-blank burst. */
    SPREAD(WeaponConstants.DAMAGE_CLASS_COLOR_SPREAD_RED,
           WeaponConstants.DAMAGE_CLASS_COLOR_SPREAD_GREEN,
           WeaponConstants.DAMAGE_CLASS_COLOR_SPREAD_BLUE),
    /** Plasma Rifle, Arc Cannon — cracks machine shields. */
    ENERGY(WeaponConstants.DAMAGE_CLASS_COLOR_ENERGY_RED,
           WeaponConstants.DAMAGE_CLASS_COLOR_ENERGY_GREEN,
           WeaponConstants.DAMAGE_CLASS_COLOR_ENERGY_BLUE),
    /** Railgun — cracks golem plate. */
    RAIL(WeaponConstants.DAMAGE_CLASS_COLOR_RAIL_RED,
         WeaponConstants.DAMAGE_CLASS_COLOR_RAIL_GREEN,
         WeaponConstants.DAMAGE_CLASS_COLOR_RAIL_BLUE),
    /** Incinerator (and fire tiles, for matchup purposes) — groups and the undead. */
    FIRE(WeaponConstants.DAMAGE_CLASS_COLOR_FIRE_RED,
         WeaponConstants.DAMAGE_CLASS_COLOR_FIRE_GREEN,
         WeaponConstants.DAMAGE_CLASS_COLOR_FIRE_BLUE),
    /** Grenade Launcher (and explosive barrels, for matchup purposes). */
    EXPLOSIVE(WeaponConstants.DAMAGE_CLASS_COLOR_EXPLOSIVE_RED,
              WeaponConstants.DAMAGE_CLASS_COLOR_EXPLOSIVE_GREEN,
              WeaponConstants.DAMAGE_CLASS_COLOR_EXPLOSIVE_BLUE),
    /** Combat Knife, Chainsaw. */
    BLADE(WeaponConstants.DAMAGE_CLASS_COLOR_BLADE_RED,
          WeaponConstants.DAMAGE_CLASS_COLOR_BLADE_GREEN,
          WeaponConstants.DAMAGE_CLASS_COLOR_BLADE_BLUE),
    /** Fist, Hammer. */
    BLUNT(WeaponConstants.DAMAGE_CLASS_COLOR_BLUNT_RED,
          WeaponConstants.DAMAGE_CLASS_COLOR_BLUNT_GREEN,
          WeaponConstants.DAMAGE_CLASS_COLOR_BLUNT_BLUE);

    private final float colorRed;
    private final float colorGreen;
    private final float colorBlue;

    DamageClass(float colorRed, float colorGreen, float colorBlue) {
        this.colorRed = colorRed;
        this.colorGreen = colorGreen;
        this.colorBlue = colorBlue;
    }

    /** Red channel (0..1) of this class's colour (glyphs, EFFECTIVE trait glyph tint). */
    public float colorRed() {
        return colorRed;
    }

    /** Green channel (0..1) of this class's colour. */
    public float colorGreen() {
        return colorGreen;
    }

    /** Blue channel (0..1) of this class's colour. */
    public float colorBlue() {
        return colorBlue;
    }
}
