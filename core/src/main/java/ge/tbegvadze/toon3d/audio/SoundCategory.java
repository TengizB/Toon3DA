package ge.tbegvadze.toon3d.audio;

/**
 * The five mix buses every sound belongs to (procedural-sound-effects order 1).
 *
 * <p>A category is not a volume group — it is what {@link SoundMixer} counts.  Turn-based combat
 * resolves every enemy in the SAME instant, so the per-turn voice cap and the stack falloff are
 * applied PER CATEGORY: three enemies swinging must read as three attacks rather than one smear,
 * while the player's own gunshot in the same instant must not be damped by them at all.
 *
 * <p>Headless by design — no LibGDX import, so {@code sim/} can construct the whole model.
 */
public enum SoundCategory {

    /** The gun in the player's hands. Never attenuated, never panned, never damped by anything. */
    PLAYER_WEAPON,
    /** What happens TO the player: hurt, guarded, healed, killed. */
    PLAYER_STATE,
    /** Everything an enemy does — the only category with a per-turn voice cap. */
    ENEMY,
    /** The facility itself: doors, barrels, stairs, pickups lying in the world. */
    ENVIRONMENT,
    /** Non-diegetic confirmations: weapon switch, level-up, kill confirm. */
    INTERFACE;

    public static final int COUNT = values().length;
}
