package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.CombatPalette;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * WHAT EACH STATUS LOOKS LIKE ON A MIGRATED CREATURE (generated-enemy-art order 3) — one data row per
 * {@link StatusType}, never a switch: the repeating burst while the status is active and where on the body it
 * comes from, the burst its onset fires (STATUS_APPLIED), and a body tint for a status the shared enemy pass
 * does not already tint. The dazed hold of STUNNED is the animator's (it reads the status directly).
 *
 * <p>Colours obey the semantic table (art-direction V5): fire and toxin come from the shared STATUS set, which
 * is the same on every family; BLEED is the family's own leak (sparks off a machine, ichor off a tick — never
 * blood). VULNERABLE / WEAK / EXPOSED and the BURNING / POISONED wash are tinted by {@code EnemyRenderer} for
 * every enemy, migrated or not, so their rows carry no tint here. BLINDED and SLOWED are only ever put on the
 * player today; their rows are empty, and adding a look is filling the row.
 */
public final class EnemyStatusVisuals {

    public static final class Row {
        public final StatusType                    status;
        /** Repeats while active, at {@link #placement}; null = no particles. */
        public final EnemyEffectSet.BurstId        loopBurst;
        public final EnemyEffectSet.PlacementId    placement;
        /** Fired once when the status lands; null = none. */
        public final EnemyEffectSet.BurstId        onsetBurst;
        /** Body tint over the graded colour while active; strength 0 = none. */
        public final float tintRed, tintGreen, tintBlue, tintStrength;

        Row(StatusType status, EnemyEffectSet.BurstId loopBurst, EnemyEffectSet.PlacementId placement,
            EnemyEffectSet.BurstId onsetBurst, float tintRed, float tintGreen, float tintBlue, float tintStrength) {
            this.status = status; this.loopBurst = loopBurst; this.placement = placement; this.onsetBurst = onsetBurst;
            this.tintRed = tintRed; this.tintGreen = tintGreen; this.tintBlue = tintBlue; this.tintStrength = tintStrength;
        }
    }

    private static final Row[] ROWS = new Row[StatusType.values().length];

    static {
        //        status                 loop burst                              placement                               onset burst
        particles(StatusType.BURNING,    EnemyEffectSet.BurstId.BURNING,  EnemyEffectSet.PlacementId.BURN,   EnemyEffectSet.BurstId.BURNING_ONSET);
        particles(StatusType.POISONED,   EnemyEffectSet.BurstId.POISONED, EnemyEffectSet.PlacementId.POISON, null);
        particles(StatusType.BLEED,      EnemyEffectSet.BurstId.BLEED,    EnemyEffectSet.PlacementId.BODY,   null);
        particles(StatusType.STUNNED,    EnemyEffectSet.BurstId.STUNNED,  EnemyEffectSet.PlacementId.STUN,   null);
        tint(StatusType.EMPOWERED, CombatPalette.BUFF_RED, CombatPalette.BUFF_GREEN, CombatPalette.BUFF_BLUE);
        none(StatusType.BLINDED);
        none(StatusType.SLOWED);
        none(StatusType.VULNERABLE);   // shared tint (EnemyRenderer)
        none(StatusType.WEAK);         // shared tint (EnemyRenderer)
        none(StatusType.EXPOSED);      // shared tint (EnemyRenderer)
        for (StatusType status : StatusType.values()) {
            if (ROWS[status.ordinal()] == null) throw new IllegalStateException("no status visual row for " + status);
        }
    }

    private EnemyStatusVisuals() {
    }

    private static void particles(StatusType status, EnemyEffectSet.BurstId loopBurst,
                                  EnemyEffectSet.PlacementId placement, EnemyEffectSet.BurstId onsetBurst) {
        ROWS[status.ordinal()] = new Row(status, loopBurst, placement, onsetBurst, 0f, 0f, 0f, 0f);
    }

    private static void tint(StatusType status, float red, float green, float blue) {
        ROWS[status.ordinal()] = new Row(status, null, EnemyEffectSet.PlacementId.BODY, null, red, green, blue,
                EffectConstants.ENEMY_STATUS_TINT_STRENGTH);
    }

    private static void none(StatusType status) {
        ROWS[status.ordinal()] = new Row(status, null, EnemyEffectSet.PlacementId.BODY, null, 0f, 0f, 0f, 0f);
    }

    public static Row rowFor(StatusType status) {
        return ROWS[status.ordinal()];
    }

    public static int count() {
        return ROWS.length;
    }

    public static Row get(int index) {
        return ROWS[index];
    }
}
