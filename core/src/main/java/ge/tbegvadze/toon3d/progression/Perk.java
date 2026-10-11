package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * THE PERK CATALOG (balance-overhaul order 5, rule B2) — the tagged level-up perks that replace the
 * budget-equal upgrade cards. A row is pure data: its {@link BuildTag}, its rank count, its typed effects
 * ({@link PerkEffect} — a {@link PerkModifier} kind with the TOTAL magnitude per rank, every number read
 * from BalanceConfig SECTION 24), an optional attribute grant, and its UI text (held on the enum exactly
 * as {@code UpgradeCard} holds its card text — UI text, not story string ids).
 *
 * <p>Adding a perk is one row plus its SECTION 24 magnitudes; R-PERK (felt per rank on the tag's niche,
 * focused on that niche) is what it must pass. Nothing switches on a perk.
 *
 * <p>Decisions this order's spec left open (recorded in docs/xp-level-progression.txt):
 * <ul>
 *   <li>POINT MAN r1: SPREAD damage at point-blank per rank, so every rank is felt; r2 adds the
 *       knockback tile, r3 the 3-tile stagger.</li>
 *   <li>SHIELD WALL r3: the guard covers every side (r1 widened it to the flanks, r2 made a guarded heavy
 *       harmless) — the guard model's last open lever.</li>
 *   <li>FIRST BLOOD / STEADY AIM / LANE DISCIPLINE: BALLISTIC and RAIL activations only.</li>
 *   <li>ACCELERANT: FIRE damage per rank beside the burn stack (the stack alone is never reached in a
 *       reference fight, which ends before three stacks); ARC JUMP: the chain multiplier rises per rank
 *       beside the jump (a fourth jump at 0.6^4 is not felt).</li>
 *   <li>Attribute grant: BUTCHER +1 STRENGTH per rank (melee only, so it stays inside the BLADE niche);
 *       no other row grants an attribute in this order.</li>
 * </ul>
 * Headless: no LibGDX imports.
 */
public enum Perk {

    // ── CLOSE QUARTERS ──────────────────────────────────────────────────────────────────────────
    BUCKSHOT_DISCIPLINE(BuildTag.CLOSE_QUARTERS, 3, "BUCKSHOT DISCIPLINE",
            "+" + percent(BalanceConfig.PERK_BUCKSHOT_SPREAD_DAMAGE_BY_RANK[0]) + " SPREAD damage per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.SPREAD_DAMAGE, BalanceConfig.PERK_BUCKSHOT_SPREAD_DAMAGE_BY_RANK)),
    POINT_MAN(BuildTag.CLOSE_QUARTERS, 3, "POINT MAN",
            "+" + percent(BalanceConfig.PERK_POINT_MAN_POINT_BLANK_DAMAGE_BY_RANK[0])
                    + " SPREAD damage point-blank per rank; r2 knockback +1 tile; r3 stagger at "
                    + BalanceConfig.PERK_POINT_MAN_STAGGER_REACH_TILES + " tiles",
            null, 0,
            PerkEffect.byRank(PerkModifier.SPREAD_POINT_BLANK_DAMAGE, BalanceConfig.PERK_POINT_MAN_POINT_BLANK_DAMAGE_BY_RANK),
            PerkEffect.fromRank(PerkModifier.KNOCKBACK_BONUS, 2, BalanceConfig.PERK_POINT_MAN_KNOCKBACK_BONUS_TILES),
            PerkEffect.fromRank(PerkModifier.STAGGER_BONUS, 3,
                    BalanceConfig.PERK_POINT_MAN_STAGGER_REACH_TILES - BalanceConfig.SHOTGUN_STAGGER_MAX_TILES)),
    BREACHER(BuildTag.CLOSE_QUARTERS, 3, "BREACHER",
            "SPREAD kills refund a shell: " + percent(BalanceConfig.PERK_BREACHER_REFUND_CHANCE_BY_RANK[0]) + " chance per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.SPREAD_KILL_SHELL_REFUND, BalanceConfig.PERK_BREACHER_REFUND_CHANCE_BY_RANK)),

    // ── MARKSMAN ────────────────────────────────────────────────────────────────────────────────
    STEADY_AIM(BuildTag.MARKSMAN, 3, "STEADY AIM",
            "-" + percent(BalanceConfig.PERK_STEADY_AIM_FALLOFF_REDUCTION_BY_RANK[0]) + " BALLISTIC / RAIL falloff per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.BALLISTIC_RAIL_FALLOFF_REDUCTION, BalanceConfig.PERK_STEADY_AIM_FALLOFF_REDUCTION_BY_RANK)),
    FIRST_BLOOD(BuildTag.MARKSMAN, 3, "FIRST BLOOD",
            "+" + percent(BalanceConfig.PERK_FIRST_BLOOD_DAMAGE_BY_RANK[0]) + " BALLISTIC / RAIL damage to full-HP enemies per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.FULL_HEALTH_TARGET_DAMAGE, BalanceConfig.PERK_FIRST_BLOOD_DAMAGE_BY_RANK)),
    LANE_DISCIPLINE(BuildTag.MARKSMAN, 3, "LANE DISCIPLINE",
            "+1 range, +" + percent(BalanceConfig.PERK_LANE_DISCIPLINE_DAMAGE_BY_RANK[0]) + " damage at "
                    + BalanceConfig.BUILD_LONG_RANGE_MIN_TILES + "+ tiles per rank (BALLISTIC / RAIL)",
            null, 0,
            PerkEffect.byRank(PerkModifier.RANGE_BONUS, BalanceConfig.PERK_LANE_DISCIPLINE_RANGE_BY_RANK),
            PerkEffect.byRank(PerkModifier.LONG_RANGE_DAMAGE, BalanceConfig.PERK_LANE_DISCIPLINE_DAMAGE_BY_RANK)),

    // ── PYRO ────────────────────────────────────────────────────────────────────────────────────
    ACCELERANT(BuildTag.PYRO, 3, "ACCELERANT",
            "+1 max burn stack and +" + percent(BalanceConfig.PERK_ACCELERANT_FIRE_DAMAGE_BY_RANK[0]) + " FIRE damage per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.BURN_MAX_STACK, BalanceConfig.PERK_ACCELERANT_BURN_STACKS_BY_RANK),
            PerkEffect.byRank(PerkModifier.FIRE_DAMAGE, BalanceConfig.PERK_ACCELERANT_FIRE_DAMAGE_BY_RANK)),
    WILDFIRE(BuildTag.PYRO, 3, "WILDFIRE",
            "A burning enemy that dies passes all its burn to its neighbours; your burns last +1 turn per rank",
            null, 0,
            PerkEffect.fromRank(PerkModifier.WILDFIRE, 1, 1f),
            PerkEffect.byRank(PerkModifier.BURN_TURNS, BalanceConfig.PERK_WILDFIRE_BURN_TURNS_BY_RANK)),
    FIREPROOF(BuildTag.PYRO, 1, "FIREPROOF",
            "Immune to fire tiles",
            null, 0,
            PerkEffect.of(PerkModifier.FIRE_TILE_IMMUNE, 1f)),

    // ── DEMOLITION ──────────────────────────────────────────────────────────────────────────────
    SHAPED_CHARGES(BuildTag.DEMOLITION, 3, "SHAPED CHARGES",
            "+" + percent(BalanceConfig.PERK_SHAPED_CHARGES_DAMAGE_BY_RANK[0]) + " EXPLOSIVE damage per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.EXPLOSIVE_DAMAGE, BalanceConfig.PERK_SHAPED_CHARGES_DAMAGE_BY_RANK)),
    BLAST_SHIELD(BuildTag.DEMOLITION, 3, "BLAST SHIELD",
            "Your own explosions hurt you less: -" + percent(BalanceConfig.PERK_BLAST_SHIELD_REDUCTION_BY_RANK[0])
                    + " / -" + percent(BalanceConfig.PERK_BLAST_SHIELD_REDUCTION_BY_RANK[1])
                    + " / -" + percent(BalanceConfig.PERK_BLAST_SHIELD_REDUCTION_BY_RANK[2]),
            null, 0,
            PerkEffect.byRank(PerkModifier.OWN_EXPLOSION_REDUCTION, BalanceConfig.PERK_BLAST_SHIELD_REDUCTION_BY_RANK)),
    SAPPER(BuildTag.DEMOLITION, 3, "SAPPER",
            "+" + percent(BalanceConfig.PERK_SAPPER_BARREL_DAMAGE_BY_RANK[0]) + " barrel damage per rank; barrels on the mini-map",
            null, 0,
            PerkEffect.byRank(PerkModifier.BARREL_DAMAGE, BalanceConfig.PERK_SAPPER_BARREL_DAMAGE_BY_RANK),
            PerkEffect.fromRank(PerkModifier.BARRELS_ON_MINIMAP, 1, 1f)),

    // ── ENERGY ──────────────────────────────────────────────────────────────────────────────────
    CAPACITOR(BuildTag.ENERGY, 3, "CAPACITOR",
            "+" + percent(BalanceConfig.PERK_CAPACITOR_ENERGY_DAMAGE_BY_RANK[0]) + " ENERGY damage per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.ENERGY_DAMAGE, BalanceConfig.PERK_CAPACITOR_ENERGY_DAMAGE_BY_RANK)),
    ARC_JUMP(BuildTag.ENERGY, 3, "ARC JUMP",
            "Arc Cannon: +1 chain jump per rank, and each jump loses less",
            null, 0,
            PerkEffect.byRank(PerkModifier.ARC_CHAIN_JUMP, BalanceConfig.PERK_ARC_JUMP_CHAIN_JUMPS_BY_RANK),
            PerkEffect.byRank(PerkModifier.ARC_CHAIN_MULTIPLIER, BalanceConfig.PERK_ARC_JUMP_CHAIN_MULTIPLIER_BY_RANK)),
    OVERCHARGE(BuildTag.ENERGY, 3, "OVERCHARGE",
            "Plasma: +" + percent(BalanceConfig.PERK_OVERCHARGE_PIERCE_BONUS_BY_RANK[0]) + " per enemy already pierced, per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.PIERCE_PER_ENEMY_DAMAGE, BalanceConfig.PERK_OVERCHARGE_PIERCE_BONUS_BY_RANK)),

    // ── BULWARK ─────────────────────────────────────────────────────────────────────────────────
    SHIELD_WALL(BuildTag.BULWARK, 3, "SHIELD WALL",
            "Guard arc +/-90 (r1); a guarded heavy deals x" + BalanceConfig.PERK_SHIELD_WALL_HEAVY_GUARD_MULTIPLIER
                    + " (r2); the guard covers every side (r3)",
            null, 0,
            PerkEffect.fromRank(PerkModifier.WIDE_GUARD_ARC, 1, 1f),
            PerkEffect.fromRank(PerkModifier.HEAVY_GUARD_MULTIPLIER, 2, BalanceConfig.PERK_SHIELD_WALL_HEAVY_GUARD_MULTIPLIER),
            PerkEffect.fromRank(PerkModifier.ALL_ROUND_GUARD, 3, 1f)),
    RIPOSTE_DRILL(BuildTag.BULWARK, 3, "RIPOSTE DRILL",
            "Riposte: +1 VULNERABLE turn and +" + percent(BalanceConfig.PERK_RIPOSTE_DRILL_DAMAGE_BY_RANK[0]) + " damage per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.RIPOSTE_VULNERABLE_TURNS, BalanceConfig.PERK_RIPOSTE_DRILL_TURNS_BY_RANK),
            PerkEffect.byRank(PerkModifier.RIPOSTE_DAMAGE, BalanceConfig.PERK_RIPOSTE_DRILL_DAMAGE_BY_RANK)),
    PLATE_CARRIER(BuildTag.BULWARK, 3, "PLATE CARRIER",
            "+" + percent(BalanceConfig.PERK_PLATE_CARRIER_ARMOR_BY_RANK[0]) + " max armour per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.MAX_ARMOR_BONUS, BalanceConfig.PERK_PLATE_CARRIER_ARMOR_BY_RANK)),

    // ── BLADE ───────────────────────────────────────────────────────────────────────────────────
    BUTCHER(BuildTag.BLADE, 3, "BUTCHER",
            "+" + percent(BalanceConfig.PERK_BUTCHER_MELEE_DAMAGE_BY_RANK[0]) + " melee damage and +1 STRENGTH per rank",
            Attribute.STRENGTH, 1,
            PerkEffect.byRank(PerkModifier.MELEE_DAMAGE, BalanceConfig.PERK_BUTCHER_MELEE_DAMAGE_BY_RANK)),
    BLOODLETTING(BuildTag.BLADE, 3, "BLOODLETTING",
            "Melee kills heal " + percent(BalanceConfig.PERK_BLOODLETTING_HEAL_BY_RANK[0]) + " max HP per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.MELEE_KILL_HEAL, BalanceConfig.PERK_BLOODLETTING_HEAL_BY_RANK)),

    // ── SCAVENGER ───────────────────────────────────────────────────────────────────────────────
    PACK_RAT(BuildTag.SCAVENGER, 3, "PACK RAT",
            "+" + percent(BalanceConfig.PERK_PACK_RAT_AMMO_BY_RANK[0]) + " ammo from pickups per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.AMMO_PICKUP_BONUS, BalanceConfig.PERK_PACK_RAT_AMMO_BY_RANK)),
    HAGGLER(BuildTag.SCAVENGER, 3, "HAGGLER",
            "-" + percent(BalanceConfig.PERK_HAGGLER_DISCOUNT_BY_RANK[0]) + " shop prices per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.SHOP_DISCOUNT, BalanceConfig.PERK_HAGGLER_DISCOUNT_BY_RANK)),
    SALVAGER(BuildTag.SCAVENGER, 3, "SALVAGER",
            "Carriers drop +1 item: " + percent(BalanceConfig.PERK_SALVAGER_EXTRA_DROP_BY_RANK[0]) + " chance per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.CARRIER_EXTRA_DROP, BalanceConfig.PERK_SALVAGER_EXTRA_DROP_BY_RANK)),

    // ── VITALITY ────────────────────────────────────────────────────────────────────────────────
    HARDY(BuildTag.VITALITY, 3, "HARDY",
            "Max HP +" + percent(BalanceConfig.PERK_HARDY_MAX_HEALTH_BY_RANK[0])
                    + " / +" + percent(BalanceConfig.PERK_HARDY_MAX_HEALTH_BY_RANK[1])
                    + " / +" + percent(BalanceConfig.PERK_HARDY_MAX_HEALTH_BY_RANK[2]),
            null, 0,
            PerkEffect.byRank(PerkModifier.MAX_HEALTH_BONUS, BalanceConfig.PERK_HARDY_MAX_HEALTH_BY_RANK)),
    FIELD_MEDIC(BuildTag.VITALITY, 3, "FIELD MEDIC",
            "+" + percent(BalanceConfig.PERK_FIELD_MEDIC_HEAL_BY_RANK[0]) + " healing per rank",
            null, 0,
            PerkEffect.byRank(PerkModifier.HEAL_BONUS, BalanceConfig.PERK_FIELD_MEDIC_HEAL_BY_RANK)),
    SECOND_WIND(BuildTag.VITALITY, 1, "SECOND WIND",
            "Once per floor, a lethal hit leaves you at 1 HP",
            null, 0,
            PerkEffect.of(PerkModifier.SECOND_WIND, 1f));

    public final BuildTag  tag;
    public final int       maxRank;
    public final String    displayName;
    /** One plain line: what each rank does (UI text on the level-up plate). */
    public final String    effectLine;
    /** Attribute a rank grants (null = none). */
    public final Attribute attributeGrant;
    /** Points of {@link #attributeGrant} each rank grants. */
    public final int       attributeGrantPerRank;
    private final PerkEffect[] effects;

    Perk(BuildTag tag, int maxRank, String displayName, String effectLine,
         Attribute attributeGrant, int attributeGrantPerRank, PerkEffect... effects) {
        this.tag                   = tag;
        this.maxRank               = Math.max(1, Math.min(BalanceConfig.PERK_MAX_RANK, maxRank));
        this.displayName           = displayName;
        this.effectLine            = effectLine;
        this.attributeGrant        = attributeGrant;
        this.attributeGrantPerRank = attributeGrant == null ? 0 : attributeGrantPerRank;
        this.effects               = effects;
    }

    /** Folds ranks 1..rank of this perk into the aggregate (each effect at its TOTAL for that rank). */
    public void applyRanks(BuildStats stats, int rank) {
        int clampedRank = Math.min(maxRank, rank);
        if (clampedRank <= 0) return;
        for (PerkEffect effect : effects) {
            effect.applyAt(stats, clampedRank);
        }
        if (attributeGrant != null) stats.attributeGrants[attributeGrant.ordinal()] += attributeGrantAt(clampedRank);
    }

    /** True when the perk's tag owns a weapon class (the B3 SYNERGY test reads it). */
    public boolean isWeaponTag() {
        return tag.isWeaponTag();
    }

    /** Attribute points this perk grants at a rank. */
    public int attributeGrantAt(int rank) {
        return attributeGrantPerRank * Math.max(0, Math.min(maxRank, rank));
    }

    /** "15%" from 0.15 (UI text). */
    static String percent(float fraction) {
        return Math.round(fraction * 100f) + "%";
    }
}
