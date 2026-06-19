package ge.tbegvadze.toon3d.entity;

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
        Family.UNIVERSAL, Trigger.PASSIVE, false, "Quick Hands", 'Q',
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
}
