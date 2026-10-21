package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.enemy.EnemyVisualEvent;
import ge.tbegvadze.toon3d.enemy.EnemyVisualMoment;
import ge.tbegvadze.toon3d.entity.MatchupOutcome;
import ge.tbegvadze.toon3d.util.EffectConstants;

import java.util.EnumMap;

/**
 * THE CLIP-SELECTION TABLE (generated-enemy-art order 2, rule R1) as data rows: which clip a simulation
 * moment starts, at what priority, and which enemy states HOLD a clip. Highest priority wins, so a hit
 * interrupts an attack, an attack interrupts a held wind-up, and nothing interrupts a death:
 * <pre>
 *   DEATH 7 > HURT_HEAVY / HURT_LIGHT 6 > ATTACK 5 > WIND_UP (held) 4 > DEFEND (held) 3 > ALERT 2 > MOVE 1 > IDLE 0
 * </pre>
 * A finished one-shot falls back to the highest ongoing condition (a held state, a running slide, idle) —
 * that is not a row, it is {@link EnemyAnimator#currentFrame} picking the best candidate every frame.
 *
 * <p>A row's behaviour is its {@link Kind}, an enum with a per-constant body (the {@code EnemyType}
 * idiom), so applying a moment is one virtual call and never a switch.
 *
 * <p>ORDER 3 — REACTIONS. DAMAGED is no longer one clip: {@link HitReaction} reads the hit (absorbed by a
 * shard, eaten by Block, RESISTED by the matchup, heavy or light by the 25% rule) and each answer is a row of
 * data — the clip, the family burst, the recoil, the drop, whether the white flash shows, the block shimmer —
 * so hurt / resisted / blocked / absorbed always differ (R2). STATUS_APPLIED fires the status's onset burst
 * ({@link EnemyStatusVisuals}); ATTACK_COMMITTED schedules a ranged creature's projectile; DIED adds the kill
 * and death bursts and the remains. A STUNNED / staggered enemy holds HURT_HEAVY's last pose (DAZED 4).
 */
public final class EnemyAnimationRules {

    public static final int PRIORITY_IDLE    = 0;
    public static final int PRIORITY_MOVE    = 1;
    public static final int PRIORITY_ALERT   = 2;
    public static final int PRIORITY_DEFEND  = 3;
    public static final int PRIORITY_WIND_UP = 4;
    /** Order 3: STUNNED / staggered — HURT_HEAVY's last pose, held while the enemy is dazed. */
    public static final int PRIORITY_DAZED   = 4;
    public static final int PRIORITY_ATTACK  = 5;
    public static final int PRIORITY_HURT    = 6;
    public static final int PRIORITY_DEATH   = 7;

    /** What a row does when its moment arrives. */
    public enum Kind {
        /** Plays the row's clip once on top of whatever is lower priority. */
        ONE_SHOT {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                animator.playOneShot(row.clip, row.priority);
            }
        },
        /** Order 3: the strike's clip, and a ranged creature's shot scheduled for the clip's emit frame. */
        ATTACK {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                animator.playOneShot(row.clip, row.priority);
                layer.scheduleShot(animator, event.enemy);
            }
        },
        /** Order 3: a player hit, answered by its {@link HitReaction} row. */
        HIT {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                layer.react(animator, event, HitReaction.classify(event));
            }
        },
        /** Order 3: a stagger or riposte — the heavy flinch with a heavy kick (the dazed hold follows by state). */
        STAGGER {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                layer.react(animator, event, HitReaction.STAGGERED);
            }
        },
        /** Order 3: a status landed — its onset burst, if the status has one. */
        STATUS {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                layer.statusApplied(animator, event.enemy, event.status);
            }
        },
        /** Starts the row's clip from frame 0 into its held last frame; the enemy's STATE then keeps it held. */
        HOLD {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                animator.beginHold(row.clip, row.priority);
            }
        },
        /** A cosmetic tile slide from the moment's {@code from} tile to its {@code to} tile (MOVE loops during it). */
        SLIDE {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                int tiles = Math.abs(event.toColumn - event.fromColumn) + Math.abs(event.toRow - event.fromRow);
                float seconds = Math.min(EffectConstants.ENEMY_VISUAL_SLIDE_MAX_SECONDS,
                        tiles * EffectConstants.ENEMY_VISUAL_SLIDE_SECONDS_PER_TILE);
                animator.beginSlide(event.fromColumn, event.fromRow, event.toColumn, event.toRow, seconds);
            }
        },
        /** Pushed: a short slide AND the row's flinch. */
        KNOCKBACK {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                animator.beginSlide(event.fromColumn, event.fromRow, event.toColumn, event.toRow,
                        EffectConstants.ENEMY_VISUAL_KNOCKBACK_SECONDS);
                animator.playOneShot(row.clip, row.priority);
            }
        },
        /** The enemy is about to be removed: kill + death bursts, the dying pool plays DEATH (R4), then its remains. */
        DEATH {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                layer.beginDying(animator, event.enemy, event.deathCause);
            }
        };

        abstract void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row);
    }

    /** One moment's rule. */
    public static final class Row {
        public final EnemyVisualMoment moment;
        public final Kind              kind;
        public final EnemyClipId       clip;
        public final int               priority;

        Row(EnemyVisualMoment moment, Kind kind, EnemyClipId clip, int priority) {
            this.moment   = moment;
            this.kind     = kind;
            this.clip     = clip;
            this.priority = priority;
        }
    }

    /**
     * HOW A CREATURE ANSWERS A HIT (order 3, guide 8) — one row per outcome, so the four reads can never
     * collapse into one (R2):
     * <pre>
     *   outcome      clip        family burst  recoil  drop  white flash  extra
     *   HURT_LIGHT   HURT_LIGHT  HIT           light   3 px  yes
     *   HURT_HEAVY   HURT_HEAVY  HIT_HEAVY     heavy   6 px  yes          (>= 25% of max HP)
     *   STAGGERED    HURT_HEAVY  -             heavy   6 px  (the hit's)  then the dazed hold
     *   RESISTED     none        RESIST        shove   0     no           (one hard spark / a dull chip)
     *   BLOCKED      none        -             nudge   0     no           a steel-blue shimmer down the body
     *   ABSORBED     none        -             none    0     (gold)       the existing Auric gold flash
     * </pre>
     * A KILLING blow is not a row: DIED follows it at once and plays DEATH with the KILL burst.
     */
    public enum HitReaction {
        HURT_LIGHT(EnemyClipId.HURT_LIGHT, EnemyEffectSet.BurstId.HIT,
                EffectConstants.ENEMY_VISUAL_RECOIL_LIGHT_TILES, EffectConstants.ENEMY_VISUAL_RECOIL_LIGHT_DROP_PIXELS,
                false, false),
        HURT_HEAVY(EnemyClipId.HURT_HEAVY, EnemyEffectSet.BurstId.HIT_HEAVY,
                EffectConstants.ENEMY_VISUAL_RECOIL_HEAVY_TILES, EffectConstants.ENEMY_VISUAL_RECOIL_HEAVY_DROP_PIXELS,
                false, false),
        STAGGERED(EnemyClipId.HURT_HEAVY, null,
                EffectConstants.ENEMY_VISUAL_RECOIL_HEAVY_TILES, EffectConstants.ENEMY_VISUAL_RECOIL_HEAVY_DROP_PIXELS,
                false, false),
        RESISTED(null, EnemyEffectSet.BurstId.RESIST, EffectConstants.ENEMY_VISUAL_RECOIL_RESIST_TILES, 0f, true, false),
        BLOCKED(null, null, EffectConstants.ENEMY_VISUAL_RECOIL_BLOCK_TILES, 0f, true, true),
        ABSORBED(null, null, 0f, 0f, false, false);

        /** The flinch, or null: a hit that did not hurt does not flinch (guide 8). */
        public final EnemyClipId           clip;
        public final EnemyEffectSet.BurstId burst;
        public final float                 recoilTiles;
        public final float                 dropPixels;
        public final boolean               suppressFlash;
        public final boolean               shimmer;

        HitReaction(EnemyClipId clip, EnemyEffectSet.BurstId burst, float recoilTiles, float dropPixels,
                    boolean suppressFlash, boolean shimmer) {
            this.clip = clip; this.burst = burst; this.recoilTiles = recoilTiles; this.dropPixels = dropPixels;
            this.suppressFlash = suppressFlash; this.shimmer = shimmer;
        }

        /** The row a DAMAGED payload names. Order matters: a shard absorb beats Block beats the matchup. */
        public static HitReaction classify(EnemyVisualEvent event) {
            if (event.absorbed) return ABSORBED;
            if (event.blocked)  return BLOCKED;
            if (event.outcome == MatchupOutcome.RESISTED) return RESISTED;
            return event.healthFraction >= EffectConstants.ENEMY_VISUAL_HURT_HEAVY_HEALTH_FRACTION ? HURT_HEAVY : HURT_LIGHT;
        }
    }

    /** A state that keeps a clip held on its last frame while the simulation stays in it. */
    public static final class Hold {
        public final EnemyClipId clip;
        public final int         priority;

        Hold(EnemyClipId clip, int priority) {
            this.clip     = clip;
            this.priority = priority;
        }
    }

    private static final Row[] ROWS = new Row[EnemyVisualMoment.COUNT];
    private static final EnumMap<EnemyState, Hold> HOLDS = new EnumMap<>(EnemyState.class);

    static {
        register(EnemyVisualMoment.ALERTED,          Kind.ONE_SHOT,  EnemyClipId.ALERT,      PRIORITY_ALERT);
        register(EnemyVisualMoment.MOVE_STARTED,     Kind.SLIDE,     EnemyClipId.MOVE,       PRIORITY_MOVE);
        register(EnemyVisualMoment.WIND_UP_STARTED,  Kind.HOLD,      EnemyClipId.WIND_UP,    PRIORITY_WIND_UP);
        register(EnemyVisualMoment.ATTACK_COMMITTED, Kind.ATTACK,    EnemyClipId.ATTACK,     PRIORITY_ATTACK);
        register(EnemyVisualMoment.ABILITY_USED,     Kind.ONE_SHOT,  EnemyClipId.ATTACK,     PRIORITY_ATTACK);
        register(EnemyVisualMoment.DAMAGED,          Kind.HIT,       null,                   PRIORITY_HURT);
        register(EnemyVisualMoment.STAGGERED,        Kind.STAGGER,   EnemyClipId.HURT_HEAVY, PRIORITY_HURT);
        register(EnemyVisualMoment.KNOCKED_BACK,     Kind.KNOCKBACK, EnemyClipId.HURT_HEAVY, PRIORITY_HURT);
        register(EnemyVisualMoment.DEFEND_STARTED,   Kind.HOLD,      EnemyClipId.DEFEND,     PRIORITY_DEFEND);
        register(EnemyVisualMoment.STATUS_APPLIED,   Kind.STATUS,    null,                   PRIORITY_IDLE);
        register(EnemyVisualMoment.DIED,             Kind.DEATH,     EnemyClipId.DEATH,      PRIORITY_DEATH);

        hold(EnemyState.WINDING_UP,       EnemyClipId.WIND_UP, PRIORITY_WIND_UP);
        hold(EnemyState.SELF_DESTRUCTING, EnemyClipId.WIND_UP, PRIORITY_WIND_UP);
        hold(EnemyState.DEFENDING,        EnemyClipId.DEFEND,  PRIORITY_DEFEND);

        for (EnemyVisualMoment moment : EnemyVisualMoment.values()) {
            if (ROWS[moment.ordinal()] == null) {
                throw new IllegalStateException("no animation rule for " + moment);
            }
        }
    }

    private EnemyAnimationRules() {
    }

    private static void register(EnemyVisualMoment moment, Kind kind, EnemyClipId clip, int priority) {
        ROWS[moment.ordinal()] = new Row(moment, kind, clip, priority);
    }

    private static void hold(EnemyState state, EnemyClipId clip, int priority) {
        HOLDS.put(state, new Hold(clip, priority));
    }

    public static Row rowFor(EnemyVisualMoment moment) {
        return ROWS[moment.ordinal()];
    }

    /** The clip {@code state} holds, or null when the state holds nothing. */
    public static Hold holdFor(EnemyState state) {
        return HOLDS.get(state);
    }
}
