package ge.tbegvadze.toon3d.relic;

import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.progression.PerkEffect;
import ge.tbegvadze.toon3d.progression.PerkModifier;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * THE CONTENT FILE: one {@code registry.register(...)} call per relic. Adding a relic = one call plus its
 * BalanceConfig SECTION 26 magnitudes; never a switch. Card text is built from the same constants the effects
 * read, so the text can never drift from the numbers. Boolean kinds carry the keystone idiom {@code of(kind, 1f)}.
 *
 * <p>Ownership: one shared, lazily built registry ({@link #registry()}), the {@code RouteRegistries} idiom
 * (static singleton, populated exactly once, safe to call from World, SimWorld and the audit alike).
 */
public final class RelicCatalog {

    private static final RelicRegistry SHARED = new RelicRegistry();
    private static boolean bootstrapped = false;

    private RelicCatalog() { }

    /** The shared relic registry, populated on first access (exactly once). */
    public static synchronized RelicRegistry registry() {
        if (!bootstrapped) {
            bootstrapped = true;
            bootstrap(SHARED);
        }
        return SHARED;
    }

    /** Registers all 36 relics: 11 COMMON, 12 RARE, 7 LEGENDARY, 6 CURSED. */
    public static void bootstrap(RelicRegistry registry) {
        // ---- COMMON ----
        registry.register(RelicDefinition.builder()
                .id("field_dressing").name("FIELD DRESSING")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ON_FLOOR_ARRIVAL).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.FLOOR_ARRIVAL_HEAL, BalanceConfig.RELIC_FIELD_DRESSING_FLOOR_ARRIVAL_HEAL))
                .effectText("Arriving on a floor heals "+RelicText.percent(BalanceConfig.RELIC_FIELD_DRESSING_FLOOR_ARRIVAL_HEAL)+" max HP")
                .build());
        registry.register(RelicDefinition.builder()
                .id("spare_plates").name("SPARE PLATES")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ON_FLOOR_ARRIVAL).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.FLOOR_ARRIVAL_ARMOR, BalanceConfig.RELIC_SPARE_PLATES_FLOOR_ARRIVAL_ARMOR))
                .effectText("Arriving restores "+RelicText.percent(BalanceConfig.RELIC_SPARE_PLATES_FLOOR_ARRIVAL_ARMOR)+" max armour")
                .build());
        registry.register(RelicDefinition.builder()
                .id("bandolier").name("BANDOLIER")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ON_FLOOR_ARRIVAL).valuation(RelicValuation.SUPPLY)
                .effects(PerkEffect.of(PerkModifier.FLOOR_ARRIVAL_AMMO, BalanceConfig.RELIC_BANDOLIER_FLOOR_ARRIVAL_AMMO))
                .effectText("Arriving refills "+RelicText.percent(BalanceConfig.RELIC_BANDOLIER_FLOOR_ARRIVAL_AMMO)+" of each carried ammo type")
                .build());
        registry.register(RelicDefinition.builder()
                .id("bounty_chip").name("BOUNTY CHIP")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.SUPPLY)
                .effects(PerkEffect.of(PerkModifier.KILL_CREDIT_BONUS, BalanceConfig.RELIC_BOUNTY_CHIP_KILL_CREDIT_BONUS))
                .effectText("Kills pay +"+RelicText.percent(BalanceConfig.RELIC_BOUNTY_CHIP_KILL_CREDIT_BONUS)+" credits")
                .build());
        registry.register(RelicDefinition.builder()
                .id("threshold_stim").name("THRESHOLD STIM")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ON_DOOR_OPEN).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.DOOR_OPEN_HEAL, BalanceConfig.RELIC_THRESHOLD_STIM_DOOR_OPEN_HEAL))
                .effectText("Each door you open heals "+RelicText.percent(BalanceConfig.RELIC_THRESHOLD_STIM_DOOR_OPEN_HEAL)+" max HP (once per door)")
                .build());
        registry.register(RelicDefinition.builder()
                .id("salvage_hook").name("SALVAGE HOOK")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.SUPPLY)
                .effects(PerkEffect.of(PerkModifier.CARRIER_EXTRA_DROP, BalanceConfig.RELIC_SALVAGE_HOOK_CARRIER_EXTRA_DROP))
                .effectText("Carriers drop a second item "+RelicText.percent(BalanceConfig.RELIC_SALVAGE_HOOK_CARRIER_EXTRA_DROP)+" of the time")
                .build());
        registry.register(RelicDefinition.builder()
                .id("medics_glove").name("MEDIC'S GLOVE")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ALWAYS).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.HEAL_BONUS, BalanceConfig.RELIC_MEDICS_GLOVE_HEAL_BONUS))
                .effectText("Medkits and treatments heal +"+RelicText.percent(BalanceConfig.RELIC_MEDICS_GLOVE_HEAL_BONUS))
                .build());
        registry.register(RelicDefinition.builder()
                .id("recoil_spring").name("RECOIL SPRING")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.SUPPLY)
                .effects(PerkEffect.of(PerkModifier.KILL_AMMO_REFUND, BalanceConfig.RELIC_RECOIL_SPRING_KILL_AMMO_REFUND))
                .effectText("Kills refund a round to your gun "+RelicText.percent(BalanceConfig.RELIC_RECOIL_SPRING_KILL_AMMO_REFUND)+" of the time")
                .build());
        registry.register(RelicDefinition.builder()
                .id("dog_tags").name("DOG TAGS")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.SUPPLY)
                .effects(PerkEffect.of(PerkModifier.XP_MULTIPLIER, BalanceConfig.RELIC_DOG_TAGS_XP_MULTIPLIER))
                .effectText("+"+RelicText.percent(BalanceConfig.RELIC_DOG_TAGS_XP_MULTIPLIER)+" experience from kills")
                .build());
        registry.register(RelicDefinition.builder()
                .id("spotters_lens").name("SPOTTER'S LENS")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ALWAYS).valuation(RelicValuation.CARRY)
                .affinity(BuildTag.MARKSMAN)
                .effects(PerkEffect.of(PerkModifier.FULL_HEALTH_TARGET_DAMAGE, BalanceConfig.RELIC_SPOTTERS_LENS_FULL_HEALTH_TARGET_DAMAGE))
                .effectText("+"+RelicText.percent(BalanceConfig.RELIC_SPOTTERS_LENS_FULL_HEALTH_TARGET_DAMAGE)+" BALLISTIC / RAIL damage to unhurt enemies")
                .build());
        registry.register(RelicDefinition.builder()
                .id("asbestos_lining").name("ASBESTOS LINING")
                .rarity(RelicRarity.COMMON).trigger(RelicTrigger.ALWAYS).valuation(RelicValuation.CARRY)
                .affinity(BuildTag.PYRO)
                .effects(PerkEffect.of(PerkModifier.FIRE_TILE_IMMUNE, 1f),
                        PerkEffect.of(PerkModifier.BURN_TURNS, BalanceConfig.RELIC_ASBESTOS_LINING_BURN_TURNS),
                        PerkEffect.of(PerkModifier.FIRE_DAMAGE, BalanceConfig.RELIC_ASBESTOS_LINING_FIRE_DAMAGE))
                .effectText("Fire tiles can't hurt you; burns +"+BalanceConfig.RELIC_ASBESTOS_LINING_BURN_TURNS+" turn; +"+RelicText.percent(BalanceConfig.RELIC_ASBESTOS_LINING_FIRE_DAMAGE)+" FIRE")
                .build());
        // ---- RARE ----
        registry.register(RelicDefinition.builder()
                .id("breaching_charge").name("BREACHING CHARGE")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ON_DOOR_OPEN).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.DOOR_BLAST, BalanceConfig.RELIC_BREACHING_CHARGE_DOOR_BLAST))
                .effectText("Doors you open explode beyond them (enemies only)")
                .build());
        registry.register(RelicDefinition.builder()
                .id("vampiric_core").name("VAMPIRIC CORE")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.KILL_HEAL, BalanceConfig.RELIC_VAMPIRIC_CORE_KILL_HEAL))
                .effectText("Every kill heals "+RelicText.percent(BalanceConfig.RELIC_VAMPIRIC_CORE_KILL_HEAL)+" max HP")
                .build());
        registry.register(RelicDefinition.builder()
                .id("salvage_plating").name("SALVAGE PLATING")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.KILL_ARMOR, BalanceConfig.RELIC_SALVAGE_PLATING_KILL_ARMOR))
                .effectText("Every kill restores "+RelicText.percent(BalanceConfig.RELIC_SALVAGE_PLATING_KILL_ARMOR)+" max armour")
                .build());
        registry.register(RelicDefinition.builder()
                .id("grief_engine").name("GRIEF ENGINE")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.CORPSE_BLAST, BalanceConfig.RELIC_GRIEF_ENGINE_CORPSE_BLAST))
                .effectText("Enemies you kill burst into the enemies beside them")
                .build());
        registry.register(RelicDefinition.builder()
                .id("kevlar_weave").name("KEVLAR WEAVE")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ON_HIT_TAKEN).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.DAMAGE_TAKEN_MULTIPLIER, BalanceConfig.RELIC_KEVLAR_WEAVE_DAMAGE_TAKEN_MULTIPLIER))
                .effectText("Take "+RelicText.percent(1f - BalanceConfig.RELIC_KEVLAR_WEAVE_DAMAGE_TAKEN_MULTIPLIER)+" less damage")
                .build());
        registry.register(RelicDefinition.builder()
                .id("ablative_shell").name("ABLATIVE SHELL")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ON_HIT_TAKEN).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.FIRST_HIT_SHIELD, 1f))
                .effectText("The first hit you take on each floor does nothing")
                .build());
        registry.register(RelicDefinition.builder()
                .id("culling_signal").name("CULLING SIGNAL")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ON_ENEMY_SPAWN).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.ENEMY_HEALTH_MULTIPLIER, BalanceConfig.RELIC_CULLING_SIGNAL_ENEMY_HEALTH_MULTIPLIER))
                .effectText("Enemies (not bosses) arrive with "+RelicText.percent(1f - BalanceConfig.RELIC_CULLING_SIGNAL_ENEMY_HEALTH_MULTIPLIER)+" less health")
                .build());
        registry.register(RelicDefinition.builder()
                .id("choke_bore").name("CHOKE BORE")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ALWAYS).valuation(RelicValuation.CARRY)
                .affinity(BuildTag.CLOSE_QUARTERS)
                .effects(PerkEffect.of(PerkModifier.SPREAD_DAMAGE, BalanceConfig.RELIC_CHOKE_BORE_SPREAD_DAMAGE),
                        PerkEffect.of(PerkModifier.KNOCKBACK_BONUS, BalanceConfig.RELIC_CHOKE_BORE_KNOCKBACK_BONUS))
                .effectText("+"+RelicText.percent(BalanceConfig.RELIC_CHOKE_BORE_SPREAD_DAMAGE)+" SPREAD damage; knockback +"+BalanceConfig.RELIC_CHOKE_BORE_KNOCKBACK_BONUS+" tile")
                .build());
        registry.register(RelicDefinition.builder()
                .id("capacitor_bank").name("CAPACITOR BANK")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ALWAYS).valuation(RelicValuation.CARRY)
                .affinity(BuildTag.ENERGY)
                .effects(PerkEffect.of(PerkModifier.ENERGY_DAMAGE, BalanceConfig.RELIC_CAPACITOR_BANK_ENERGY_DAMAGE),
                        PerkEffect.of(PerkModifier.ARC_CHAIN_JUMP, BalanceConfig.RELIC_CAPACITOR_BANK_ARC_CHAIN_JUMP))
                .effectText("+"+RelicText.percent(BalanceConfig.RELIC_CAPACITOR_BANK_ENERGY_DAMAGE)+" ENERGY damage; arcs jump to +"+BalanceConfig.RELIC_CAPACITOR_BANK_ARC_CHAIN_JUMP+" enemy")
                .build());
        registry.register(RelicDefinition.builder()
                .id("shaped_casings").name("SHAPED CASINGS")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ALWAYS).valuation(RelicValuation.CARRY)
                .affinity(BuildTag.DEMOLITION)
                .effects(PerkEffect.of(PerkModifier.EXPLOSIVE_DAMAGE, BalanceConfig.RELIC_SHAPED_CASINGS_EXPLOSIVE_DAMAGE),
                        PerkEffect.of(PerkModifier.OWN_EXPLOSION_REDUCTION, BalanceConfig.RELIC_SHAPED_CASINGS_OWN_EXPLOSION_REDUCTION))
                .effectText("+"+RelicText.percent(BalanceConfig.RELIC_SHAPED_CASINGS_EXPLOSIVE_DAMAGE)+" EXPLOSIVE damage; your blasts hurt you "+RelicText.percent(BalanceConfig.RELIC_SHAPED_CASINGS_OWN_EXPLOSION_REDUCTION)+" less")
                .build());
        registry.register(RelicDefinition.builder()
                .id("counterweight").name("COUNTERWEIGHT")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ON_HIT_TAKEN).valuation(RelicValuation.CARRY)
                .affinity(BuildTag.BULWARK)
                .effects(PerkEffect.of(PerkModifier.WIDE_GUARD_ARC, 1f),
                        PerkEffect.of(PerkModifier.GUARD_REFLECT, BalanceConfig.RELIC_COUNTERWEIGHT_GUARD_REFLECT))
                .effectText("Guard covers your flanks; reflects "+RelicText.percent(BalanceConfig.RELIC_COUNTERWEIGHT_GUARD_REFLECT)+" it stops")
                .build());
        registry.register(RelicDefinition.builder()
                .id("bone_saw").name("BONE SAW")
                .rarity(RelicRarity.RARE).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.CARRY)
                .affinity(BuildTag.BLADE)
                .effects(PerkEffect.of(PerkModifier.MELEE_DAMAGE, BalanceConfig.RELIC_BONE_SAW_MELEE_DAMAGE),
                        PerkEffect.of(PerkModifier.MELEE_KILL_HEAL, BalanceConfig.RELIC_BONE_SAW_MELEE_KILL_HEAL))
                .effectText("+"+RelicText.percent(BalanceConfig.RELIC_BONE_SAW_MELEE_DAMAGE)+" melee damage; melee kills heal "+RelicText.percent(BalanceConfig.RELIC_BONE_SAW_MELEE_KILL_HEAL)+" max HP")
                .build());
        // ---- LEGENDARY ----
        registry.register(RelicDefinition.builder()
                .id("second_heart").name("SECOND HEART")
                .rarity(RelicRarity.LEGENDARY).trigger(RelicTrigger.ON_LETHAL_HIT).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.SECOND_WIND, 1f),
                        PerkEffect.of(PerkModifier.MAX_HEALTH_BONUS, BalanceConfig.RELIC_SECOND_HEART_MAX_HEALTH_BONUS))
                .effectText("Once per floor a killing blow leaves you at 1 HP; +"+RelicText.percent(BalanceConfig.RELIC_SECOND_HEART_MAX_HEALTH_BONUS)+" max HP")
                .build());
        registry.register(RelicDefinition.builder()
                .id("hunger_loop").name("HUNGER LOOP")
                .rarity(RelicRarity.LEGENDARY).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.KILL_HEAL, BalanceConfig.RELIC_HUNGER_LOOP_KILL_HEAL),
                        PerkEffect.of(PerkModifier.KILL_ARMOR, BalanceConfig.RELIC_HUNGER_LOOP_KILL_ARMOR))
                .effectText("Every kill heals "+RelicText.percent(BalanceConfig.RELIC_HUNGER_LOOP_KILL_HEAL)+" max HP and "+RelicText.percent(BalanceConfig.RELIC_HUNGER_LOOP_KILL_ARMOR)+" max armour")
                .build());
        registry.register(RelicDefinition.builder()
                .id("chain_detonator").name("CHAIN DETONATOR")
                .rarity(RelicRarity.LEGENDARY).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.CORPSE_BLAST, BalanceConfig.RELIC_CHAIN_DETONATOR_CORPSE_BLAST),
                        PerkEffect.of(PerkModifier.DOOR_BLAST, BalanceConfig.RELIC_CHAIN_DETONATOR_DOOR_BLAST))
                .effectText("Kills burst and doors you open explode (enemies only)")
                .build());
        registry.register(RelicDefinition.builder()
                .id("fourth_holster").name("FOURTH HOLSTER")
                .rarity(RelicRarity.LEGENDARY).trigger(RelicTrigger.ALWAYS).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.EXTRA_LOADOUT_SLOT, BalanceConfig.RELIC_FOURTH_HOLSTER_EXTRA_LOADOUT_SLOT),
                        PerkEffect.of(PerkModifier.FLOOR_ARRIVAL_AMMO, BalanceConfig.RELIC_FOURTH_HOLSTER_FLOOR_ARRIVAL_AMMO))
                .effectText("+"+BalanceConfig.RELIC_FOURTH_HOLSTER_EXTRA_LOADOUT_SLOT+" weapon slot; arriving refills "+RelicText.percent(BalanceConfig.RELIC_FOURTH_HOLSTER_FLOOR_ARRIVAL_AMMO)+" of each ammo type")
                .build());
        registry.register(RelicDefinition.builder()
                .id("deadman_trigger").name("DEADMAN TRIGGER")
                .rarity(RelicRarity.LEGENDARY).trigger(RelicTrigger.ON_LOW_HEALTH).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.LOW_HEALTH_DAMAGE, BalanceConfig.RELIC_DEADMAN_TRIGGER_LOW_HEALTH_DAMAGE))
                .effectText("Below "+RelicText.percent(BalanceConfig.BUILD_BERSERK_LOW_HEALTH_FRACTION)+" HP every weapon deals +"+RelicText.percent(BalanceConfig.RELIC_DEADMAN_TRIGGER_LOW_HEALTH_DAMAGE)+" damage")
                .build());
        registry.register(RelicDefinition.builder()
                .id("atrophy_field").name("ATROPHY FIELD")
                .rarity(RelicRarity.LEGENDARY).trigger(RelicTrigger.ON_ENEMY_SPAWN).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.ENEMY_HEALTH_MULTIPLIER, BalanceConfig.RELIC_ATROPHY_FIELD_ENEMY_HEALTH_MULTIPLIER))
                .effectText("Enemies (not bosses) arrive with "+RelicText.percent(1f - BalanceConfig.RELIC_ATROPHY_FIELD_ENEMY_HEALTH_MULTIPLIER)+" less health")
                .build());
        registry.register(RelicDefinition.builder()
                .id("overclocked_servos").name("OVERCLOCKED SERVOS")
                .rarity(RelicRarity.LEGENDARY).trigger(RelicTrigger.ALWAYS).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.FIRST_HIT_CRITICAL, 1f),
                        PerkEffect.of(PerkModifier.RANGE_BONUS, BalanceConfig.RELIC_OVERCLOCKED_SERVOS_RANGE_BONUS))
                .effectText("Your first hit on every enemy is a critical; +"+BalanceConfig.RELIC_OVERCLOCKED_SERVOS_RANGE_BONUS+" range")
                .build());
        // ---- CURSED ----
        registry.register(RelicDefinition.builder()
                .id("blood_pact").name("BLOOD PACT")
                .rarity(RelicRarity.CURSED).trigger(RelicTrigger.ALWAYS).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.ALL_WEAPON_DAMAGE, BalanceConfig.RELIC_BLOOD_PACT_ALL_WEAPON_DAMAGE),
                        PerkEffect.of(PerkModifier.DAMAGE_TAKEN_MULTIPLIER, BalanceConfig.RELIC_BLOOD_PACT_DAMAGE_TAKEN_MULTIPLIER))
                .effectText("+"+RelicText.percent(BalanceConfig.RELIC_BLOOD_PACT_ALL_WEAPON_DAMAGE)+" damage with every weapon")
                .costText("You take "+RelicText.percent(BalanceConfig.RELIC_BLOOD_PACT_DAMAGE_TAKEN_MULTIPLIER - 1f)+" more damage")
                .build());
        registry.register(RelicDefinition.builder()
                .id("fever_core").name("FEVER CORE")
                .rarity(RelicRarity.CURSED).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.KILL_HEAL, BalanceConfig.RELIC_FEVER_CORE_KILL_HEAL),
                        PerkEffect.of(PerkModifier.HEAL_COST, BalanceConfig.RELIC_FEVER_CORE_HEAL_COST))
                .effectText("Every kill heals "+RelicText.percent(BalanceConfig.RELIC_FEVER_CORE_KILL_HEAL)+" max HP")
                .costText("Medkits and treatments heal "+RelicText.percent(1f - BalanceConfig.RELIC_FEVER_CORE_HEAL_COST)+" less")
                .build());
        registry.register(RelicDefinition.builder()
                .id("volatile_rig").name("VOLATILE RIG")
                .rarity(RelicRarity.CURSED).trigger(RelicTrigger.ON_DOOR_OPEN).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.DOOR_BLAST, BalanceConfig.RELIC_VOLATILE_RIG_DOOR_BLAST),
                        PerkEffect.of(PerkModifier.CORPSE_BLAST, BalanceConfig.RELIC_VOLATILE_RIG_CORPSE_BLAST),
                        PerkEffect.of(PerkModifier.MAX_ARMOR_COST, BalanceConfig.RELIC_VOLATILE_RIG_MAX_ARMOR_COST))
                .effectText("Doors and kills explode, hard (enemies only)")
                .costText("Max armour -"+RelicText.percent(1f - BalanceConfig.RELIC_VOLATILE_RIG_MAX_ARMOR_COST))
                .build());
        registry.register(RelicDefinition.builder()
                .id("tithe_engine").name("TITHE ENGINE")
                .rarity(RelicRarity.CURSED).trigger(RelicTrigger.ON_KILL).valuation(RelicValuation.SUPPLY)
                .effects(PerkEffect.of(PerkModifier.KILL_CREDIT_BONUS, BalanceConfig.RELIC_TITHE_ENGINE_KILL_CREDIT_BONUS),
                        PerkEffect.of(PerkModifier.XP_MULTIPLIER, BalanceConfig.RELIC_TITHE_ENGINE_XP_MULTIPLIER),
                        PerkEffect.of(PerkModifier.ENEMY_HEALTH_MULTIPLIER, BalanceConfig.RELIC_TITHE_ENGINE_ENEMY_HEALTH_MULTIPLIER))
                .effectText("Kills pay +"+RelicText.percent(BalanceConfig.RELIC_TITHE_ENGINE_KILL_CREDIT_BONUS)+" credits and +"+RelicText.percent(BalanceConfig.RELIC_TITHE_ENGINE_XP_MULTIPLIER)+" XP")
                .costText("Enemies arrive with "+RelicText.percent(BalanceConfig.RELIC_TITHE_ENGINE_ENEMY_HEALTH_MULTIPLIER - 1f)+" more health")
                .build());
        registry.register(RelicDefinition.builder()
                .id("iron_lung").name("IRON LUNG")
                .rarity(RelicRarity.CURSED).trigger(RelicTrigger.ON_HIT_TAKEN).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.DAMAGE_TAKEN_MULTIPLIER, BalanceConfig.RELIC_IRON_LUNG_DAMAGE_TAKEN_MULTIPLIER),
                        PerkEffect.of(PerkModifier.ALL_WEAPON_DAMAGE_COST, BalanceConfig.RELIC_IRON_LUNG_ALL_WEAPON_DAMAGE_COST))
                .effectText("Take "+RelicText.percent(1f - BalanceConfig.RELIC_IRON_LUNG_DAMAGE_TAKEN_MULTIPLIER)+" less damage")
                .costText("Every weapon deals "+RelicText.percent(1f - BalanceConfig.RELIC_IRON_LUNG_ALL_WEAPON_DAMAGE_COST)+" less damage")
                .build());
        registry.register(RelicDefinition.builder()
                .id("deaths_door").name("DEATH'S DOOR")
                .rarity(RelicRarity.CURSED).trigger(RelicTrigger.ON_LOW_HEALTH).valuation(RelicValuation.CARRY)
                .effects(PerkEffect.of(PerkModifier.LOW_HEALTH_DAMAGE, BalanceConfig.RELIC_DEATHS_DOOR_LOW_HEALTH_DAMAGE),
                        PerkEffect.of(PerkModifier.MAX_HEALTH_COST, BalanceConfig.RELIC_DEATHS_DOOR_MAX_HEALTH_COST))
                .effectText("Below "+RelicText.percent(BalanceConfig.BUILD_BERSERK_LOW_HEALTH_FRACTION)+" HP every weapon deals +"+RelicText.percent(BalanceConfig.RELIC_DEATHS_DOOR_LOW_HEALTH_DAMAGE))
                .costText("Max HP -"+RelicText.percent(1f - BalanceConfig.RELIC_DEATHS_DOOR_MAX_HEALTH_COST))
                .build());
    }
}
