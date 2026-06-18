package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;

/**
 * The shared contract for every weapon consumer (HUD, fire handler, inspect overlay,
 * AbilityResolver, EnemyManager hit pipeline). All gameplay logic must operate through
 * this interface — never cast to a concrete subclass.
 *
 * Weapon.java declares {@code implements WeaponProfile} (added in weapon-system-order-2).
 * Every existing subclass automatically satisfies the interface once Weapon adds the
 * missing methods in that order.
 */
public interface WeaponProfile {

    // ── Identity / loot ────────────────────────────────────────────────────
    String     getDisplayName();
    WeaponTier getTier();
    int        getWeaponLevel();   // 1..WeaponConstants.MAX_WEAPON_LEVEL (10)
    boolean    isMelee();

    // ── Effective stats (base * level scaling) ─────────────────────────────
    // These are the ONLY numbers gameplay logic should read; never bypass with base fields.
    int   getEffectiveDamage();
    float getEffectiveAccuracy();      // [0..1]; 1.0 = never misses
    int   getEffectiveClipSize();
    int   getEffectiveReloadTicks();
    int   getEffectiveRange();         // tiles; melee always returns 1

    // ── Abilities ──────────────────────────────────────────────────────────
    int             getAbilityCount();
    AbilityInstance getAbility(int index);
    boolean         hasAbility(WeaponAbility ability);
    float           abilityMagnitude(WeaponAbility ability);  // 0f if absent
    int             abilityCount(WeaponAbility ability);       // 0 if absent

    // ── Unified actions ────────────────────────────────────────────────────
    // These already exist on Weapon; declared here so callers use the interface.
    boolean canFire();
    boolean requestManualReload();

    // ── Ammo ───────────────────────────────────────────────────────────────
    /** Returns the ammo type this weapon consumes per shot, or null for melee weapons. */
    AmmoType getAmmoType();

    // ── HUD readouts ───────────────────────────────────────────────────────
    int    getShotsInClip();
    int    getReserveAmmo();
    String hudAmmoString();
}
