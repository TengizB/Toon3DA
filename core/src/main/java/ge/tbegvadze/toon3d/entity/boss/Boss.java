package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * A boss entity — a special Enemy with a two-phase attack pattern system,
 * a DangerTileSet for telegraph overlays, and arena-level state tracking.
 *
 * Boss AI is driven entirely by BossFloorController (a TickSubscriber);
 * EnemyManager's normal AI is bypassed for Boss instances via an instanceof check.
 * The boss health scales with depth via GameMath.bossDepthScaledStat().
 */
public class Boss extends Enemy {

    /** Display name shown in the intro cutscene and the full-screen HP bar. */
    public final String bossName;
    /** Flavour subtitle shown below the boss name during the intro. */
    public final String bossEpithet;
    /** One-line text shown in the HUD event feed when the boss is killed. */
    public final String killLine;

    /** Boss accent color (red channel) — used for the HP bar and telegraph overlays. */
    public final float accentRed;
    /** Boss accent color (green channel). */
    public final float accentGreen;
    /** Boss accent color (blue channel). */
    public final float accentBlue;

    /** Height multiplier applied over the normal enemy sprite height (e.g. 2.0 = double-height). */
    public final float bossHeightMultiplier;

    /** Current phase: 1 = initial phase, 2 = triggered after crossing phase-2 HP threshold. */
    public int phase = 1;

    /** True during the one-tick invulnerability window between phase 1 and phase 2. */
    public boolean invulnerable = false;
    /** Remaining ticks of phase-transition invulnerability. */
    public int invulnerableTurns = 0;

    /** Attack pattern for phase 1. */
    public final BossAttackPattern phase1Pattern;
    /** Attack pattern for phase 2. */
    public final BossAttackPattern phase2Pattern;

    /** Live danger-tile state for telegraph → resolve mechanics. Pre-allocated; never replaced. */
    public final DangerTileSet dangerTileSet = new DangerTileSet();

    /** Monotonically increasing count of ticks since the boss awakened. Drives pattern sequencing. */
    public long ticksSinceAwaken = 0L;

    /** True after the boss first detects the player — gates the intro sequence and arena lock. */
    public boolean hasAwakened = false;

    public Boss(EnemyType type, int tileColumn, int tileRow,
                String bossName, String bossEpithet, String killLine,
                float accentRed, float accentGreen, float accentBlue,
                float bossHeightMultiplier,
                BossAttackPattern phase1Pattern, BossAttackPattern phase2Pattern) {
        super(type, tileColumn, tileRow);
        this.bossName             = bossName;
        this.bossEpithet          = bossEpithet;
        this.killLine             = killLine;
        this.accentRed            = accentRed;
        this.accentGreen          = accentGreen;
        this.accentBlue           = accentBlue;
        this.bossHeightMultiplier = bossHeightMultiplier;
        this.phase1Pattern        = phase1Pattern;
        this.phase2Pattern        = phase2Pattern;
    }

    /** Returns the active attack pattern for the current phase. */
    public BossAttackPattern activePattern() {
        return phase == 1 ? phase1Pattern : phase2Pattern;
    }

    /**
     * Returns true when HP has fallen to or below the phase-2 HP threshold.
     * Used by BossFloorController to trigger the phase transition.
     */
    public boolean isAtPhase2Threshold() {
        return maxHealth > 0 && (float) health / (float) maxHealth <= Constants.BOSS_PHASE2_HP_THRESHOLD;
    }

    /**
     * Damage is silently absorbed while the boss is invulnerable during phase transition.
     */
    @Override
    public void applyDamage(int amount) {
        if (invulnerable) return;
        super.applyDamage(amount);
    }
}
