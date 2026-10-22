package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.enemy.EnemyVisualEvent;
import ge.tbegvadze.toon3d.enemy.EnemyVisualMoment;
import ge.tbegvadze.toon3d.enemy.SpecialAbility;
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
 * ({@link EnemyStatusVisuals}); a ranged shot is scheduled from {@code EnemyAttackListener.onRangedAttack} (aimed at the committed lane, see
 * {@link EnemyVisualLayer#scheduleShot}); DIED adds the kill
 * and death bursts and the remains. A STUNNED / staggered enemy holds HURT_HEAVY's last pose (DAZED 4).
 */
public final class EnemyAnimationRules {

    public static final int PRIORITY_IDLE    = 0;
    public static final int PRIORITY_MOVE    = 1;
    public static final int PRIORITY_ALERT   = 2;
    public static final int PRIORITY_DEFEND  = 3;
    public static final int PRIORITY_WIND_UP = 4;
    /** Order 3: STUNNED / staggered — HURT_HEAVY's last pose, held while dazed; wins a tie with a held WIND_UP. */
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
        /**
         * Order 4: a SpecialAbility resolved — the ability's own clip from the ability rows when the creature
         * ships it (BUFF_SELF, AREA_STRIKE; order 5 DEBUFF_PLAYER, SELF_DESTRUCT; order 6 SUMMON), otherwise the row's
         * clip (ATTACK), at the row's priority.
         */
        ABILITY {
            @Override void apply(EnemyVisualLayer layer, EnemyAnimator animator, EnemyVisualEvent event, Row row) {
                EnemyClipId clip = abilityClipFor(event.ability);
                if (clip == null || animator.getVisual().manifest.clip(clip) == null) clip = row.clip;
                animator.playOneShot(clip, row.priority);
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

    /**
     * A state that keeps a clip held on its last frame while the simulation stays in it. Order 5: a hold may name a
     * {@code fallback} for a creature that does not ship its clip (SELF_DESTRUCTING holds PRIMING, else WIND_UP).
     */
    public static final class Hold {
        public final EnemyClipId clip;
        public final EnemyClipId fallback;
        public final int         priority;

        Hold(EnemyClipId clip, EnemyClipId fallback, int priority) {
            this.clip     = clip;
            this.fallback = fallback;
            this.priority = priority;
        }

        /** The clip this hold shows on {@code manifest}: its own when shipped, else the fallback (may be null). */
        public EnemyClipId clipIn(EnemyClipManifest manifest) {
            return manifest.clip(clip) != null || fallback == null ? clip : fallback;
        }
    }

    /**
     * Order 5: how a creature dies by CAUSE - the clip the dying instance plays (its last frame is the remains) and
     * the creature's own death burst. A cause without a row, or a creature that lacks the row's clip or burst, uses
     * DEATH and the DEATH burst. A self-destruct bursts (the Plague Hulk's DEATH_BURST + DETONATE).
     */
    public static final class DeathRow {
        public final EnemyClipId           clip;
        public final EnemyEffectSet.BurstId burst;

        DeathRow(EnemyClipId clip, EnemyEffectSet.BurstId burst) {
            this.clip  = clip;
            this.burst = burst;
        }
    }

    private static final Row[] ROWS = new Row[EnemyVisualMoment.COUNT];
    private static final EnumMap<EnemyState, Hold> HOLDS = new EnumMap<>(EnemyState.class);
    /**
     * Order 4: a HEAVY committed without a winding-up state (AREA_STRIKE is planned as a heavy while the enemy
     * stays ATTACKING) holds WIND_UP for the telegraph turn, exactly as WINDING_UP does.
     */
    private static final Hold HEAVY_COMMITTED_HOLD = new Hold(EnemyClipId.WIND_UP, null, PRIORITY_WIND_UP);
    /** Order 4: the clip an ability plays when the creature ships it (absent = the ABILITY_USED row's ATTACK). */
    private static final EnumMap<SpecialAbility, EnemyClipId> ABILITY_CLIPS = new EnumMap<>(SpecialAbility.class);
    /** Order 5: death by cause (absent = DEATH with the DEATH burst). */
    private static final EnumMap<EnemyVisualEvent.DeathCause, DeathRow> DEATHS = new EnumMap<>(EnemyVisualEvent.DeathCause.class);

    static {
        register(EnemyVisualMoment.ALERTED,          Kind.ONE_SHOT,  EnemyClipId.ALERT,      PRIORITY_ALERT);
        register(EnemyVisualMoment.MOVE_STARTED,     Kind.SLIDE,     EnemyClipId.MOVE,       PRIORITY_MOVE);
        register(EnemyVisualMoment.WIND_UP_STARTED,  Kind.HOLD,      EnemyClipId.WIND_UP,    PRIORITY_WIND_UP);
        register(EnemyVisualMoment.ATTACK_COMMITTED, Kind.ONE_SHOT,  EnemyClipId.ATTACK,     PRIORITY_ATTACK);
        register(EnemyVisualMoment.ABILITY_USED,     Kind.ABILITY,   EnemyClipId.ATTACK,     PRIORITY_ATTACK);
        register(EnemyVisualMoment.DAMAGED,          Kind.HIT,       null,                   PRIORITY_HURT);
        register(EnemyVisualMoment.STAGGERED,        Kind.STAGGER,   EnemyClipId.HURT_HEAVY, PRIORITY_HURT);
        register(EnemyVisualMoment.KNOCKED_BACK,     Kind.KNOCKBACK, EnemyClipId.HURT_HEAVY, PRIORITY_HURT);
        register(EnemyVisualMoment.DEFEND_STARTED,   Kind.HOLD,      EnemyClipId.DEFEND,     PRIORITY_DEFEND);
        register(EnemyVisualMoment.STATUS_APPLIED,   Kind.STATUS,    null,                   PRIORITY_IDLE);
        register(EnemyVisualMoment.DIED,             Kind.DEATH,     EnemyClipId.DEATH,      PRIORITY_DEATH);

        hold(EnemyState.WINDING_UP,       EnemyClipId.WIND_UP, null,                PRIORITY_WIND_UP);
        // Order 5 (R4): a priming self-destruct swells (PRIMING), never the brace; WIND_UP for a creature without it.
        hold(EnemyState.SELF_DESTRUCTING, EnemyClipId.PRIMING, EnemyClipId.WIND_UP, PRIORITY_WIND_UP);
        hold(EnemyState.DEFENDING,        EnemyClipId.DEFEND,  null,                PRIORITY_DEFEND);

        // Order 4: ability -> clip. The strike area marker stays with EnemyAttackEffectSystem; the clip owns the body.
        abilityClip(SpecialAbility.BUFF_SELF,   EnemyClipId.BUFF_SELF);
        abilityClip(SpecialAbility.AREA_STRIKE, EnemyClipId.AREA_STRIKE);
        // Order 5: the Aberrations' abilities (the self-destruct row plays on each countdown turn).
        abilityClip(SpecialAbility.DEBUFF_PLAYER, EnemyClipId.DEBUFF_PLAYER);
        abilityClip(SpecialAbility.SELF_DESTRUCT, EnemyClipId.SELF_DESTRUCT);
        // Order 6: the Undead's carrier (the summoned dead arrive through ALERTED, like any spawn).
        abilityClip(SpecialAbility.SUMMON,        EnemyClipId.SUMMON);

        death(EnemyVisualEvent.DeathCause.SELF_DESTRUCT, EnemyClipId.DEATH_BURST, EnemyEffectSet.BurstId.DETONATE);

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

    private static void hold(EnemyState state, EnemyClipId clip, EnemyClipId fallback, int priority) {
        HOLDS.put(state, new Hold(clip, fallback, priority));
    }

    private static void death(EnemyVisualEvent.DeathCause cause, EnemyClipId clip, EnemyEffectSet.BurstId burst) {
        DEATHS.put(cause, new DeathRow(clip, burst));
    }

    /** The clip a creature with {@code manifest} dies by {@code cause}: the cause's row when it ships it, else DEATH. */
    public static EnemyClipId deathClipFor(EnemyVisualEvent.DeathCause cause, EnemyClipManifest manifest) {
        DeathRow row = cause != null ? DEATHS.get(cause) : null;
        return row != null && manifest.clip(row.clip) != null ? row.clip : EnemyClipId.DEATH;
    }

    /** The death burst for {@code cause} (the row's, else DEATH); the caller falls back to DEATH when a set lacks it. */
    public static EnemyEffectSet.BurstId deathBurstFor(EnemyVisualEvent.DeathCause cause) {
        DeathRow row = cause != null ? DEATHS.get(cause) : null;
        return row != null ? row.burst : EnemyEffectSet.BurstId.DEATH;
    }

    private static void abilityClip(SpecialAbility ability, EnemyClipId clip) {
        ABILITY_CLIPS.put(ability, clip);
    }

    /** The clip {@code ability} plays when a creature ships it, or null (then ABILITY_USED plays ATTACK). */
    public static EnemyClipId abilityClipFor(SpecialAbility ability) {
        return ability != null ? ABILITY_CLIPS.get(ability) : null;
    }

    public static Row rowFor(EnemyVisualMoment moment) {
        return ROWS[moment.ordinal()];
    }

    /**
     * The clip {@code enemy} holds: its state's row, else (order 4) WIND_UP while a heavy is committed; null when
     * it holds nothing.
     */
    public static Hold holdFor(Enemy enemy) {
        Hold hold = HOLDS.get(enemy.state);
        if (hold == null && enemy.plannedAction.heavyAttack != null) hold = HEAVY_COMMITTED_HOLD;
        return hold;
    }

    /** True while {@code enemy} holds its telegraph (WIND_UP, or the PRIMING swell that stands in for it): CHARGE repeats. */
    public static boolean holdsWindUp(Enemy enemy) {
        Hold hold = holdFor(enemy);
        return hold != null && (hold.clip == EnemyClipId.WIND_UP || hold.fallback == EnemyClipId.WIND_UP);
    }
}
