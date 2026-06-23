package ge.tbegvadze.toon3d.entity;

/**
 * Immutable snapshot of a weapon's rolled identity: level, tier, and abilities.
 * Stored in GroundItem so the specific variant of a weapon can be displayed when
 * the player steps on it and then applied to the arsenal singleton on pickup via
 * Weapon.configureRoll(). null weaponRoll = treat as Common Lv1, no abilities.
 */
public final class WeaponRoll {

    public final int               weaponLevel;
    public final WeaponTier        tier;
    public final AbilityInstance[] abilities;

    public WeaponRoll(int weaponLevel, WeaponTier tier, AbilityInstance[] abilities) {
        this.weaponLevel = weaponLevel;
        this.tier        = tier;
        this.abilities   = abilities != null ? abilities : new AbilityInstance[0];
    }

    /**
     * Returns true when this roll describes the same weapon variant as the other:
     * identical level, tier, and ability set. Two weapons of the same type but with a
     * different level OR tier OR ability loadout are NOT the same variant — the player
     * should be allowed to swap between them.
     */
    public boolean matches(WeaponRoll other) {
        if (other == null) return false;
        if (this.weaponLevel != other.weaponLevel) return false;
        if (this.tier != other.tier) return false;
        if (this.abilities.length != other.abilities.length) return false;
        for (int abilityIndex = 0; abilityIndex < abilities.length; abilityIndex++) {
            AbilityInstance mine   = this.abilities[abilityIndex];
            AbilityInstance theirs = other.abilities[abilityIndex];
            if (mine == null || theirs == null) {
                if (mine != theirs) return false;
                continue;
            }
            if (mine.ability != theirs.ability
                    || mine.magnitude != theirs.magnitude
                    || mine.countValue != theirs.countValue) {
                return false;
            }
        }
        return true;
    }

    /** Snapshots the current rolled state of a live weapon into a new WeaponRoll. */
    public static WeaponRoll fromWeapon(Weapon weapon) {
        if (weapon == null) return null;
        AbilityInstance[] snapshot = new AbilityInstance[weapon.getAbilityCount()];
        for (int abilityIndex = 0; abilityIndex < snapshot.length; abilityIndex++) {
            snapshot[abilityIndex] = weapon.getAbility(abilityIndex);
        }
        return new WeaponRoll(weapon.getWeaponLevel(), weapon.getTier(), snapshot);
    }
}
