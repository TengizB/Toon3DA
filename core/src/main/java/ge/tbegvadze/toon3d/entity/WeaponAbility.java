package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * All rollable weapon abilities. Each constant carries:
 *   family        — restricts to GUN, MELEE, or UNIVERSAL weapons
 *   trigger       — when the effect fires; PASSIVE means always-on
 *   legendaryOnly — reserved for the legendary signature slot
 *   displayName   — title-case label shown in the HUD and ItemWindow ability rows
 *   hudGlyph      — single character for compact HUD displays
 *   shortHint     — one-line summary shown in the ItemWindow ability row (≤50 chars)
 *   fullDescription — 2-4 sentence explanation shown in AbilityWindow
 *   affectsLines  — bullet list for the AFFECTS block in AbilityWindow (2-3 items)
 *
 * PASSIVE/ACTIVE badge shown in UI is derived from trigger:
 *   trigger == PASSIVE → "PASSIVE"; all other triggers → "ACTIVE"
 */
public enum WeaponAbility {

    // ── OFFENSIVE ──────────────────────────────────────────────────────────
    BURST_FIRE(
        Family.GUN, Trigger.ON_FIRE, false, "Burst Fire", 'B',
        "Fires multiple rounds per attack action.",
        "Each trigger pull releases a burst of rounds in rapid succession. Hit and damage are resolved individually per round. High sustained output at the cost of ammo consumption.",
        new String[]{"Rounds per attack", "Ammo per attack"}
    ),
    CRITICAL_STRIKE(
        Family.UNIVERSAL, Trigger.ON_HIT, false, "Critical Strike", 'C',
        "Attacks have a chance to deal bonus damage.",
        "A portion of attacks land on critical weak points, dealing substantially increased damage. Critical hit chance and bonus both scale with weapon level. Pairs well with high-speed weapons.",
        new String[]{"Critical hit chance %", "Crit damage multiplier"}
    ),
    ARMOR_PIERCE(
        Family.UNIVERSAL, Trigger.PASSIVE, false, "Armor Pierce", 'A',
        "Bypasses a portion of enemy armor on every hit.",
        "Each attack ignores a percentage of the target's armor rating before damage is applied. Scales with weapon level. Highly effective against heavily armored or shielded enemies.",
        new String[]{"Armor bypassed per hit %", "Effective damage vs armor"}
    ),
    EXECUTIONER(
        Family.UNIVERSAL, Trigger.ON_HIT, false, "Executioner", 'X',
        "Bonus damage against enemies below 25% HP.",
        "When the target's health drops below 25%, this weapon deals additional damage per hit. The bonus scales with weapon level. Ideal for eliminating stubborn, nearly-dead enemies quickly.",
        new String[]{"Damage bonus vs low-HP targets", "Threshold: below 25% HP"}
    ),
    REND(
        Family.UNIVERSAL, Trigger.ON_HIT, false, "Rend", 'R',
        "Hits apply a bleed DoT for 2 turns.",
        "Successful hits apply a bleeding effect that deals damage over the next two turns. A new hit resets the bleed duration rather than stacking it. Effective for sustained damage on tanky targets.",
        new String[]{"Bleed damage per turn", "Duration: 2 turns"}
    ),
    OVERPENETRATION(
        Family.GUN, Trigger.PASSIVE, false, "Overpenetration", 'O',
        "Projectile pierces all enemies on the same axis.",
        "The projectile does not stop on first contact. It pierces through all enemies aligned on the same cardinal axis as the shot. Every enemy in the line of fire is struck without damage falloff.",
        new String[]{"Max enemies hit per shot", "Line-of-fire targeting"}
    ),
    STAGGER_ROUNDS(
        Family.GUN, Trigger.ON_HIT, false, "Stagger Rounds", 'S',
        "Hits have a chance to slow enemy reactions.",
        "A percentage of hits cause the target to stagger, delaying or interrupting its next action. Stagger chance scales with weapon level. Useful for buying time during difficult encounters.",
        new String[]{"Stagger chance %", "Enemy action delay"}
    ),
    KINETIC_SLAM(
        Family.MELEE, Trigger.ON_HIT, false, "Kinetic Slam", 'K',
        "Heavy blows have a chance to stun for 1 turn.",
        "Melee attacks with sufficient force may stun the target, preventing any action for one turn. Stun chance scales with weapon level. Cannot stun the same enemy on consecutive turns.",
        new String[]{"Stun chance %", "Stun duration: 1 turn"}
    ),
    CLEAVE(
        Family.MELEE, Trigger.ON_HIT, false, "Cleave", 'V',
        "Melee attacks splash damage to adjacent enemies.",
        "A fraction of the primary hit damage spreads to all enemies in adjacent tiles. Splash damage does not trigger on-hit secondary effects. Excellent for clearing groups in corridors or rooms.",
        new String[]{"Splash damage fraction %", "Adjacent tile coverage"}
    ),
    INCENDIARY(
        Family.UNIVERSAL, Trigger.ON_HIT, false, "Incendiary", 'I',
        "Hits apply burning for 2 turns.",
        "Each successful hit ignites the target, dealing fire damage over the next two turns. A new hit refreshes the duration rather than stacking. Highly effective against slow or clustered enemies.",
        new String[]{"Burn damage per turn", "Duration: 2 turns"}
    ),

    // ── DEFENSIVE / SUSTAIN ────────────────────────────────────────────────
    LIFESTEAL(
        Family.UNIVERSAL, Trigger.ON_HIT, false, "Lifesteal", 'L',
        "A portion of damage dealt restores HP.",
        "A percentage of all damage inflicted is returned to the player as health. Scales with weapon level. Sustains extended engagements without burning medkits.",
        new String[]{"HP restored per hit %", "Works on every hit"}
    ),
    HEMORRHAGE_HARVEST(
        Family.UNIVERSAL, Trigger.ON_KILL, false, "Hemorrhage Harvest", 'H',
        "Killing a bleeding enemy restores HP.",
        "When you defeat an enemy suffering from a bleeding effect, you recover health proportional to the enemy's max HP. Rewards applying bleed before landing the killing blow.",
        new String[]{"HP on kill (bleeding target)", "Scales with enemy max HP"}
    ),
    VAMPIRIC_CRIT(
        Family.UNIVERSAL, Trigger.ON_CRIT, false, "Vampiric Crit", 'J',
        "Critical hits restore a small amount of HP.",
        "Every critical hit regenerates a fixed amount of health based on weapon level. The more frequently crits occur, the more passive healing you receive. Pairs naturally with Critical Strike.",
        new String[]{"HP restored per critical hit", "Scales with weapon level"}
    ),
    ADRENAL_SURGE(
        Family.UNIVERSAL, Trigger.ON_KILL, false, "Adrenal Surge", 'D',
        "Kills have a chance to restore HP.",
        "Defeating an enemy triggers a chance to receive an adrenaline surge that restores a small amount of health. Proc chance and heal amount both scale with weapon level.",
        new String[]{"Heal on kill chance %", "HP per trigger"}
    ),
    BULWARK_ROUNDS(
        Family.GUN, Trigger.ON_RELOAD, false, "Bulwark Rounds", 'W',
        "Completing a reload grants temporary armor.",
        "Finishing a full reload cycle grants temporary armor points that absorb incoming damage. Armor amount scales with weapon level. Rewards deliberate reload management under pressure.",
        new String[]{"Armor points on reload", "Lasts until next hit"}
    ),
    SECOND_WIND(
        Family.UNIVERSAL, Trigger.PASSIVE, false, "Second Wind", '2',
        "Below 30% HP, damage output increases.",
        "When your health falls below 30%, this weapon deals additional damage per hit. The bonus persists as long as health remains below the threshold. Rewards aggressive low-HP play.",
        new String[]{"Damage bonus at low HP", "Threshold: below 30% HP"}
    ),

    // ── UTILITY / ECONOMY ─────────────────────────────────────────────────
    SCAVENGER_ROUNDS(
        Family.GUN, Trigger.ON_KILL, false, "Scavenger Rounds", 'Z',
        "Kills have a chance to drop matching ammo.",
        "Each kill rolls a chance to drop ammo matching this weapon's type. Drop chance scales with weapon level. Helps offset the high consumption cost of burst and rapid-fire weapons.",
        new String[]{"Ammo drop chance %", "Ammo type: matches weapon"}
    ),
    SALVAGE_STRIKE(
        Family.MELEE, Trigger.ON_KILL, false, "Salvage Strike", 'T',
        "Melee kills have a chance to drop credits.",
        "Landing the killing blow with a melee weapon has a chance to knock loose a credit drop from the target. Drop chance and credit value both scale with weapon level.",
        new String[]{"Credit drop chance %", "Credits per drop"}
    ),
    SCHOLARS_EDGE(
        Family.MELEE, Trigger.ON_KILL, false, "Scholar's Edge", 'E',
        "Melee kills grant a bonus XP reward.",
        "Each enemy dispatched with a melee weapon provides additional XP beyond the standard kill reward. Bonus scales with weapon level. Accelerates attribute progression in extended runs.",
        new String[]{"Bonus XP per melee kill", "Scales with weapon level"}
    ),
    QUICK_HANDS(
        Family.GUN, Trigger.PASSIVE, false, "Quick Hands", 'Q',
        "Reduces this weapon's reload tick count.",
        "The operator has drilled rapid field reloads, cutting the reload tick count significantly. Does not affect clip size or damage output. Reduces exposed windows between shots.",
        new String[]{"Reload ticks reduced", "Return-to-fire speed"}
    ),
    EXTENDED_MAG(
        Family.GUN, Trigger.PASSIVE, false, "Extended Mag", 'M',
        "Increases clip size by several rounds.",
        "A modified feed mechanism allows more rounds per magazine load. Clip size increases by a fixed count that scales with weapon level. Reduces how often you need to break off to reload.",
        new String[]{"Clip size increase", "Shots before reload"}
    ),
    FIELD_MEDIC_ROUNDS(
        Family.UNIVERSAL, Trigger.ON_KILL, false, "Field Medic Rounds", 'F',
        "Kills have a chance to spawn a medkit.",
        "A small chance on each kill to find a field medkit among the enemy remains. Drop chance scales with weapon level. Provides passive health sustain during long dungeon runs.",
        new String[]{"Medkit drop chance %", "HP restored per medkit"}
    ),
    CREDIT_FANG(
        Family.UNIVERSAL, Trigger.ON_KILL, false, "Credit Fang", 'G',
        "Each kill drops a bonus credit reward.",
        "Every kill with this weapon yields a small credit bonus on top of standard loot drops. Bonus amount scales with weapon level. Efficient in runs focused on purchasing terminal upgrades.",
        new String[]{"Bonus credits per kill", "Scales with weapon level"}
    ),

    // ── HYBRID / SITUATIONAL ──────────────────────────────────────────────
    POINT_BLANK(
        Family.GUN, Trigger.PASSIVE, false, "Point Blank", 'P',
        "Bonus damage when attacking at 1-2 tile range.",
        "When the target is within one or two tiles, this weapon deals significantly increased damage. The bonus falls off at longer ranges. Ideal for corridor ambushes and aggressive close-quarters positioning.",
        new String[]{"Damage bonus at close range", "Range threshold: 1-2 tiles"}
    ),
    MARKSMANS_PATIENCE(
        Family.GUN, Trigger.PASSIVE, false, "Marksman's Patience", 'N',
        "Each tile of distance adds bonus damage.",
        "Damage scales upward with the distance to the target, adding a flat bonus per tile of separation. Rewards careful positioning and luring enemies into open, longer sightlines.",
        new String[]{"Damage per tile of distance", "Long-range scaling"}
    ),
    OPENING_SALVO(
        Family.UNIVERSAL, Trigger.PASSIVE, false, "Opening Salvo", 'U',
        "First shot of a new encounter deals bonus damage.",
        "The first attack fired upon a fresh enemy encounter deals increased damage. The bonus resets when combat ends. Rewards initiating with a decisive, high-value opening strike.",
        new String[]{"First-shot damage bonus", "Resets: per encounter"}
    ),
    RHYTHM(
        Family.GUN, Trigger.PASSIVE, false, "Rhythm", 'Y',
        "Consecutive hits on the same target ramp damage.",
        "Each successive hit on the same enemy increases damage by a stacking bonus. The ramp resets on a miss or when switching targets. Rewards sustained focus-fire on a single enemy.",
        new String[]{"Damage ramp per hit", "Resets on miss or switch"}
    ),
    STATIC_DISCHARGE(
        Family.UNIVERSAL, Trigger.ON_KILL, false, "Static Discharge", '~',
        "Killing an enemy releases a small damage burst.",
        "Defeating an enemy releases a kinetic burst that deals minor damage to all enemies in adjacent tiles. Does not harm the player. Effective for finishing fights in clustered enemy packs.",
        new String[]{"Burst damage on kill", "Radius: adjacent tiles"}
    ),
    RESONANT_ROUNDS(
        Family.UNIVERSAL, Trigger.ON_HIT, false, "Resonant Rounds", '#',
        "Amplifies damage against bleeding targets.",
        "Each hit against an enemy already suffering from bleed is amplified by a percentage. Rewards applying bleed first, then switching to a high-damage weapon. Multiplicative with bleed damage.",
        new String[]{"Damage amplification %", "Requires: target is bleeding"}
    ),

    // ── LEGENDARY SIGNATURES (legendaryOnly = true) ───────────────────────
    SOULFORGE(
        Family.UNIVERSAL, Trigger.ON_KILL, true, "Soulforge", '*',
        "Legendary — kills restore HP and refresh attack.",
        "Each kill regenerates a portion of health and resets the weapon's cooldown once per encounter. Forged from UAC experimental data. Sustains long combat chains without burning resources.",
        new String[]{"HP on kill", "Attack refresh: once per encounter"}
    ),
    JUDGMENT(
        Family.GUN, Trigger.ON_FIRE, true, "Judgment", '!',
        "Legendary — first shot of combat always crits.",
        "The opening shot of every new engagement is guaranteed to land as a critical hit. Resets when a new encounter begins. Devastating when combined with high base-damage weapons.",
        new String[]{"Guaranteed critical: first shot", "Resets: per encounter"}
    ),
    HELLFIRE_NOVA(
        Family.UNIVERSAL, Trigger.ON_CRIT, true, "Hellfire Nova", '@',
        "Legendary — crits trigger a 1-tile AoE burst.",
        "Landing a critical hit releases a nova burst striking all enemies within one tile of the target. Nova damage scales with weapon level. Paired with high crit-chance builds, this clears groups rapidly.",
        new String[]{"Nova burst on critical", "AoE radius: 1 tile"}
    ),
    BERSERKERS_OATH(
        Family.MELEE, Trigger.PASSIVE, true, "Berserker's Oath", '^',
        "Legendary — below 20% HP, every hit crits.",
        "While your health remains below 20%, every melee attack is a guaranteed critical hit. A legendary pact forged from desperation. Punishes careless play; rewards the utterly fearless.",
        new String[]{"100% crit rate at low HP", "Threshold: below 20% HP"}
    );

    public enum Family  { GUN, MELEE, UNIVERSAL }
    public enum Trigger { PASSIVE, ON_HIT, ON_KILL, ON_CRIT, ON_FIRE, ON_RELOAD }

    public final Family   family;
    public final Trigger  trigger;
    public final boolean  legendaryOnly;
    public final String   displayName;
    public final char     hudGlyph;
    public final String   shortHint;
    public final String   fullDescription;
    public final String[] affectsLines;

    WeaponAbility(Family family, Trigger trigger, boolean legendaryOnly,
                  String displayName, char hudGlyph,
                  String shortHint, String fullDescription, String[] affectsLines) {
        this.family          = family;
        this.trigger         = trigger;
        this.legendaryOnly   = legendaryOnly;
        this.displayName     = displayName;
        this.hudGlyph        = hudGlyph;
        this.shortHint       = shortHint;
        this.fullDescription = fullDescription;
        this.affectsLines    = affectsLines;
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
                     + "Crit chance scales with weapon level up to 30%. Pairs well with high-speed weapons.";
            case ARMOR_PIERCE:
                return "Every attack ignores " + pct + "% of the target's armor rating before damage is applied. "
                     + "Scales up to 60% at max weapon level. Highly effective against heavily armored enemies.";
            case EXECUTIONER:
                return "Deals +" + pct + "% bonus damage against targets below 25% HP. "
                     + "Bonus scales with weapon level up to +80%. Ideal for finishing stubborn, nearly-dead enemies.";
            case REND:
                return "Hits apply bleeding: " + flat + " damage per turn for 4 turns. "
                     + "A new hit refreshes the duration rather than stacking. Effective against tanky targets.";
            case OVERPENETRATION:
                return "Shots pierce up to " + count + (count == 1 ? " enemy" : " enemies") + " in a line without stopping. "
                     + "No damage falloff between targets. Every enemy on the cardinal axis is struck.";
            case STAGGER_ROUNDS:
                return pct + "% of hits delay or interrupt the target's next action for 1 turn. "
                     + "Stagger chance scales with weapon level up to 35%. Useful for buying time in difficult encounters.";
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
                     + "Scales with weapon level up to 20%. Sustains extended engagements without burning medkits.";
            case HEMORRHAGE_HARVEST:
                return "Killing a bleeding enemy restores " + flat + " HP. "
                     + "Scales with weapon level up to 12 HP. Rewards applying bleed before landing the killing blow.";
            case VAMPIRIC_CRIT:
                return "Every critical hit restores " + flat + " HP. "
                     + "Scales with weapon level up to 13 HP. Pairs naturally with Critical Strike for reliable healing.";
            case ADRENAL_SURGE:
                return pct + "% chance on kill: your next attack deals +30% damage. "
                     + "Proc chance scales with weapon level up to 40%. Rewards aggressive, kill-focused play.";
            case BULWARK_ROUNDS:
                return "Completing a reload grants " + flat + " temporary armor that absorbs the next hit. "
                     + "Scales with weapon level up to 8 armor. Rewards deliberate reload management under pressure.";
            case SECOND_WIND:
                return "Below 30% HP, deal +" + pct + "% bonus damage. "
                     + "Bonus scales with weapon level up to +75%. Persists as long as health stays below the threshold.";
            case SCAVENGER_ROUNDS:
                return pct + "% chance on kill to drop 1 ammo matching this weapon's type (2 ammo at Lv.7+). "
                     + "Drop chance scales with weapon level up to 50%. Offsets the ammo cost of burst-fire weapons.";
            case SALVAGE_STRIKE:
                return pct + "% chance on melee kill to drop a credit reward. "
                     + "Scales to 100% at max weapon level. Efficient in runs focused on purchasing terminal upgrades.";
            case SCHOLARS_EDGE:
                return "Each melee kill grants +" + pct + "% bonus XP beyond the standard kill reward. "
                     + "Scales with weapon level up to +75%. Accelerates attribute progression in extended runs.";
            case QUICK_HANDS:
                return "Reduces this weapon's reload tick count by 1 tick. "
                     + "Does not affect clip size or damage output. Reduces the time you are exposed between shots.";
            case EXTENDED_MAG:
                return "Increases clip size by +" + count + " rounds per magazine load. "
                     + "Scales with weapon level up to +4 rounds. Reduces how often you need to reload mid-fight.";
            case FIELD_MEDIC_ROUNDS:
                return pct + "% chance on kill to spawn a medkit at the enemy's location. "
                     + "Drop chance scales with weapon level up to 25%. Provides passive HP sustain during long dungeon runs.";
            case CREDIT_FANG:
                return "Every kill yields +" + flat + " bonus credits on top of standard loot. "
                     + "Scales with weapon level up to +12 credits per kill. Efficient for credit-focused runs.";
            case POINT_BLANK:
                return "+" + pct + "% bonus damage when the target is within 1 tile. "
                     + "Scales with weapon level up to +70%. Ideal for corridor ambushes and aggressive close-quarters positioning.";
            case MARKSMANS_PATIENCE:
                return "+" + pct + "% bonus damage per tile of distance beyond 2 tiles (max +60% total). "
                     + "Per-tile bonus scales with weapon level up to +12%. Rewards luring enemies into long sightlines.";
            case OPENING_SALVO:
                return "First attack on a fresh enemy deals +" + pct + "% bonus damage. "
                     + "Bonus scales with weapon level up to +90%. Resets when combat ends or a new encounter begins.";
            case RHYTHM: {
                int totalRhythmBonus = pct * 5;
                return "+" + pct + "% bonus damage per consecutive hit on the same target (max 5 stacks = +" + totalRhythmBonus + "% total). "
                     + "Resets on a miss or when switching targets. Rewards sustained focus-fire on a single enemy.";
            }
            case STATIC_DISCHARGE:
                return "On kill: releases a burst dealing " + flat + " damage to all enemies in adjacent tiles. "
                     + "Scales with weapon level up to 14 damage. Effective for finishing fights in clustered enemy packs.";
            case RESONANT_ROUNDS:
                return "Hits against bleeding targets deal +" + pct + "% of the target's max HP as bonus damage. "
                     + "Scales with weapon level up to +10%. Pair with Rend or Incendiary for maximum effect.";
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
            case ARMOR_PIERCE:        return "Ignores " + pct + "% armor on every hit.";
            case EXECUTIONER:         return "+" + pct + "% damage vs enemies below 25% HP.";
            case REND:                return "Applies bleed: " + flat + " dmg/turn for 4 turns.";
            case OVERPENETRATION:     return "Pierces up to " + count + (count == 1 ? " enemy" : " enemies") + " in line.";
            case STAGGER_ROUNDS:      return pct + "% chance to stagger enemy for 1 turn.";
            case KINETIC_SLAM:        return pct + "% chance to stun for 1 turn (melee).";
            case CLEAVE:              return "Hits splash " + pct + "% damage to adjacent enemies.";
            case INCENDIARY:          return "Ignites: " + flat + " fire dmg/turn for 3 turns.";
            case LIFESTEAL:           return pct + "% of damage dealt returned as HP.";
            case HEMORRHAGE_HARVEST:  return "Kill a bleeding enemy: restore " + flat + " HP.";
            case VAMPIRIC_CRIT:       return "Critical hits restore " + flat + " HP.";
            case ADRENAL_SURGE:       return pct + "% on kill: next attack +30% damage.";
            case BULWARK_ROUNDS:      return "Reload grants " + flat + " temporary armor.";
            case SECOND_WIND:         return "Below 30% HP: +" + pct + "% damage output.";
            case SCAVENGER_ROUNDS:    return pct + "% chance on kill to drop matching ammo.";
            case SALVAGE_STRIKE:      return pct + "% chance on melee kill to drop credits.";
            case SCHOLARS_EDGE:       return "Melee kills grant +" + pct + "% bonus XP.";
            case QUICK_HANDS:         return "Reduces reload tick count by 1 tick.";
            case EXTENDED_MAG:        return "Clip size increased by +" + count + " rounds.";
            case FIELD_MEDIC_ROUNDS:  return pct + "% chance on kill to spawn a medkit.";
            case CREDIT_FANG:         return "Each kill drops +" + flat + " bonus credits.";
            case POINT_BLANK:         return "+" + pct + "% damage at 1-tile range.";
            case MARKSMANS_PATIENCE:  return "+" + pct + "% bonus damage per tile of distance.";
            case OPENING_SALVO:       return "First shot vs fresh enemy: +" + pct + "% damage.";
            case RHYTHM:              return "+" + pct + "% per consec. hit (max 5 stacks).";
            case STATIC_DISCHARGE:    return "On kill: " + flat + " damage burst to adjacent tiles.";
            case RESONANT_ROUNDS:     return "Hits vs bleeding: +" + pct + "% of target max HP.";
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
                return new String[]{"Armor bypassed per hit: " + pct + "%", "Effective vs armored enemies"};
            case EXECUTIONER:
                return new String[]{"Damage bonus vs low-HP: +" + pct + "%", "Threshold: target below 25% HP"};
            case REND:
                return new String[]{"Bleed damage per turn: " + flat, "Bleed duration: 4 turns"};
            case OVERPENETRATION:
                return new String[]{"Max enemies per shot: " + count, "Line-of-fire targeting"};
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
            case HEMORRHAGE_HARVEST:
                return new String[]{"HP on kill (bleeding target): " + flat, "Requires: target is bleeding"};
            case VAMPIRIC_CRIT:
                return new String[]{"HP restored per critical hit: " + flat, "Scales with weapon level"};
            case ADRENAL_SURGE:
                return new String[]{"Proc chance on kill: " + pct + "%", "Damage bonus on trigger: +30%"};
            case BULWARK_ROUNDS:
                return new String[]{"Temporary armor on reload: " + flat, "Duration: until next hit"};
            case SECOND_WIND:
                return new String[]{"Damage bonus at low HP: +" + pct + "%", "Threshold: below 30% HP"};
            case SCAVENGER_ROUNDS:
                return new String[]{"Ammo drop chance: " + pct + "%", "Ammo type: matches weapon"};
            case SALVAGE_STRIKE:
                return new String[]{"Credit drop chance: " + pct + "%", "Melee kills only"};
            case SCHOLARS_EDGE:
                return new String[]{"Bonus XP per melee kill: +" + pct + "%", "Scales with weapon level"};
            case QUICK_HANDS:
                return new String[]{"Reload ticks reduced: 1", "Return-to-fire speed improved"};
            case EXTENDED_MAG:
                return new String[]{"Clip size increase: +" + count + " rounds", "Shots before reload"};
            case FIELD_MEDIC_ROUNDS:
                return new String[]{"Medkit drop chance: " + pct + "%", "Provides passive HP sustain"};
            case CREDIT_FANG:
                return new String[]{"Bonus credits per kill: +" + flat, "Scales with weapon level"};
            case POINT_BLANK:
                return new String[]{"Damage bonus at close range: +" + pct + "%", "Range threshold: 1 tile"};
            case MARKSMANS_PATIENCE:
                return new String[]{"Bonus per tile of distance: +" + pct + "%", "Total bonus cap: +60%"};
            case OPENING_SALVO:
                return new String[]{"First-shot damage bonus: +" + pct + "%", "Resets: per encounter"};
            case RHYTHM: {
                int totalRhythmBonus = pct * 5;
                return new String[]{"Damage ramp per consec. hit: +" + pct + "%",
                                    "Max 5 stacks (+" + totalRhythmBonus + "% total)"};
            }
            case STATIC_DISCHARGE:
                return new String[]{"Burst damage on kill: " + flat, "Radius: adjacent tiles"};
            case RESONANT_ROUNDS:
                return new String[]{"Bonus vs bleeding: +" + pct + "% of target max HP", "Requires: target is bleeding"};
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
     * Creates an AbilityInstance pre-loaded with level-1 base values from GameBalance.
     * Used when showing signature abilities for unequipped weapon types (inventory grid)
     * where no real AbilityInstance with a specific weapon level is available.
     */
    public AbilityInstance levelOneInstance() {
        switch (this) {
            case BURST_FIRE:          return new AbilityInstance(this, 2f, 2);
            case CRITICAL_STRIKE:     return new AbilityInstance(this, GameBalance.CRIT_CHANCE_BASE, 0);
            case ARMOR_PIERCE:        return new AbilityInstance(this, GameBalance.ARMOR_PIERCE_BASE, 0);
            case EXECUTIONER:         return new AbilityInstance(this, GameBalance.EXECUTIONER_BONUS_BASE, 0);
            case REND:                return new AbilityInstance(this, GameBalance.REND_DAMAGE_PER_TURN_BASE, 0);
            case OVERPENETRATION:     return new AbilityInstance(this,
                                          (float) GameBalance.OVERPENETRATION_BASE_COUNT,
                                          GameBalance.OVERPENETRATION_BASE_COUNT);
            case STAGGER_ROUNDS:      return new AbilityInstance(this, GameBalance.STAGGER_CHANCE_BASE, 0);
            case KINETIC_SLAM:        return new AbilityInstance(this, GameBalance.KINETIC_SLAM_CHANCE_BASE, 0);
            case CLEAVE:              return new AbilityInstance(this, GameBalance.CLEAVE_FRACTION_BASE, 0);
            case INCENDIARY:          return new AbilityInstance(this, GameBalance.INCENDIARY_BURN_PER_TURN_BASE, 0);
            case LIFESTEAL:           return new AbilityInstance(this, GameBalance.LIFESTEAL_BASE, 0);
            case HEMORRHAGE_HARVEST:  return new AbilityInstance(this, GameBalance.HEMORRHAGE_HP_BASE, 0);
            case VAMPIRIC_CRIT:       return new AbilityInstance(this, GameBalance.VAMPIRIC_CRIT_HP_BASE, 0);
            case ADRENAL_SURGE:       return new AbilityInstance(this, GameBalance.ADRENAL_SURGE_CHANCE_BASE, 0);
            case BULWARK_ROUNDS:      return new AbilityInstance(this, GameBalance.BULWARK_ARMOR_BASE, 0);
            case SECOND_WIND:         return new AbilityInstance(this, GameBalance.SECOND_WIND_BONUS_BASE, 0);
            case SCAVENGER_ROUNDS:    return new AbilityInstance(this, GameBalance.SCAVENGER_CHANCE_BASE, 0);
            case SALVAGE_STRIKE:      return new AbilityInstance(this, GameBalance.SALVAGE_CHANCE_BASE, 0);
            case SCHOLARS_EDGE:       return new AbilityInstance(this, GameBalance.SCHOLARS_XP_BONUS_BASE, 0);
            case QUICK_HANDS:         return new AbilityInstance(this, 1f, 0);
            case EXTENDED_MAG:        return new AbilityInstance(this,
                                          (float) GameBalance.EXTENDED_MAG_BASE_COUNT,
                                          GameBalance.EXTENDED_MAG_BASE_COUNT);
            case FIELD_MEDIC_ROUNDS:  return new AbilityInstance(this, GameBalance.FIELD_MEDIC_CHANCE_BASE, 0);
            case CREDIT_FANG:         return new AbilityInstance(this, GameBalance.CREDIT_FANG_BASE, 0);
            case POINT_BLANK:         return new AbilityInstance(this, GameBalance.POINT_BLANK_BONUS_BASE, 0);
            case MARKSMANS_PATIENCE:  return new AbilityInstance(this, GameBalance.MARKSMAN_PER_TILE_BASE, 0);
            case OPENING_SALVO:       return new AbilityInstance(this, GameBalance.OPENING_SALVO_BONUS_BASE, 0);
            case RHYTHM:              return new AbilityInstance(this, GameBalance.RHYTHM_RAMP_PER_HIT_BASE, 0);
            case STATIC_DISCHARGE:    return new AbilityInstance(this, GameBalance.STATIC_SPLASH_BASE, 0);
            case RESONANT_ROUNDS:     return new AbilityInstance(this, GameBalance.RESONANT_PCT_BASE, 0);
            default:                  return new AbilityInstance(this, 1f, 0);
        }
    }
}
