package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.route.NodeSupplySpec;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BossBalance;
import ge.tbegvadze.toon3d.util.ExpectedPlayer;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.ItemConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * THE SUPPLY PLANNER (balance-overhaul order 2, rules S1-S10) — the one place a generated floor's
 * ammo, heals, armour, credits and weapon drops are decided. Every generator calls it (through
 * {@link FloorPopulator}); none places a pickup by its own rule.
 *
 * <p>Two steps, both pure and seeded:
 * <ol>
 *   <li>{@link #plan(SupplyRequest)} — derives the floor's supply from the roster it actually fields
 *       (DEMAND) and its node's {@link NodeSupplySpec}: ammo = roster eHP x ammoRatio split 70/30 over
 *       carried / other ammo types (S2); heals = the modelled incoming damage x (1 - drainTarget) with
 *       an armour share (S3), never below the heal floor (S4); credits (S8); weapon drops with their
 *       level offsets and the two-floor cadence (S9); and a carrier share handed to enemies (S6).</li>
 *   <li>{@link #place} — puts every ground pickup on a {@link SupplySlot}: the heal floor keycard-free
 *       and half of it early, vault ammo and the ELITE reward behind the anchor group, nothing past the
 *       per-room share (S7), preferring the exit path and the rooms that hold a fight.</li>
 * </ol>
 *
 * <p>Never reads the player's current HP or ammo (AS1). Pure — no LibGDX imports.
 */
public final class SupplyPlanner {

    /** Seed salt for the planning roll (weapon drops, carrier choice), distinct from the layout RNG. */
    private static final long PLAN_SEED_SALT      = 0x5A11E5L;
    /** Seed salt for the placement jitter. */
    private static final long PLACEMENT_SEED_SALT = 0x91ACE5L;
    /** A sliver below one pickup's value still rounds to that pickup (float safety on the heal floor). */
    private static final float ROUNDING_EPSILON   = 1e-4f;

    /** The weapon pool a planned drop draws from (ranged + melee; each equally likely). */
    private static final ItemType[] WEAPON_POOL = {
            ItemType.WEAPON_SHOTGUN, ItemType.WEAPON_DOUBLE_BARREL, ItemType.WEAPON_CHAINGUN,
            ItemType.WEAPON_ASSAULT_RIFLE, ItemType.WEAPON_PLASMA, ItemType.WEAPON_INCINERATOR,
            ItemType.WEAPON_RAILGUN, ItemType.WEAPON_ARC_CANNON, ItemType.WEAPON_ROCKET,
            ItemType.WEAPON_KNIFE, ItemType.WEAPON_HAMMER, ItemType.WEAPON_CHAINSAW
    };

    private SupplyPlanner() {}

    // =====================================================================================
    // PLAN
    // =====================================================================================

    /** Plans one floor's supply from its roster and node spec (S2-S10). Deterministic per request. */
    public static SupplyPlan plan(SupplyRequest request) {
        NodeSupplySpec spec   = request.spec;
        int            depth  = request.depth;
        ExpectedPlayer player = request.expectedPlayer;
        Random         random = new Random(request.seed ^ PLAN_SEED_SALT);

        float rosterEffectiveHitPoints = 0f;
        float rosterBaseDamagePerTurn  = 0f;
        for (EnemyType type : request.roster) {
            rosterEffectiveHitPoints += GameMath.enemyHealthAtDepth(type.maxHealth(), depth);
            rosterBaseDamagePerTurn  += type.attackDamage() / (float) Math.max(1, type.attackCadenceTurns());
        }
        float incomingFraction = GameMath.floorExpectedIncomingDamage(rosterBaseDamagePerTurn, depth, player,
                BalanceConfig.SUPPLY_TURNS_ENGAGED_PER_ENEMY, BalanceConfig.SUPPLY_AVOIDANCE_FACTOR);

        Map<SupplyCategory, Float> planned = new EnumMap<>(SupplyCategory.class);
        List<PlannedPickup> pickups = new ArrayList<>();

        // --- S2 AMMO (BOSS: the R-BOSS-AMMO arena budget instead of a roster demand) -------------
        float ammoDamage = spec.bossArenaAmmo()
                ? BossBalance.arenaAmmoBudgetDamage(request.bossEffectiveHitPoints)
                : rosterEffectiveHitPoints * spec.ammoRatio();
        float vaultDamage = rosterEffectiveHitPoints * spec.vaultAmmoRatio();
        planned.put(SupplyCategory.AMMO, ammoDamage + vaultDamage);
        float mainPlaced = addAmmoPickups(pickups, ammoDamage, request.carriedAmmoTypes, depth, false);
        // The vault rounds against the RUNNING total, so two rounding steps never compound.
        if (vaultDamage > 0f) {
            addAmmoPickups(pickups, vaultDamage + (ammoDamage - mainPlaced), request.carriedAmmoTypes, depth, true);
        }

        // --- S3 / S4 HEALS + ARMOUR ------------------------------------------------------------
        float healFloor   = healFloorFraction(spec);
        float healValue   = plannedHealFraction(spec, incomingFraction, player);
        float armourValue = plannedArmourFraction(spec, incomingFraction, player);
        planned.put(SupplyCategory.HEAL, healValue);
        planned.put(SupplyCategory.ARMOUR, armourValue);
        addHealPickups(pickups, healValue, healFloor);
        addArmourPickups(pickups, armourValue, player, spec.guaranteedVest());

        // --- S8 CREDITS --------------------------------------------------------------------------
        int chips = plannedCreditChips(spec);
        int chipValue = Math.round(averageCreditChipValue());
        planned.put(SupplyCategory.CREDITS, (float) chips * chipValue);
        for (int chipIndex = 0; chipIndex < chips; chipIndex++) {
            pickups.add(PlannedPickup.credits(chipValue));
        }

        // --- S9 WEAPONS + the two-floor cadence ------------------------------------------------
        int weaponsPlanned = addWeaponPickups(pickups, spec, random, request.weaponCadenceDue);
        planned.put(SupplyCategory.WEAPON, (float) weaponsPlanned);

        // --- S6 CARRIERS -------------------------------------------------------------------------
        List<PlannedPickup> withCarriers = assignCarriers(pickups, request.roster.size(), random);
        return new SupplyPlan(depth, rosterEffectiveHitPoints, incomingFraction, healFloor, planned, withCarriers);
    }

    /** S4: the heal floor a spec's floor carries, as a fraction of expected max HP (0 when exempt). */
    public static float healFloorFraction(NodeSupplySpec spec) {
        return spec.healFloorApplies() ? BalanceConfig.SUPPLY_HEAL_FLOOR_FRACTION : 0f;
    }

    /**
     * S3: the ARMOUR value a floor plans (fraction of expected max HP) — the spec's armour share of the
     * planned value; ELITE's promised vest is part of the PLAN, not a bonus on top of it (S5 tracks it).
     * Shared with the route ledger (route/RouteEconomicsModel) so the map is priced on this same plan.
     */
    public static float plannedArmourFraction(NodeSupplySpec spec, float incomingFraction, ExpectedPlayer player) {
        float healFloor  = healFloorFraction(spec);
        float totalValue = GameMath.plannedHealValue(incomingFraction, spec.drainTarget(),
                healFloor, spec.healFloorApplies());
        float armourValue = totalValue * spec.armourShare();
        if (spec.guaranteedVest()) {
            armourValue = Math.max(armourValue, armourPickupValue(BalanceConfig.ARMOUR_VEST_FRACTION, player));
        }
        return armourValue;
    }

    /**
     * S3 / S4: the HEAL value a floor plans (fraction of expected max HP) — the planned value less its
     * armour, never below the heal floor: armour never stands in for the one medkit a floor guarantees.
     */
    public static float plannedHealFraction(NodeSupplySpec spec, float incomingFraction, ExpectedPlayer player) {
        float healFloor  = healFloorFraction(spec);
        float totalValue = GameMath.plannedHealValue(incomingFraction, spec.drainTarget(),
                healFloor, spec.healFloorApplies());
        return Math.max(totalValue - plannedArmourFraction(spec, incomingFraction, player), healFloor);
    }

    /** S8: credit chips a spec's floor plans (spec.creditScale x the per-floor chip count, rounded). */
    public static int plannedCreditChips(NodeSupplySpec spec) {
        return Math.round(spec.creditScale() * BalanceConfig.SUPPLY_CREDIT_CHIPS_PER_FLOOR);
    }

    /** The ammo types the order-1 expected player carries (the reference workhorse eats BULLETS). */
    public static Set<AmmoType> expectedPlayerAmmoTypes() {
        return EnumSet.of(AmmoType.BULLETS);
    }

    /**
     * Depth-1 damage one unit of an ammo type buys — the per-shot damage of the weapon that eats it
     * (the same rows the R-SCARCITY table priced: AR bullets, shotgun shells, plasma cells, grenade
     * rockets, full-charge railgun slugs).
     */
    public static float depthOneDamagePerUnit(AmmoType ammoType) {
        switch (ammoType) {
            case BULLETS: return BalanceConfig.ASSAULT_RIFLE_DAMAGE;
            case SHELLS:  return BalanceConfig.SHOTGUN_DAMAGE;
            case CELLS:   return BalanceConfig.PLASMA_RIFLE_DAMAGE;
            case ROCKETS: return BalanceConfig.GRENADE_SPLASH_DAMAGE;
            case SLUGS:   return BalanceConfig.RAILGUN_DAMAGE_BY_CHARGE[BalanceConfig.RAILGUN_DAMAGE_BY_CHARGE.length - 1];
            default:      return 0f;
        }
    }

    /** Damage one BOX of an ammo type is worth to the on-curve player at a depth. */
    public static float boxDamageAtDepth(AmmoType ammoType, int depth) {
        return ammoType.getAmountPerBox()
                * GameMath.supplyDamagePerUnitAtDepth(depthOneDamagePerUnit(ammoType), depth);
    }

    /** The average credit chip value (the old weighted small / medium / large chip table's mean). */
    public static float averageCreditChipValue() {
        float weightSmall  = BalanceConfig.CREDIT_SPAWN_WEIGHT_SMALL;
        float weightMedium = BalanceConfig.CREDIT_SPAWN_WEIGHT_MEDIUM;
        float weightLarge  = BalanceConfig.CREDIT_SPAWN_WEIGHT_LARGE;
        float weightTotal  = weightSmall + weightMedium + weightLarge;
        if (weightTotal <= 0f) return ItemConstants.CREDIT_SMALL_BASE;
        return (weightSmall * ItemConstants.CREDIT_SMALL_BASE
                + weightMedium * ItemConstants.CREDIT_MEDIUM_BASE
                + weightLarge * ItemConstants.CREDIT_LARGE_BASE) / weightTotal;
    }

    /** HP-equivalent value of an armour pickup, as a fraction of the expected player's max HP. */
    public static float armourPickupValue(float armourFraction, ExpectedPlayer player) {
        if (player.maxHealth <= 0f) return 0f;
        return armourFraction * player.maxArmor / player.maxHealth;
    }

    /**
     * S2: splits {@code damage} over the ammo types — {@link BalanceConfig#SUPPLY_CARRIED_SHARE} across
     * the carried types, {@link BalanceConfig#SUPPLY_OFF_TYPE_SHARE} across the rest — and rounds to
     * whole boxes by largest remainder against the TOTAL, so rounding moves the floor's ammo by less
     * than one box. Returns the damage actually planned in whole boxes.
     */
    private static float addAmmoPickups(List<PlannedPickup> pickups, float damage, Set<AmmoType> carried,
                                       int depth, boolean behindAnchor) {
        if (damage <= 0f) return 0f;
        AmmoType[] types = AmmoType.values();
        int carriedCount = 0;
        for (AmmoType type : types) if (carried.contains(type)) carriedCount++;
        int otherCount = types.length - carriedCount;
        float carriedShare = otherCount == 0 ? 1f : BalanceConfig.SUPPLY_CARRIED_SHARE;
        float otherShare   = carriedCount == 0 ? 1f : BalanceConfig.SUPPLY_OFF_TYPE_SHARE;

        int[]   boxes     = new int[types.length];
        float[] remainder = new float[types.length];
        float   placed    = 0f;
        for (int typeIndex = 0; typeIndex < types.length; typeIndex++) {
            AmmoType type = types[typeIndex];
            float share = carried.contains(type)
                    ? carriedShare / Math.max(1, carriedCount)
                    : otherShare / Math.max(1, otherCount);
            float boxDamage = boxDamageAtDepth(type, depth);
            if (boxDamage <= 0f) continue;
            float exact = damage * share / boxDamage;
            boxes[typeIndex]     = (int) Math.floor(exact);
            remainder[typeIndex] = exact - boxes[typeIndex];
            placed += boxes[typeIndex] * boxDamage;
        }
        // Largest remainder first: add a box while it brings the total CLOSER to the plan. Every pass
        // zeroes one remainder, so this runs at most once per ammo type.
        while (true) {
            int best = -1;
            for (int typeIndex = 0; typeIndex < types.length; typeIndex++) {
                if (remainder[typeIndex] <= 0f) continue;
                if (best < 0 || remainder[typeIndex] > remainder[best]) best = typeIndex;
            }
            if (best < 0) break;
            float boxDamage = boxDamageAtDepth(types[best], depth);
            remainder[best] = 0f;
            if (Math.abs(placed + boxDamage - damage) < Math.abs(placed - damage)) {
                boxes[best]++;
                placed += boxDamage;
            }
        }
        for (int typeIndex = 0; typeIndex < types.length; typeIndex++) {
            AmmoType type = types[typeIndex];
            float boxDamage = boxDamageAtDepth(type, depth);
            for (int box = 0; box < boxes[typeIndex]; box++) {
                pickups.add(PlannedPickup.grid(SupplyCategory.AMMO, type.getPickupTileChar(), boxDamage,
                        false, behindAnchor));
            }
        }
        return placed;
    }

    /** S3 / S4: field medkits 'H' first, the remainder as stims '+'; the first medkit(s) are the floor. */
    private static void addHealPickups(List<PlannedPickup> pickups, float healValue, float healFloor) {
        float medkitValue = BalanceConfig.MEDKIT_FULL_HEAL_FRACTION;
        float stimValue   = BalanceConfig.MEDKIT_STIM_HEAL_FRACTION;
        int medkits = (int) Math.floor((healValue + ROUNDING_EPSILON) / medkitValue);
        float rest  = Math.max(0f, healValue - medkits * medkitValue);
        int stims   = Math.round(rest / stimValue);
        float floorCovered = 0f;
        for (int medkit = 0; medkit < medkits; medkit++) {
            boolean partOfFloor = floorCovered + ROUNDING_EPSILON < healFloor;
            pickups.add(PlannedPickup.grid(SupplyCategory.HEAL, 'H', medkitValue, partOfFloor, false));
            if (partOfFloor) floorCovered += medkitValue;
        }
        for (int stim = 0; stim < stims; stim++) {
            boolean partOfFloor = floorCovered + ROUNDING_EPSILON < healFloor;
            pickups.add(PlannedPickup.grid(SupplyCategory.HEAL, '+', stimValue, partOfFloor, false));
            if (partOfFloor) floorCovered += stimValue;
        }
    }

    /** S3: armour as vests 'A' then shards 'a' (a guaranteed vest first on an ELITE floor). */
    private static void addArmourPickups(List<PlannedPickup> pickups, float armourValue, ExpectedPlayer player,
                                         boolean guaranteedVest) {
        float vestValue  = armourPickupValue(BalanceConfig.ARMOUR_VEST_FRACTION, player);
        float shardValue = armourPickupValue(BalanceConfig.ARMOUR_SHARD_FRACTION, player);
        if (vestValue <= 0f || shardValue <= 0f) return;
        float rest = armourValue;
        int vests = (int) Math.floor((rest + ROUNDING_EPSILON) / vestValue);
        if (guaranteedVest && vests == 0) vests = 1;
        rest = Math.max(0f, rest - vests * vestValue);
        int shards = Math.round(rest / shardValue);
        for (int vest = 0; vest < vests; vest++) {
            pickups.add(PlannedPickup.grid(SupplyCategory.ARMOUR, 'A', vestValue, false, false));
        }
        for (int shard = 0; shard < shards; shard++) {
            pickups.add(PlannedPickup.grid(SupplyCategory.ARMOUR, 'a', shardValue, false, false));
        }
    }

    /**
     * S9: the spec's weapon drops at seeded level offsets, plus the cadence — when the previous
     * non-boss floor offered nothing at level &gt;= its depth, this floor guarantees one at offset 0.
     * Returns the number planned.
     */
    private static int addWeaponPickups(List<PlannedPickup> pickups, NodeSupplySpec spec, Random random,
                                        boolean cadenceDue) {
        List<PlannedPickup> weapons = new ArrayList<>();
        for (int drop = 0; drop < spec.weaponDrops(); drop++) {
            boolean appears = random.nextFloat() < spec.weaponDropChance();
            int offset = spec.weaponLevelOffsetMin()
                    + random.nextInt(spec.weaponLevelOffsetMax() - spec.weaponLevelOffsetMin() + 1);
            ItemType type = WEAPON_POOL[random.nextInt(WEAPON_POOL.length)];
            if (appears) {
                weapons.add(PlannedPickup.weapon(type, offset, spec.weaponTierBonus(), spec.weaponBehindAnchor()));
            }
        }
        if (cadenceDue && !spec.bossArenaAmmo()) {
            boolean onLevel = false;
            for (PlannedPickup weapon : weapons) {
                if (weapon.weaponLevelOffset >= 0) onLevel = true;
            }
            if (!onLevel) {
                if (weapons.isEmpty()) {
                    weapons.add(PlannedPickup.weapon(WEAPON_POOL[random.nextInt(WEAPON_POOL.length)], 0, -1, false));
                } else {
                    weapons.set(0, weapons.get(0).withWeaponLevelOffset(0));
                }
            }
        }
        pickups.addAll(weapons);
        return weapons.size();
    }

    /**
     * S6: hands {@link BalanceConfig#SUPPLY_CARRIER_SHARE} of the eligible pickups (ammo outside the
     * vault, heals outside the heal floor) to enemies, capped by the roster size. Seeded. The count is
     * ROUNDED to the nearest pickup (not floored), so a floor with two or more eligible pickups always
     * has at least one carrier and skipping its fights always costs something.
     */
    private static List<PlannedPickup> assignCarriers(List<PlannedPickup> pickups, int rosterSize, Random random) {
        List<Integer> eligible = new ArrayList<>();
        for (int index = 0; index < pickups.size(); index++) {
            PlannedPickup pickup = pickups.get(index);
            boolean ammo = pickup.category == SupplyCategory.AMMO && !pickup.behindAnchor;
            boolean heal = pickup.category == SupplyCategory.HEAL && !pickup.healFloor;
            if (ammo || heal) eligible.add(index);
        }
        int carriers = Math.min(rosterSize, Math.round(BalanceConfig.SUPPLY_CARRIER_SHARE * eligible.size()));
        Collections.shuffle(eligible, random);
        List<PlannedPickup> result = new ArrayList<>(pickups);
        for (int carrier = 0; carrier < carriers; carrier++) {
            int index = eligible.get(carrier);
            result.set(index, result.get(index).asCarried());
        }
        return result;
    }

    // =====================================================================================
    // PLACE
    // =====================================================================================

    /**
     * Places a plan (S4 / S6 / S7). Ground pickups take a free {@link SupplySlot}; carried pickups
     * take an enemy from {@code carrierSpawnIndices} (in order — the caller passes them shuffled or in
     * whatever order it prefers); a carried pickup with no enemy left falls back to the ground.
     *
     * @param plan                the floor's plan
     * @param slots               every ground-eligible slot (already excluding enemy / weapon tiles)
     * @param carrierSpawnIndices spawn-list indices of the enemies that may carry a drop
     * @param anchorRegionId      the anchor group's room, or a negative id when the floor has none
     * @param anchorWalkDistance  the shortest walk into the anchor group's room (its own tile when the group
     *                            fills the room), or a negative value when unknown — "past the anchor" means
     *                            at least this deep
     * @param halfDistance        the S4 "first half" walk distance
     * @param seed                the floor seed (placement jitter)
     */
    public static SupplyPlacement place(SupplyPlan plan, List<SupplySlot> slots, List<Integer> carrierSpawnIndices,
                                        int anchorRegionId, int anchorWalkDistance, int halfDistance,
                                        long seed) {
        Random random = new Random(seed ^ PLACEMENT_SEED_SALT);
        List<SupplyPlacement.GroundPlacement>   ground   = new ArrayList<>();
        List<SupplyPlacement.CarrierAssignment> carriers = new ArrayList<>();
        List<PlannedPickup>                     unplaced = new ArrayList<>();

        // --- Carriers first: whatever finds no enemy rejoins the ground queue.
        List<PlannedPickup> groundQueue = new ArrayList<>();
        int nextCarrier = 0;
        for (PlannedPickup pickup : plan.pickups()) {
            if (pickup.carried && nextCarrier < carrierSpawnIndices.size()) {
                carriers.add(new SupplyPlacement.CarrierAssignment(carrierSpawnIndices.get(nextCarrier++), pickup));
            } else {
                groundQueue.add(pickup);
            }
        }

        // --- Ground: heal floor first, then the anchor-bound pickups (the reward WEAPON before the vault
        // ammo, so the anchor room's few free tiles go to the promise the route card made — C2), then
        // everything else by category.
        List<PlannedPickup> ordered = new ArrayList<>();
        for (PlannedPickup pickup : groundQueue) if (pickup.healFloor) ordered.add(pickup);
        for (PlannedPickup pickup : groundQueue) {
            if (!pickup.healFloor && pickup.behindAnchor && pickup.category == SupplyCategory.WEAPON) ordered.add(pickup);
        }
        for (PlannedPickup pickup : groundQueue) {
            if (!pickup.healFloor && pickup.behindAnchor && pickup.category != SupplyCategory.WEAPON) ordered.add(pickup);
        }
        for (SupplyCategory category : SupplyCategory.values()) {
            for (PlannedPickup pickup : groundQueue) {
                if (!pickup.healFloor && !pickup.behindAnchor && pickup.category == category) ordered.add(pickup);
            }
        }

        Map<SupplyCategory, Integer> categoryCounts = new EnumMap<>(SupplyCategory.class);
        for (PlannedPickup pickup : ordered) categoryCounts.merge(pickup.category, 1, Integer::sum);
        Map<SupplyCategory, Map<Integer, Integer>> roomCounts = new EnumMap<>(SupplyCategory.class);
        for (SupplyCategory category : SupplyCategory.values()) roomCounts.put(category, new HashMap<>());

        // The anchor's depth comes from the caller: when the anchor group fills its room no free slot
        // carries that room's id, and a slot-derived depth would leave "past the anchor" unmatchable.
        int anchorDistance = anchorWalkDistance >= 0 ? anchorWalkDistance : Integer.MAX_VALUE;
        for (SupplySlot slot : slots) {
            if (slot.regionId == anchorRegionId && anchorRegionId >= 0) {
                anchorDistance = Math.min(anchorDistance, slot.walkDistance);
            }
        }

        boolean[] taken = new boolean[slots.size()];
        float healFloorPlaced = 0f;
        float healFloorEarly  = 0f;
        for (PlannedPickup pickup : ordered) {
            int roomCap = Math.max(1, (int) Math.floor(BalanceConfig.SUPPLY_MAX_ROOM_SHARE
                    * categoryCounts.getOrDefault(pickup.category, 0)));
            Map<Integer, Integer> perRoom = roomCounts.get(pickup.category);
            boolean needEarly = pickup.healFloor
                    && healFloorEarly + ROUNDING_EPSILON < BalanceConfig.SUPPLY_HEAL_EARLY_SHARE
                            * (healFloorPlaced + pickup.value);
            int chosen = -1;
            // Tiers of relaxation: 0 rooms under the cap -> 1 corridor connectors (not rooms, so the S7
            // share is never broken) -> 2 rooms under the cap past the anchor (lets an anchor-bound pickup
            // leave the anchor's room) -> 3 any room under the cap or connector, dropping the "early" and
            // "behind the anchor" preferences -> 4 (last resort) any tile. The heal floor is NEVER placed
            // behind a keycard.
            for (int tier = 0; tier < 5 && chosen < 0; tier++) {
                float bestScore = -Float.MAX_VALUE;
                for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
                    if (taken[slotIndex]) continue;
                    SupplySlot slot = slots.get(slotIndex);
                    if (slot.weaponOnly && !(pickup.behindAnchor && pickup.category == SupplyCategory.WEAPON)) continue;
                    boolean roomFull = !slot.isConnector()
                            && perRoom.getOrDefault(slot.regionId, 0) >= roomCap;
                    if (tier == 0 && (slot.isConnector() || roomFull)) continue;
                    if (tier == 1 && !slot.isConnector()) continue;
                    if ((tier == 2 || tier == 3) && roomFull) continue;
                    if (pickup.healFloor) {
                        if (!slot.reachableWithoutKeycard) continue;
                        if (needEarly && tier < 3 && slot.walkDistance > halfDistance) continue;
                    }
                    if (pickup.behindAnchor && anchorRegionId >= 0 && tier < 3) {
                        // In the anchor's room first; failing that, anywhere at least as deep as it.
                        boolean inAnchor = slot.regionId == anchorRegionId;
                        boolean past     = slot.walkDistance >= anchorDistance;
                        if (tier == 0 && !inAnchor) continue;
                        if (tier >= 1 && !past) continue;
                    }
                    float score = random.nextFloat();
                    if (slot.onExitPath)    score += BalanceConfig.SUPPLY_EXIT_PATH_BONUS;
                    if (slot.groupRegion)   score += BalanceConfig.SUPPLY_GROUP_ROOM_BONUS;
                    // Nothing in or past the anchor was free: the anchor-bound pickup takes the DEEPEST
                    // tile left, so it still sits as far behind the fight as the floor allows.
                    if (pickup.behindAnchor && tier >= 3) score += slot.walkDistance;
                    if (score > bestScore) {
                        bestScore = score;
                        chosen    = slotIndex;
                    }
                }
            }
            if (chosen < 0) {
                unplaced.add(pickup);
                continue;
            }
            taken[chosen] = true;
            SupplySlot slot = slots.get(chosen);
            ground.add(new SupplyPlacement.GroundPlacement(pickup, slot));
            perRoom.merge(slot.regionId, 1, Integer::sum);
            if (pickup.healFloor) {
                healFloorPlaced += pickup.value;
                if (slot.walkDistance <= halfDistance) healFloorEarly += pickup.value;
            }
        }
        return new SupplyPlacement(ground, carriers, unplaced, halfDistance);
    }
}
