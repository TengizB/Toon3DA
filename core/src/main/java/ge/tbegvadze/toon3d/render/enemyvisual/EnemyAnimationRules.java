package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.enemy.EnemyVisualEvent;
import ge.tbegvadze.toon3d.enemy.EnemyVisualMoment;
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
 * idiom), so applying a moment is one virtual call and never a switch. Order 3 maps the moments this
 * order leaves silent (STATUS_APPLIED, resisted / blocked / absorbed reads) by changing rows here.
 */
public final class EnemyAnimationRules {

    public static final int PRIORITY_IDLE    = 0;
    public static final int PRIORITY_MOVE    = 1;
    public static final int PRIORITY_ALERT   = 2;
    public static final int PRIORITY_DEFEND  = 3;
    public static final int PRIORITY_WIND_UP = 4;
    public static final int PRIORITY_ATTACK  = 5;
    public static final int PRIORITY_HURT    = 6;
    public static final int PRIORITY_DEATH   = 7;

    /** What a row does when its moment arrives. */
    public enum Kind {
        /** Reported, but no clip in this order. */
        NONE {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
            }
        },
        /** Plays the row's clip once on top of whatever is lower priority. */
        ONE_SHOT {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                EnemyClipId clip = row.clipFor(event);
                if (clip != null) animator.playOneShot(clip, row.priority);
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
        /** The enemy is about to be removed: hand it to the dying pool, which plays DEATH (rule R4). */
        DEATH {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                layer.beginDying(animator, event.enemy);
            }
        };

        abstract void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row);
    }

    /** One moment's rule. {@code heavyClip}, when set, replaces {@code clip} for a heavy hit (the 25% rule). */
    public static final class Row {
        public final EnemyVisualMoment moment;
        public final Kind              kind;
        public final EnemyClipId       clip;
        public final EnemyClipId       heavyClip;
        public final int               priority;

        Row(EnemyVisualMoment moment, Kind kind, EnemyClipId clip, EnemyClipId heavyClip, int priority) {
            this.moment    = moment;
            this.kind      = kind;
            this.clip      = clip;
            this.heavyClip = heavyClip;
            this.priority  = priority;
        }

        /**
         * The clip this event plays. A hit that never reached the body (a shard absorbed it, Block ate it)
         * does not flinch — guide 8: "resists / blocks: no flinch" (order 3 adds the spark / shimmer read).
         */
        EnemyClipId clipFor(EnemyVisualEvent event) {
            if (event.moment.carriesHit && (event.absorbed || event.blocked)) return null;
            if (heavyClip == null) return clip;
            boolean heavy = !event.moment.carriesHit
                    || event.healthFraction >= EffectConstants.ENEMY_VISUAL_HURT_HEAVY_HEALTH_FRACTION;
            return heavy ? heavyClip : clip;
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
        register(EnemyVisualMoment.ALERTED,          Kind.ONE_SHOT,  EnemyClipId.ALERT,      null,                   PRIORITY_ALERT);
        register(EnemyVisualMoment.MOVE_STARTED,     Kind.SLIDE,     EnemyClipId.MOVE,       null,                   PRIORITY_MOVE);
        register(EnemyVisualMoment.WIND_UP_STARTED,  Kind.HOLD,      EnemyClipId.WIND_UP,    null,                   PRIORITY_WIND_UP);
        register(EnemyVisualMoment.ATTACK_COMMITTED, Kind.ONE_SHOT,  EnemyClipId.ATTACK,     null,                   PRIORITY_ATTACK);
        register(EnemyVisualMoment.ABILITY_USED,     Kind.ONE_SHOT,  EnemyClipId.ATTACK,     null,                   PRIORITY_ATTACK);
        register(EnemyVisualMoment.DAMAGED,          Kind.ONE_SHOT,  EnemyClipId.HURT_LIGHT, EnemyClipId.HURT_HEAVY, PRIORITY_HURT);
        register(EnemyVisualMoment.STAGGERED,        Kind.ONE_SHOT,  EnemyClipId.HURT_HEAVY, null,                   PRIORITY_HURT);
        register(EnemyVisualMoment.KNOCKED_BACK,     Kind.KNOCKBACK, EnemyClipId.HURT_HEAVY, null,                   PRIORITY_HURT);
        register(EnemyVisualMoment.DEFEND_STARTED,   Kind.HOLD,      EnemyClipId.DEFEND,     null,                   PRIORITY_DEFEND);
        register(EnemyVisualMoment.STATUS_APPLIED,   Kind.NONE,      null,                   null,                   PRIORITY_IDLE);
        register(EnemyVisualMoment.DIED,             Kind.DEATH,     EnemyClipId.DEATH,      null,                   PRIORITY_DEATH);

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

    private static void register(EnemyVisualMoment moment, Kind kind, EnemyClipId clip, EnemyClipId heavyClip,
                                 int priority) {
        ROWS[moment.ordinal()] = new Row(moment, kind, clip, heavyClip, priority);
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
