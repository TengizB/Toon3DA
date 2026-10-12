package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * All rollable weapon abilities. Each constant carries:
 *   family        — restricts to GUN, MELEE, or UNIVERSAL weapons
 *   trigger       — when the effect fires; PASSIVE means always-on
 *   rank          — STANDARD (UNCOMMON+), MAJOR (EPIC+, the "big felt" list of B5) or SIGNATURE (the
 *                   legendary signature slot); legendaryOnly == (rank == SIGNATURE)
 *   buildTag      — the BuildTag this ability counts toward for perk synergy (B6; never null)
 *   displayName   — title-case label shown in the HUD and ItemWindow ability rows
 *   hudGlyph      — single character for compact HUD displays
 *   shortHint     — one-line summary shown in the ItemWindow ability row (≤50 chars)
 *   fullDescription — 2-4 sentence explanation shown in AbilityWindow
 *   affectsLines  — bullet list for the AFFECTS block in AbilityWindow (2-3 items)
 *
 * PASSIVE/ACTIVE badge shown in UI is derived from trigger:
 *   trigger == PASSIVE → "PASSIVE"; all other triggers → "ACTIVE"
 *
 * <p><b>Pruned by balance-overhaul order 5 (B6).</b> Every ability must price at least
 * {@code ABILITY_MIN_FELT_PP} at its expected weapon level. Thirteen that could not were CUT
 * (HEMORRHAGE_HARVEST, VAMPIRIC_CRIT, ADRENAL_SURGE, BULWARK_ROUNDS, SECOND_WIND, SALVAGE_STRIKE,
 * SCHOLARS_EDGE, QUICK_HANDS, EXTENDED_MAG, FIELD_MEDIC_ROUNDS, CREDIT_FANG, MARKSMANS_PATIENCE,
 * RESONANT_ROUNDS) and OVERPENETRATION was MERGED into ARMOR_PIERCE (which now also pierces
 * {@code ARMOR_PIERCE_EXTRA_TARGETS} extra enemies in line).
 */
public enum WeaponAbility {

    // ── OFFENSIVE ──────────────────────────────────────────────────────────
    BURST_FIRE(
        Family.GUN, Trigger.ON_FIRE, Rank.MAJOR, BuildTag.CLOSE_QUARTERS, "Burst Fire", 'B',
        "Fires multiple rounds per attack action.",
        "Each trigger pull releases a burst of rounds in rapid succession. Hit and damage are resolved individually per round. High sustained output at the cost of ammo consumption.",
        new String[]{"Rounds per attack", "Ammo per attack"}
    ),
    CRITICAL_STRIKE(
        Family.UNIVERSAL, Trigger.ON_HIT, Rank.STANDARD, BuildTag.MARKSMAN, "Critical Strike", 'C',
        "Attacks have a chance to deal bonus damage.",
        "A portion of attacks land on critical weak points, dealing substantially increased damage. Critical hit chance and bonus both scale with weapon level. Pairs well with high-speed weapons.",
        new String[]{"Critical hit chance %", "Crit damage multiplier"}
    ),
    ARMOR_PIERCE(
        Family.UNIVERSAL, Trigger.PASSIVE, Rank.STANDARD, BuildTag.DEMOLITION, "Armor Pierce", 'A',
        "Bypasses enemy Block; shots pierce an extra enemy.",
        "Each attack ignores a percentage of the target's Block — the shield enemies raise to absorb damage — so part of the hit bleeds straight through to HP, and every shot carries on through one more enemy in line. Weapons that already pierce everything (Plasma, Railgun) instead hit harder past the first enemy.",
        new String[]{"Block bypassed per hit %", "Extra enemies pierced"}
    ),
    EXECUTIONER(
        Family.UNIVERSAL, Trigger.ON_HIT, Rank.MAJOR, BuildTag.BLADE, "Executioner", 'X',
        "Bonus damage against enemies below 25% HP.",
        "When the target's health drops below 25%, this weapon deals additional damage per hit. The bonus scales with weapon level. Ideal for eliminating stubborn, nearly-dead enemies quickly.",
        new String[]{"Damage bonus vs low-HP targets", "Threshold: below 25% HP"}
    ),
    REND(
        Family.UNIVERSAL, Trigger.ON_HIT, Rank.STANDARD, BuildTag.BLADE, "Rend", 'R',
        "Hits apply a bleed DoT for 2 turns.",
        "Successful hits apply a bleeding effect that deals damage over the next two turns. A new hit resets the bleed duration rather than stacking it. Effective for sustained damage on tanky targets.",
        new String[]{"Bleed damage per turn", "Duration: 2 turns"}
    ),
    STAGGER_ROUNDS(
        Family.GUN, Trigger.ON_HIT, Rank.STANDARD, BuildTag.CLOSE_QUARTERS, "Stagger Rounds", 'S',
        "Hits have a chance to slow enemy reactions.",
        "A percentage of hits cause the target to stagger, delaying or interrupting its next action. Stagger chance scales with weapon level. Useful for buying time during difficult encounters.",
        new String[]{"Stagger chance %", "Enemy action delay"}
    ),
    KINETIC_SLAM(
        Family.MELEE, Trigger.ON_HIT, Rank.STANDARD, BuildTag.BULWARK, "Kinetic Slam", 'K',
        "Heavy blows have a chance to stun for 1 turn.",
        "Melee attacks with sufficient force may stun the target, preventing any action for one turn. Stun chance scales with weapon level. Cannot stun the same enemy on consecutive turns.",
        new String[]{"Stun chance %", "Stun duration: 1 turn"}
    ),
    CLEAVE(
        Family.MELEE, Trigger.ON_HIT, Rank.MAJOR, BuildTag.BLADE, "Cleave", 'V',
        "Melee attacks splash damage to adjacent enemies.",
        "A fraction of the primary hit damage spreads to all enemies in adjacent tiles. Splash damage does not trigger on-hit secondary effects. Excellent for clearing groups in corridors or rooms.",
        new String[]{"Splash damage fraction %", "Adjacent tile coverage"}
    ),
    INCENDIARY(
        Family.UNIVERSAL, Trigger.ON_HIT, Rank.STANDARD, BuildTag.PYRO, "Incendiary", 'I',
        "Hits apply burning for 2 turns.",
        "Each successful hit ignites the target, dealing fire damage over the next two turns. A new hit refreshes the duration rather than stacking. Highly effective against slow or clustered enemies.",
        new String[]{"Burn damage per turn", "Duration: 2 turns"}
    ),

    // ── DEFENSIVE / SUSTAIN ────────────────────────────────────────────────
    LIFESTEAL(
        Family.UNIVERSAL, Trigger.ON_HIT, Rank.STANDARD, BuildTag.VITALITY, "Lifesteal", 'L',
        "A portion of damage dealt restores HP.",
        "A percentage of all damage inflicted is returned to the player as health. Scales with weapon level. Sustains extended engagements without burning medkits.",
        new String[]{"HP restored per hit %", "Works on every hit"}
    ),

    // ── UTILITY / ECONOMY ─────────────────────────────────────────────────
    SCAVENGER_ROUNDS(
        Family.GUN, Trigger.ON_KILL, Rank.STANDARD, BuildTag.SCAVENGER, "Scavenger Rounds", 'Z',
        "Kills have a chance to refund rounds to the reserve.",
        "Each kill rolls a chance to return rounds of this weapon's ammo straight to the reserve. Refund chance scales with weapon level. Helps offset the high consumption cost of burst and rapid-fire weapons.",
        new String[]{"Ammo drop chance %", "Ammo type: matches weapon"}
    ),

    // ── HYBRID / SITUATIONAL ──────────────────────────────────────────────
    POINT_BLANK(
        Family.GUN, Trigger.PASSIVE, Rank.STANDARD, BuildTag.CLOSE_QUARTERS, "Point Blank", 'P',
        "Bonus damage when attacking at 1-2 tile range.",
        "When the target is within one or two tiles, this weapon deals significantly increased damage. The bonus falls off at longer ranges. Ideal for corridor ambushes and aggressive close-quarters positioning.",
        new String[]{"Damage bonus at close range", "Range threshold: 1-2 tiles"}
    ),
    OPENING_SALVO(
        Family.UNIVERSAL, Trigger.PASSIVE, Rank.MAJOR, BuildTag.MARKSMAN, "Opening Salvo", 'U',
        "First shot of a new encounter deals bonus damage.",
        "The first attack fired upon a fresh enemy encounter deals increased damage. The bonus resets when combat ends. Rewards initiating with a decisive, high-value opening strike.",
        new String[]{"First-shot damage bonus", "Resets: per encounter"}
    ),
    RHYTHM(
        Family.GUN, Trigger.PASSIVE, Rank.STANDARD, BuildTag.ENERGY, "Rhythm", 'Y',
        "Consecutive hits on the same target ramp damage.",
        "Each successive hit on the same enemy increases damage by a stacking bonus. The ramp resets on a miss or when switching targets. Rewards sustained focus-fire on a single enemy.",
        new String[]{"Damage ramp per hit", "Resets on miss or switch"}
    ),
    STATIC_DISCHARGE(
        Family.UNIVERSAL, Trigger.ON_KILL, Rank.STANDARD, BuildTag.ENERGY, "Static Discharge", '~',
        "Killing an enemy releases a small damage burst.",
        "Defeating an enemy releases a kinetic burst that deals minor damage to all enemies in adjacent tiles. Does not harm the player. Effective for finishing fights in clustered enemy packs.",
        new String[]{"Burst damage on kill", "Radius: adjacent tiles"}
    ),

    // ── LEGENDARY SIGNATURES (legendaryOnly = true) ───────────────────────
    SOULFORGE(
        Family.UNIVERSAL, Trigger.ON_KILL, Rank.SIGNATURE, BuildTag.SCAVENGER, "Soulforge", '*',
        "Legendary — kills restore HP and refresh attack.",
        "Each kill regenerates a portion of health and resets the weapon's cooldown once per encounter. Forged from UAC experimental data. Sustains long combat chains without burning resources.",
        new String[]{"HP on kill", "Attack refresh: once per encounter"}
    ),
    JUDGMENT(
        Family.GUN, Trigger.ON_FIRE, Rank.SIGNATURE, BuildTag.MARKSMAN, "Judgment", '!',
        "Legendary — first shot of combat always crits.",
        "The opening shot of every new engagement is guaranteed to land as a critical hit. Resets when a new encounter begins. Devastating when combined with high base-damage weapons.",
        new String[]{"Guaranteed critical: first shot", "Resets: per encounter"}
    ),
    HELLFIRE_NOVA(
        Family.UNIVERSAL, Trigger.ON_CRIT, Rank.SIGNATURE, BuildTag.PYRO, "Hellfire Nova", '@',
        "Legendary — crits trigger a 1-tile AoE burst.",
        "Landing a critical hit releases a nova burst striking all enemies within one tile of the target. Nova damage scales with weapon level. Paired with high crit-chance builds, this clears groups rapidly.",
        new String[]{"Nova burst on critical", "AoE radius: 1 tile"}
    ),
    BERSERKERS_OATH(
        Family.MELEE, Trigger.PASSIVE, Rank.SIGNATURE, BuildTag.BLADE, "Berserker's Oath", '^',
        "Legendary — below 20% HP, every hit crits.",
        "While your health remains below 20%, every melee attack is a guaranteed critical hit. A legendary pact forged from desperation. Punishes careless play; rewards the utterly fearless.",
        new String[]{"100% crit rate at low HP", "Threshold: below 20% HP"}
    );

    public enum Family  { GUN, MELEE, UNIVERSAL }
    public enum Trigger { PASSIVE, ON_HIT, ON_KILL, ON_CRIT, ON_FIRE, ON_RELOAD }
    /**
     * Where an ability may roll (balance-overhaul order 5, B5): STANDARD from UNCOMMON up, MAJOR on EPIC and
     * LEGENDARY only (exactly {@code WeaponTier.majorCount} of them), SIGNATURE in the legendary signature slot.
     */
    public enum Rank    { STANDARD, MAJOR, SIGNATURE }

    public final Family   family;
    public final Trigger  trigger;
    public final Rank     rank;
    /** True for the legendary signature slot only (== rank == SIGNATURE). */
    public final boolean  legendaryOnly;
    /** The build tag this ability counts toward for B3 perk synergy (balance-overhaul order 5, B6). */
    public final BuildTag buildTag;
    public final String   displayName;
    public final char     hudGlyph;
    public final String   shortHint;
    public final String   fullDescription;
    public final String[] affectsLines;

    WeaponAbility(Family family, Trigger trigger, Rank rank, BuildTag buildTag,
                  String displayName, char hudGlyph,
                  String shortHint, String fullDescription, String[] affectsLines) {
        this.family          = family;
        this.trigger         = trigger;
        this.rank            = rank;
        this.legendaryOnly   = rank == Rank.SIGNATURE;
        this.buildTag        = buildTag;
        this.displayName     = displayName;
        this.hudGlyph        = hudGlyph;
        this.shortHint       = shortHint;
        this.fullDescription = fullDescription;
        this.affectsLines    = affectsLines;
    }

    /** Whether this ability is on the MAJOR list (EPIC+ only; B5). */
    public boolean isMajor() {
        return rank == Rank.MAJOR;
    }

    /** "PASSIVE" when trigger is PASSIVE; "ACTIVE" for all other triggers. */
    public String getTypeLabel() {
        return trigger == Trigger.PASSIVE ? "PASSIVE" : "ACTIVE";
    }

    /**
     * Whether this ability may be rolled onto a given weapon.
     * GUN abilities require !isMelee; MELEE abilities require isMelee;
     * UNIVERSAL matches both. LegendaryOnly abilities only roll into the
     * legendary signature slot (caller enforces this separately).
     */
    public boolean eligibleFor(boolean isMeleeWeapon) {
        if (family == Family.GUN   && isMeleeWeapon)  return false;
        if (family == Family.MELEE && !isMeleeWeapon) return false;
        return true;
    }

    // =========================================================================
    // Dynamic description builders — substitute live numbers from AbilityInstance
    // =========================================================================

    /** A fractional magnitude as a whole percent, for UI text (e.g. a cap of 0.14 -> 14). */
    private static int wholePercent(float fraction) {
        return Math.round(fraction * 100f);
    }

    /**
     * Returns a full description string with exact numbers from the given instance.
     * Replaces the static fullDescription field when the UI has a real AbilityInstance.
     */
    public String buildDescription(AbilityInstance instance) {
        int pct   = instance.magnitudePercent();
        int flat  = Math.round(instance.magnitude);
        int count = instance.countValue;
        switch (this) {
            case BURST_FIRE:
                return "Fires " + count + " rounds per trigger pull, each resolved individually for hit and damage. "
                     + "Consumes " + count + "× ammo per attack action. High sustained output at the cost of rapid ammo drain.";
            case CRITICAL_STRIKE:
                return pct + "% of attacks strike critical weak points, dealing 2× damage (100% bonus). "
                     + "Crit chance scales with weapon level up to " + wholePercent(GameBalance.CRIT_CHANCE_CAP)
                     + "%. Pairs well with high-speed weapons.";
            case ARMOR_PIERCE:
                return "Every attack ignores " + pct + "% of the target's Block, so that fraction of the hit "
                     + "bleeds through the shield to HP, and every shot pierces " + count + " more "
                     + (count == 1 ? "enemy" : "enemies") + " in line. On weapons that already pierce "
                     + "(Plasma, Railgun), each enemy past the first instead takes +"
                     + wholePercent(GameBalance.ARMOR_PIERCE_ALREADY_PIERCING_BONUS) + "% damage.";
            case EXECUTIONER:
                return "Deals +" + pct + "% bonus damage against targets below 25% HP. "
                     + "Bonus scales with weapon level up to +" + wholePercent(GameBalance.EXECUTIONER_BONUS_CAP)
                     + "%. Ideal for finishing stubborn, nearly-dead enemies.";
            case REND:
                return "Hits apply bleeding: " + flat + " damage per turn for 4 turns. "
                     + "A new hit refreshes the duration rather than stacking. Effective against tanky targets.";
            case STAGGER_ROUNDS:
                return pct + "% of hits delay or interrupt the target's next action for 1 turn. "
                     + "Stagger chance scales with weapon level up to " + wholePercent(GameBalance.STAGGER_CHANCE_CAP)
                     + "%. Useful for buying time in difficult encounters.";
            case KINETIC_SLAM:
                return pct + "% chance per hit to stun the target for 1 turn, preventing any action. "
                     + "Cannot stun the same enemy on consecutive turns. Melee only.";
            case CLEAVE:
                return pct + "% of the primary hit damage spreads to all enemies in adjacent tiles. "
                     + "Splash does not trigger secondary on-hit effects. Excellent for clearing corridors and rooms.";
            case INCENDIARY:
                return "Hits ignite the target: " + flat + " fire damage per turn for 3 turns. "
                     + "A new hit refreshes the duration rather than stacking. Effective against slow or clustered enemies.";
            case LIFESTEAL:
                return pct + "% of all damage dealt is returned to you as HP. "
                     + "Scales with weapon level up to " + wholePercent(GameBalance.LIFESTEAL_CAP)
                     + "%. Sustains extended engagements without burning medkits.";
            case SCAVENGER_ROUNDS:
                return pct + "% chance on kill to refund " + count + " rounds of this weapon's ammo to the reserve. "
                     + "Refund chance scales with weapon level up to " + wholePercent(GameBalance.SCAVENGER_CHANCE_CAP)
                     + "%. Offsets the ammo cost of burst-fire weapons.";
            case POINT_BLANK:
                return "+" + pct + "% bonus damage when the target is within 1 tile. "
                     + "Scales with weapon level up to +" + wholePercent(GameBalance.POINT_BLANK_BONUS_CAP)
                     + "%. Ideal for corridor ambushes and aggressive close-quarters positioning.";
            case OPENING_SALVO:
                return "First attack on a fresh enemy deals +" + pct + "% bonus damage. "
                     + "Bonus scales with weapon level up to +" + wholePercent(GameBalance.OPENING_SALVO_BONUS_CAP)
                     + "%. Resets when combat ends or a new encounter begins.";
            case RHYTHM: {
                int totalRhythmBonus = pct * GameBalance.RHYTHM_MAX_STACKS;
                return "+" + pct + "% bonus damage per consecutive hit on the same target (max "
                     + GameBalance.RHYTHM_MAX_STACKS + " stacks = +" + totalRhythmBonus + "% total). "
                     + "Resets on a miss or when switching targets. Rewards sustained focus-fire on a single enemy.";
            }
            case STATIC_DISCHARGE:
                return "On kill: releases a burst dealing " + flat + " damage to all enemies in adjacent tiles. "
                     + "Scales with weapon level up to " + GameBalance.STATIC_SPLASH_CAP
                     + " damage. Effective for finishing fights in clustered enemy packs.";
            case SOULFORGE:
                return "Every 5 kills permanently raises this weapon's level by 1. "
                     + "A legendary pact that lets the weapon grow with you. Sustains long combat chains without burning resources.";
            case JUDGMENT:
                return "Every 5 fires: releases a guaranteed critical lance dealing 3× damage, piercing up to 20 tiles. "
                     + "Resets when a new encounter begins. Devastating on high base-damage weapons.";
            case HELLFIRE_NOVA:
                return "Landing a critical hit releases a nova burst dealing 75% of your crit damage to all enemies within 2 tiles. "
                     + "Does not harm the player. Paired with high crit-chance builds, clears groups rapidly.";
            case BERSERKERS_OATH:
                return "Below 20% HP: every melee attack is a guaranteed critical hit. "
                     + "Each kill below 20% HP stacks +10% damage (max 5 stacks = +50% total). Melee only. Punishes caution, rewards the fearless.";
            default:
                return fullDescription;
        }
    }

    /**
     * Returns a short one-line hint with exact numbers from the given instance.
     * Replaces the static shortHint field when the UI has a real AbilityInstance.
     */
    public String buildShortHint(AbilityInstance instance) {
        int pct   = instance.magnitudePercent();
        int flat  = Math.round(instance.magnitude);
        int count = instance.countValue;
        switch (this) {
            case BURST_FIRE:          return "Fires " + count + " rounds per attack action.";
            case CRITICAL_STRIKE:     return pct + "% crit chance; 2× damage on every crit.";
            case ARMOR_PIERCE:        return "Ignores " + pct + "% Block; pierces " + count + " more in line.";
            case EXECUTIONER:         return "+" + pct + "% damage vs enemies below 25% HP.";
            case REND:                return "Applies bleed: " + flat + " dmg/turn for 4 turns.";
            case STAGGER_ROUNDS:      return pct + "% chance to stagger enemy for 1 turn.";
            case KINETIC_SLAM:        return pct + "% chance to stun for 1 turn (melee).";
            case CLEAVE:              return "Hits splash " + pct + "% damage to adjacent enemies.";
            case INCENDIARY:          return "Ignites: " + flat + " fire dmg/turn for 3 turns.";
            case LIFESTEAL:           return pct + "% of damage dealt returned as HP.";
            case SCAVENGER_ROUNDS:    return pct + "% on kill: refund " + count + " rounds to reserve.";
            case POINT_BLANK:         return "+" + pct + "% damage at 1-tile range.";
            case OPENING_SALVO:       return "First shot vs fresh enemy: +" + pct + "% damage.";
            case RHYTHM:              return "+" + pct + "% per consec. hit (max " + GameBalance.RHYTHM_MAX_STACKS + " stacks).";
            case STATIC_DISCHARGE:    return "On kill: " + flat + " damage burst to adjacent tiles.";
            case SOULFORGE:           return "Legendary — every 5 kills levels up weapon.";
            case JUDGMENT:            return "Legendary — every 5 fires: 3× crit lance.";
            case HELLFIRE_NOVA:       return "Legendary — crits trigger 2-tile AoE (75% dmg).";
            case BERSERKERS_OATH:     return "Legendary — below 20% HP, every hit crits.";
            default:                  return shortHint;
        }
    }

    /**
     * Returns affect-block lines with exact numbers from the given instance.
     * Call only at open/update time, not inside render() — creates a new array.
     */
    public String[] buildAffectsLines(AbilityInstance instance) {
        int pct   = instance.magnitudePercent();
        int flat  = Math.round(instance.magnitude);
        int count = instance.countValue;
        switch (this) {
            case BURST_FIRE:
                return new String[]{"Rounds per attack: " + count, "Ammo per attack: " + count};
            case CRITICAL_STRIKE:
                return new String[]{"Critical hit chance: " + pct + "%", "Crit damage multiplier: 2× (100% bonus)"};
            case ARMOR_PIERCE:
                return new String[]{"Block bypassed per hit: " + pct + "%", "Extra enemies pierced: " + count};
            case EXECUTIONER:
                return new String[]{"Damage bonus vs low-HP: +" + pct + "%", "Threshold: target below 25% HP"};
            case REND:
                return new String[]{"Bleed damage per turn: " + flat, "Bleed duration: 4 turns"};
            case STAGGER_ROUNDS:
                return new String[]{"Stagger chance per hit: " + pct + "%", "Stagger duration: 1 turn"};
            case KINETIC_SLAM:
                return new String[]{"Stun chance per hit: " + pct + "%", "Stun duration: 1 turn"};
            case CLEAVE:
                return new String[]{"Splash damage fraction: " + pct + "%", "Radius: all adjacent tiles"};
            case INCENDIARY:
                return new String[]{"Burn damage per turn: " + flat, "Burn duration: 3 turns"};
            case LIFESTEAL:
                return new String[]{"HP restored per hit: " + pct + "% of damage dealt", "Works on every hit"};
            case SCAVENGER_ROUNDS:
                return new String[]{"Refund chance on kill: " + pct + "%", "Rounds refunded: " + count};
            case POINT_BLANK:
                return new String[]{"Damage bonus at close range: +" + pct + "%", "Range threshold: 1 tile"};
            case OPENING_SALVO:
                return new String[]{"First-shot damage bonus: +" + pct + "%", "Resets: per encounter"};
            case RHYTHM: {
                int totalRhythmBonus = pct * GameBalance.RHYTHM_MAX_STACKS;
                return new String[]{"Damage ramp per consec. hit: +" + pct + "%",
                                    "Max " + GameBalance.RHYTHM_MAX_STACKS + " stacks (+" + totalRhythmBonus + "% total)"};
            }
            case STATIC_DISCHARGE:
                return new String[]{"Burst damage on kill: " + flat, "Radius: adjacent tiles"};
            case SOULFORGE:
                return new String[]{"Kills to level up weapon: 5", "Attack refresh: once per encounter"};
            case JUDGMENT:
                return new String[]{"Fires between lances: 5", "Lance: 3× damage, 20-tile range"};
            case HELLFIRE_NOVA:
                return new String[]{"Nova on crit: 75% of crit damage", "AoE radius: 2 tiles"};
            case BERSERKERS_OATH:
                return new String[]{"100% crit rate below 20% HP", "Kill stacks: +10% dmg each (max 5)"};
            default:
                return affectsLines;
        }
    }

    /**
     * Creates an AbilityInstance at level-1 values. Used when showing signature abilities for unequipped
     * weapon types (inventory grid) where no real AbilityInstance with a specific weapon level is available.
     * Delegates to the one magnitude table ({@link WeaponRoller#buildAbilityInstance}) so the UI can never
     * drift from what a level-1 roll actually carries.
     */
    public AbilityInstance levelOneInstance() {
        return WeaponRoller.buildAbilityInstance(this, 1);
    }
}
