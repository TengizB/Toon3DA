package ge.tbegvadze.toon3d.entity;

public enum WeaponAbility {

    // ── OFFENSIVE ──────────────────────────────────────────────────────────
    BURST_FIRE         (Family.GUN,       Trigger.ON_FIRE,   false, "Burst Fire",          'B'),
    CRITICAL_STRIKE    (Family.UNIVERSAL, Trigger.ON_HIT,    false, "Critical Strike",     'C'),
    ARMOR_PIERCE       (Family.UNIVERSAL, Trigger.PASSIVE,   false, "Armor Pierce",        'A'),
    EXECUTIONER        (Family.UNIVERSAL, Trigger.ON_HIT,    false, "Executioner",         'X'),
    REND               (Family.UNIVERSAL, Trigger.ON_HIT,    false, "Rend",                'R'),
    OVERPENETRATION    (Family.GUN,       Trigger.PASSIVE,   false, "Overpenetration",     'O'),
    STAGGER_ROUNDS     (Family.GUN,       Trigger.ON_HIT,    false, "Stagger Rounds",      'S'),
    KINETIC_SLAM       (Family.MELEE,     Trigger.ON_HIT,    false, "Kinetic Slam",        'K'),
    CLEAVE             (Family.MELEE,     Trigger.ON_HIT,    false, "Cleave",              'V'),
    INCENDIARY         (Family.UNIVERSAL, Trigger.ON_HIT,    false, "Incendiary",          'I'),

    // ── DEFENSIVE / SUSTAIN ────────────────────────────────────────────────
    LIFESTEAL          (Family.UNIVERSAL, Trigger.ON_HIT,    false, "Lifesteal",           'L'),
    HEMORRHAGE_HARVEST (Family.UNIVERSAL, Trigger.ON_KILL,   false, "Hemorrhage Harvest",  'H'),
    VAMPIRIC_CRIT      (Family.UNIVERSAL, Trigger.ON_CRIT,   false, "Vampiric Crit",       'J'),
    ADRENAL_SURGE      (Family.UNIVERSAL, Trigger.ON_KILL,   false, "Adrenal Surge",       'D'),
    BULWARK_ROUNDS     (Family.GUN,       Trigger.ON_RELOAD, false, "Bulwark Rounds",      'W'),
    SECOND_WIND        (Family.UNIVERSAL, Trigger.PASSIVE,   false, "Second Wind",         '2'),

    // ── UTILITY / ECONOMY ─────────────────────────────────────────────────
    SCAVENGER_ROUNDS   (Family.GUN,       Trigger.ON_KILL,   false, "Scavenger Rounds",    'Z'),
    SALVAGE_STRIKE     (Family.MELEE,     Trigger.ON_KILL,   false, "Salvage Strike",      'T'),
    SCHOLARS_EDGE      (Family.MELEE,     Trigger.ON_KILL,   false, "Scholar's Edge",      'E'),
    QUICK_HANDS        (Family.UNIVERSAL, Trigger.PASSIVE,   false, "Quick Hands",         'Q'),
    EXTENDED_MAG       (Family.GUN,       Trigger.PASSIVE,   false, "Extended Mag",        'M'),
    FIELD_MEDIC_ROUNDS (Family.UNIVERSAL, Trigger.ON_KILL,   false, "Field Medic Rounds",  'F'),
    CREDIT_FANG        (Family.UNIVERSAL, Trigger.ON_KILL,   false, "Credit Fang",         'G'),

    // ── HYBRID / SITUATIONAL ──────────────────────────────────────────────
    POINT_BLANK        (Family.GUN,       Trigger.PASSIVE,   false, "Point Blank",         'P'),
    MARKSMANS_PATIENCE (Family.GUN,       Trigger.PASSIVE,   false, "Marksman's Patience", 'N'),
    OPENING_SALVO      (Family.UNIVERSAL, Trigger.PASSIVE,   false, "Opening Salvo",       'U'),
    RHYTHM             (Family.GUN,       Trigger.PASSIVE,   false, "Rhythm",              'Y'),
    STATIC_DISCHARGE   (Family.UNIVERSAL, Trigger.ON_KILL,   false, "Static Discharge",    '~'),
    RESONANT_ROUNDS    (Family.UNIVERSAL, Trigger.ON_HIT,    false, "Resonant Rounds",     '#'),

    // ── LEGENDARY SIGNATURES (legendaryOnly = true) ───────────────────────
    SOULFORGE          (Family.UNIVERSAL, Trigger.ON_KILL,   true,  "Soulforge",           '*'),
    JUDGMENT           (Family.GUN,       Trigger.ON_FIRE,   true,  "Judgment",            '!'),
    HELLFIRE_NOVA      (Family.UNIVERSAL, Trigger.ON_CRIT,   true,  "Hellfire Nova",       '@'),
    BERSERKERS_OATH    (Family.MELEE,     Trigger.PASSIVE,   true,  "Berserker's Oath",    '^');

    public enum Family  { GUN, MELEE, UNIVERSAL }
    public enum Trigger { PASSIVE, ON_HIT, ON_KILL, ON_CRIT, ON_FIRE, ON_RELOAD }

    public final Family  family;
    public final Trigger trigger;
    public final boolean legendaryOnly;
    public final String  displayName;
    public final char    hudGlyph;

    WeaponAbility(Family family, Trigger trigger, boolean legendaryOnly,
                  String displayName, char hudGlyph) {
        this.family        = family;
        this.trigger       = trigger;
        this.legendaryOnly = legendaryOnly;
        this.displayName   = displayName;
        this.hudGlyph      = hudGlyph;
    }

    /**
     * Whether this ability may be rolled onto a given weapon.
     * GUN abilities require !isMelee; MELEE abilities require isMelee;
     * UNIVERSAL match both. LegendaryOnly abilities only roll into the
     * Legendary signature slot (caller enforces this separately).
     */
    public boolean eligibleFor(boolean isMeleeWeapon) {
        if (family == Family.GUN   && isMeleeWeapon)  return false;
        if (family == Family.MELEE && !isMeleeWeapon) return false;
        return true;
    }
}
