package ge.tbegvadze.toon3d.level;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Determinism guard for {@code LevelGenerator}: the same seed must always produce the identical level
 * grid. This test digests the full generated grid across a spread of seeds and depths (so every room
 * type — including the depth-gated STELLAR_OBSERVATORY and the order-8 SALVAGE_BAY — is exercised) into
 * one stable SHA-256 fingerprint.
 *
 * <p>Through order-5 this was a byte-for-byte "pure structural refactor" guard. order-8 is an explicit
 * BEHAVIOUR CHANGE (registry-driven room selection + rule-driven per-level variety), so the digest was
 * re-baselined once, deliberately, from the post-order-8 generator; order-9 registers ONE MORE room
 * (SUPPLY_CACHE, RECIPE B) that competes in the same seeded roulette, shifting the RNG draw sequence, so
 * the digest is re-baselined a second time, deliberately, from the post-order-9 generator. order-10 is a
 * third deliberate re-baseline: the LARGE blueprint is removed (one fewer roulette candidate), placeRooms()
 * now rolls a per-room large-modifier (an extra RNG draw per room), and a minimum-special-rooms backstop
 * upgrades some STANDARD rooms — all of which shift the RNG draw sequence and stamped tiles. It remains a
 * strict determinism contract afterwards: same seed ⇒ same rooms ⇒ same palette ⇒ same grid (permadeath /
 * seed-sharing fairness). If any later change perturbs the RNG draw sequence or a stamped tile, the
 * fingerprint changes and this test fails.
 */
class LevelGeneratorSnapshotTest {

    /**
     * SHA-256 over {@link #fingerprint()}, re-baselined for the PASSAGE-BLOCKING FIX (follow-up to the
     * walkway fix below).
     *
     * <p>Two holes remained. (1) {@code repairUnreachableFloorRegions()} flooded with the real movement rule
     * but only TARGETED stranded tiles among the four plain floor chars, so a pocket made of walkable decals
     * (stains, scorch, observatory plating) walled in by props was never reopened — ~23% of seeds still had
     * an unreachable tile. It now targets every tile the player can stand on. (2) A solid prop or column in
     * a one-tile-wide passage still plugged it whenever a detour existed; {@code FloorPopulator} now ends
     * with {@code clearPluggedPassages()} and {@code GroupBarrelPlacer} refuses such tiles. Neither consumes
     * RNG, so only the affected seeds' tiles change. The prior baseline passed on the pre-fix generator.
     *
     * <p>PRIOR RE-BASELINE (retained for history) — the WALKWAY-BLOCKING CONNECTIVITY FIX.
     *
     * <p>{@code verifyAndRepairConnectivity()} previously audited only room CENTRES, so a solid prop,
     * wall accent, or 'P' column dropped into a walkway during decoration could strand a floor pocket
     * whose centre lay elsewhere, leaving part of the level unreachable. A definitive backstop —
     * {@code repairUnreachableFloorRegions()}, mirroring {@code LinearCorridorGenerator} — now floods
     * from the spawn and carves a blocker-clearing corridor to any stranded walkable floor tile until
     * every floor tile is reachable. It is DETERMINISTIC and consumes no RNG, so the seeded draw
     * sequence is untouched; the only grids that change are the genuinely-stranded seeds, where the
     * carved corridor tiles differ. The new invariant is pinned directly by {@code GeneratorReachabilityTest}.
     *
     * <p>PRIOR RE-BASELINE (retained for history) — the CINDERFORGE COLOSSUS archetype
     * (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt).
     *
     * <p>The new archetype is a BRUISER ANCHOR, so it joins {@code EncounterBudgetPlanner}'s ANCHOR pool
     * (not the fill pool) from its {@code minSpawnDepth} of 2. From depth 2 onward the anchor roulette
     * draws differently, the chosen anchor and the roster's composition change, and the later grid passes
     * that run AFTER enemy placement (atmospheric wall theming, connectivity repair, lock-and-key,
     * stairs) consume a shifted RNG sequence and stamp different tiles. SCOPE OF THE CHANGE WAS VERIFIED,
     * not assumed: a digest over DEPTH 1 alone is BYTE-IDENTICAL to the previous baseline (verified by
     * building the pre-change generator from HEAD and re-digesting depth 1 across all 40 seeds — same
     * hash), because the depth band keeps the Colossus out of the anchor pool entirely on floor 1. Only
     * depths 2+ move, which is exactly the intended blast radius. Determinism itself is unaffected (the
     * digest is stable across separate JVM runs).
     *
     * <p>PRIOR RE-BASELINE (retained for history) — the AURIC SENTINEL archetype
     * (.claude/agents/ideas/elemental-golem-auric-sentinel.txt): the first golem joined the FILL pool at
     * {@code minSpawnDepth} 3, shifting the roster roulette from depth 3 onward while leaving depths 1-2
     * byte-identical.
     *
     * <p>PRIOR RE-BASELINE (retained for history) — the ENCOUNTER DENSITY change
     * (.claude/agents/ideas/encounter-density-and-corpse-semantics.txt).
     *
     * <p>NOTE ON THE PREVIOUS BASELINE: it was already STALE before this change — the committed digest
     * did not match the generator on master, so `./gradlew test` was failing. Generation itself was
     * never broken (the digest is byte-stable across separate JVM runs, verified three times); the
     * constant had simply not been re-based after an earlier behaviour change. This re-baseline fixes
     * that too, so the gate is green and meaningful again.
     *
     * <p>Four deliberate behaviour changes perturb the RNG draw sequence and the stamped tiles:
     * (1) the anchor is bounded by a real budget CEILING instead of a fallback that could eat 76% of a
     * small floor, so low-budget floors now roll a different roster; (2) a REMAINDER PASS converts
     * leftover Threat Points into bodies rather than discarding them; (3) chaff packs are placed as one
     * unit into one room, changing which tiles are claimed and in what order; and (4) ambient corpse
     * 'm' decals are no longer generated (the symbol is the runtime death marker), which removes draws
     * from the prop-weight table and re-normalises the remaining decal weights. Authored set-piece
     * corpses are unchanged.
     *
     * <p>It remains a strict determinism contract afterwards: same seed ⇒ same grid. Do NOT hand-edit;
     * regenerate only via a deliberate, reviewed behaviour change.
     *
     * <p>PRIOR RE-BASELINE (retained for history) — the SET-PIECE VISIBILITY change: the big and
     * depth-gated signature rooms that had become effectively unspawnable under the flat equal-chance weight
     * are made reliably reachable again. Specifically: STELLAR_OBSERVATORY / GORE_NEST / ATMOSPHERIC_PLANT
     * regain a per-level cap of 1 (RoomContext.alreadyPlacedOfThisKind gate); GORE_NEST and STELLAR_OBSERVATORY
     * depth gates drop to 2; STELLAR_OBSERVATORY gets a relaxed aspect window (1.4) and a high dedicated
     * selection weight, while GORE_NEST / ATMOSPHERIC_PLANT / POWER_PLANT / COMMAND_CENTER share an above-
     * baseline set-piece weight; and the large-room modifier chance rises so the oversized footprints these
     * rooms need actually occur. Each alters which blueprint the STEP-A weighted roulette picks and the room
     * sizes rolled, shifting the RNG draw sequence and the stamped tiles. This sits on top of the earlier
     * EQUAL-CHANCE, ATMOSPHERIC_PLANT / GORE_NEST, and order-8/9/10 re-baselines. It remains a strict
     * determinism contract: same seed ⇒ same grid. Do NOT hand-edit; regenerate only via a deliberate,
     * reviewed behaviour change.
     */
    // RE-BASELINE (balance-overhaul order 1, the POWER LADDER): the R8 rebase re-set every archetype's
    // depth-1 HP and damage, which re-prices their Threat Points, so the encounter planner spends the same
    // floor budget on a different roster (and the ammo box sizes the generator stamps changed). A
    // deliberate balance change; the digest was confirmed stable across two separate JVM runs.
    // RE-BASELINE (balance-overhaul order 2, CP2 — FULL FLOORS, FAIR SUPPLY): ROOMS_MST no longer rolls
    // per-room loot, weapon racks or vault / research-lab rewards; every pickup is placed by the shared
    // SupplyPlanner AFTER the layout, gate and stairs are final, from the roster's demand. A deliberate
    // balance change; the new digest was confirmed stable across two separate JVM runs.
    // RE-BASELINE (balance-overhaul order 2, CP4 — GROUPS): the encounter is now a growing body target
    // filled with group templates and placed one group per room AFTER the gate and stairs (first contact
    // in the nearest room, anchor group deepest), which moves every enemy and the RNG stream behind it.
    // Confirmed stable across two separate JVM runs.
    // NOTE (balance-overhaul order 3, CP2/CP7): trait-aware TP ships with MATCHUP_TP_REFERENCE_WEIGHT = 0 (a
    // CP7 DECISION), so the planner's prices are unchanged; a later weight > 0 re-baselines this digest.
    // RE-BASELINE (balance-overhaul order 3, CP3b — R-ROLE fit): the SupplyPlanner prices a unit of ammo at
    // the base damage of the weapon that eats it — Shotgun 44 -> 66 (and the shell box 4 -> 3, so a box stays
    // worth the same share of a floor), Grenade centre 42 -> 36, Railgun full charge 90 -> 75 — so the planned
    // ammo pickups (and the RNG stream behind them) moved. Confirmed stable across two separate JVM runs.
    // RE-BASELINE (balance-overhaul order 3, CP4 — A-1 AMMO GENEROSITY): the planner now weights each ammo
    // type's carried / off-type share by its generosity (bullets 0.8, shells 1.3, slugs 0.9; normalised, so
    // the floor's total is unchanged), which moves which boxes are placed. Confirmed stable across two runs.
    // RE-BASELINE (balance-overhaul order 3, CP7 — planned-heal re-fit): SUPPLY_AVOIDANCE_FACTOR 0.55 -> 0.70
    // shrinks the planned heal on floors above the S4 floor, so the heal pickups (and the RNG stream behind
    // them) moved. The TP weight stays 0 (DECISION), so rosters are unchanged. Stable across two runs.
    // RE-BASELINE (balance-overhaul order 4, CP1 — HEAVIES): the AREA_STRIKE slam is priced as the heavy
    // fraction of the depth-1 on-curve max HP discounted by HEAVY_EXPECTED_AVOIDANCE (was attackDamage x 1.35,
    // full hit), which re-prices the Threat Points of the slam-bearing archetypes (Plague Hulk, Iron Stalker,
    // the elite), so the encounter planner rolls a different roster. A deliberate balance change; the new
    // digest was confirmed stable across two separate JVM runs.
    // RE-BASELINE (balance-overhaul order 4, CP4 — STATUS MAGNITUDES): the acid casters' WEAK debuff is priced
    // at the new WEAK_DAMAGE_PERCENT 25 -> 35 for DEBUFF_WEAK_DURATION_TURNS 2 -> 3, which re-prices the Threat
    // Points of Mire Wraith / Acid Drone, so the encounter planner rolls a different roster. A deliberate
    // balance change; the new digest was confirmed stable across two separate JVM runs.
    // RE-BASELINE (balance-overhaul order 4, CP5 — HEAVY ASSIGNMENTS): the Cinderforge Colossus gains
    // AREA_STRIKE in its move-set, which re-prices its Threat Points through the cycle average, so the
    // encounter planner rolls a different roster. A deliberate balance change.
    // RE-BASELINE (balance-overhaul order 4, CP6 — HAZARD PLACEMENT, H14): on COMBAT / ELITE floors the shared
    // FloorPopulator now stamps 1-2 explosive barrels ('E') within two tiles of the group cluster in at least
    // 40% of the rooms holding a group of three or more (never the start room), so the generated grids gained
    // solid 'E' tiles and the palette pins 'E' to the barrel sprite. A deliberate change; confirmed stable
    // across two separate JVM runs.
    // RE-BASELINE (balance-overhaul order 6, CP4 — THE REGION DIAL, F7): REGION_TP_BUDGET_MULTIPLIER moved to
    // {0.85, 1.00, 1.25, 1.20, 1.30}, so every floor's Threat-Point budget outside region B changed and the
    // encounter planner rolls different rosters. A deliberate balance change.
    // RE-BASELINE (mixed-encounter-groups): the encounter planner fields MIXED groups (chaff in pairs of
    // different archetypes, every group of four or more mixing behaviours, a rare BROOD rolled once per
    // floor), the placer caps any room at three of one archetype, and the barrel placer's eligibility applies
    // its passage guard — so every generated roster, spawn tile and barrel moved. A deliberate change.
    // RE-BASELINE (sump-wretch-acid-death): a new CHAFF archetype, SUMP_WRETCH, joins EncounterBudgetPlanner's
    // SPAWN_POOL (minSpawnDepth 1), so the chaff roulette draws differently from floor 1 and every generated
    // roster, spawn tile and later RNG-consuming pass moved; pricing its death splash into its TP (CP3) moved the
    // planner's budget arithmetic again. A deliberate change.
    // RE-BASELINE (generated-wall-art order 2 — REGION WALL SETS): wall SYMBOLS change by design. The
    // generators no longer dress walls; the LevelFinisher stamps each room's family (P2/P3) and the hall
    // placement (P4-P9) from the floor's region set on its own seeded stream. The walkable mask and every
    // fixed symbol are unchanged for every seed (R1, proven separately over 200 seeds x every generator x
    // depths 1-25 — see .claude/checkpoints/generated-wall-art-order-2.md). Stable across two JVM runs.
    // RE-BASELINE (level-generation-overhaul CP3, 2026-10-23): GroupBarrelPlacer (R26) now refuses tiles in a
    // door clearance zone and prefers flush tiles, so group barrels move on the seeds that had one near a door.
    // No layout draw changed. A deliberate change.
    // RE-BASELINE (level-generation-overhaul CP6, 2026-10-23): ROOMS_MST is rebuilt SPECIAL ROOMS FIRST — the
    // special plan, specials placed before the entrance and regular rooms, socket-to-socket corridors through the
    // CorridorRouter, flanked one-tile doorways, kit-dressed rooms (no per-tile prop roll), the hall pass and the
    // pinned palette. Every grid changes by design. Stable across two JVM runs.
    // RE-BASELINE (level-generation-overhaul CP7, 2026-10-23): GroupBarrelPlacer never takes a terminal's last
    // walkable side (R20), so a group barrel moves on the seeds where one boxed a 'T' in. No layout draw changed.
    private static final String EXPECTED_DIGEST =
            "3434936dbe0d3bc34ddb576b65d335c88655e7f09adb239ac36dfc1d68727fd3";

    @Test
    void generatedGridsAreByteForByteStableAcrossSeedsAndDepths() {
        assertEquals(EXPECTED_DIGEST, digest(fingerprint()),
                "LevelGenerator output changed — same seed must produce the identical grid (determinism)");
    }

    /** Concatenates every generated grid (seeds 1..40 × depths 1,3,5,7) into one string. */
    private static String fingerprint() {
        StringBuilder builder = new StringBuilder();
        int[] depths = { 1, 3, 5, 7 };
        for (long seed = 1; seed <= 40; seed++) {
            for (int depth : depths) {
                Level level = new LevelGenerator(seed).generate(depth);
                builder.append("seed=").append(seed).append(" depth=").append(depth).append('\n');
                for (int tileRow = level.getHeight() - 1; tileRow >= 0; tileRow--) {
                    for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                        builder.append(level.getCell(tileColumn, tileRow));
                    }
                    builder.append('\n');
                }
            }
        }
        return builder.toString();
    }

    private static String digest(String content) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
