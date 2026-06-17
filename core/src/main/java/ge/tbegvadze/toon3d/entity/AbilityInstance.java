package ge.tbegvadze.toon3d.entity;

public final class AbilityInstance {

    public final WeaponAbility ability;
    public final float         magnitude;   // e.g. 0.185 for 18.5% crit chance
    public final int           countValue;  // e.g. 3 for Burst×3; 0 for non-count abilities

    public AbilityInstance(WeaponAbility ability, float magnitude, int countValue) {
        this.ability    = ability;
        this.magnitude  = magnitude;
        this.countValue = countValue;
    }

    /** Magnitude as an integer percent (for display). */
    public int magnitudePercent() {
        return Math.round(magnitude * 100f);
    }
}
