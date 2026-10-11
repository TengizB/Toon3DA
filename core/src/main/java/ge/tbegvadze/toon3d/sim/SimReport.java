package ge.tbegvadze.toon3d.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BalanceSchema.RuleResult;

/**
 * The human-readable half of the simulation gate (new-game-balancr order 9): plain-text tables of
 * what the played matrix actually did, written to {@code build/reports/balance-sim/}.
 *
 * <p>Same division of labour as {@code BalanceReport}: this prints, the test GATES. A summary here is
 * what a designer reads after moving a dial — "the median death depth moved from 9 to 6, and the
 * emergency lifeline now fires on a fifth of floors" is a sentence you can act on; a red assertion is
 * only a sentence you can obey.
 */
public final class SimReport {

    private SimReport() {}

    /** Where {@code ./gradlew balanceSim} leaves its output. */
    public static final String REPORT_DIRECTORY = "build/reports/balance-sim";
    public static final String SUMMARY_FILE     = "summary.txt";

    /** Renders the policy table + the band table into one text report. */
    public static String render(Map<String, PolicySummary> matrix, List<RuleResult> bandResults) {
        StringBuilder report = new StringBuilder();
        report.append("================================================================================\n");
        report.append("BALANCE SIMULATION SUMMARY (new-game-balancr order 9)\n");
        report.append("================================================================================\n");
        report.append("Seeds per policy : ").append(BalanceConfig.SIM_SEED_COUNT)
              .append(" (build-bias variants: ").append(BalanceConfig.SIM_BUILD_SEED_COUNT).append(")\n");
        report.append("Depth ceiling    : ").append(BalanceConfig.SIM_DEPTH_CEILING).append('\n');
        report.append("Turn cap / floor : ").append(BalanceConfig.SIM_TURNS_PER_FLOOR_CAP).append('\n');
        report.append('\n');

        report.append("POLICIES\n");
        report.append("--------------------------------------------------------------------------------\n");
        report.append(String.format("%-22s %5s %7s %7s %8s %9s %9s %8s%n",
                "policy", "runs", "medDep", "stalled", "readable", "bossClear", "softlock", "lvlGap"));
        for (PolicySummary summary : matrix.values()) {
            report.append(String.format("%-22s %5d %7.1f %7d %8.2f %9.2f %9.2f %8.2f%n",
                    summary.policyId,
                    summary.runCount(),
                    summary.medianEndingDepth(),
                    summary.stalledRunCount(),
                    summary.readableDeathFraction(),
                    summary.clearedFirstBossFraction(),
                    summary.softlockFraction(),
                    summary.meanLevelVersusExpected()));
        }
        report.append('\n');

        report.append("ECONOMY / PACING (means across every played floor)\n");
        report.append("--------------------------------------------------------------------------------\n");
        report.append(String.format("%-22s %10s %10s %12s%n",
                "policy", "supplyTook", "emergency", "bossTurns"));
        for (PolicySummary summary : matrix.values()) {
            report.append(String.format("%-22s %10.3f %10.3f %12.1f%n",
                    summary.policyId,
                    summary.meanExperiencedSupplyShare(),
                    summary.emergencySupplyFloorFraction(),
                    summary.meanBossFloorTurns()));
        }
        report.append('\n');

        PolicySummary tactical = matrix.get(BehavioralBands.TACTICAL_ID);
        if (tactical != null) appendFloorReport(report, tactical);
        appendMatchupReport(report, matrix);
        appendHeavyReport(report, matrix);
        appendBuildReport(report, matrix);

        report.append("BEHAVIOURAL BANDS\n");
        report.append("--------------------------------------------------------------------------------\n");
        for (RuleResult result : bandResults) {
            report.append(result.isViolation() ? "FAIL " : (result.waived ? "WAIV " : "OK   "));
            report.append(result).append('\n');
        }
        report.append('\n');
        report.append("A WAIV line is out of band with an explicit, reasoned waiver registered in\n");
        report.append("BalanceSchema and mirrored in docs/game-balance-authority.txt. A FAIL line fails\n");
        report.append("`./gradlew balanceSim`.\n");
        return report.toString();
    }

    /**
     * The FLOOR REPORT (balance-overhaul order 2, A4): per generator x node type x depth band, what the
     * planner put on TACTICAL's played floors beside what the play did with it. Then, for A4, every
     * CAVERN cell's heal value against the ROOMS_MST cell of the same node type and band (+/- the
     * tracking tolerance), and the floors below the heal floor per generator.
     */
    private static void appendFloorReport(StringBuilder report, PolicySummary summary) {
        report.append("FLOOR REPORT (").append(summary.policyId)
              .append(") — means per played floor; heal in max-HP, ammo in units, HP as a health fraction\n");
        report.append("--------------------------------------------------------------------------------\n");
        report.append(String.format("%-16s %-8s %-6s %5s %6s %6s %5s %6s %6s %5s %10s %5s %5s %7s%n",
                "generator", "node", "depth", "floor", "spawn", "killed", "grp", "1stDmg", "heal", "<flr",
                "ammo p/took", "hpIn", "hpOut", "0-heal"));
        Map<String, FloorCell> cells = new java.util.TreeMap<>();
        for (FloorLedger floor : summary.allFloors()) {
            if (floor.bossFloor) continue;
            int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
            int bandStart = ((Math.max(1, floor.depth) - 1) / band) * band + 1;
            String key = floor.generatorName + "|" + floor.nodeType + "|" + bandStart;
            cells.computeIfAbsent(key, k -> new FloorCell(floor.generatorName, floor.nodeType, bandStart,
                    bandStart + band - 1)).add(floor);
        }
        for (FloorCell cell : cells.values()) {
            report.append(String.format("%-16s %-8s %2d-%-3d %5d %6.1f %6.1f %5.1f %6s %6.2f %5d %5.0f/%-4.0f %5.2f %5.2f %7d%n",
                    cell.generator, cell.node, cell.firstDepth, cell.lastDepth, cell.floors,
                    cell.spawned / cell.floors, cell.killed / cell.floors, cell.groups / cell.floors,
                    cell.exchangeFloors == 0 ? "-" : String.format("%.1f", cell.turnsToExchange / cell.exchangeFloors),
                    cell.healPlaced / cell.floors, cell.belowHealFloor,
                    cell.ammoPlanned / cell.floors, cell.ammoTaken / cell.floors,
                    cell.healthIn / cell.floors, cell.exitFloors == 0 ? 0f : cell.healthOut / cell.exitFloors,
                    cell.deathsWithNoHeals));
        }
        report.append('\n');
        report.append("A4 — CAVERN heal value present vs ROOMS_MST (same node, same band), tolerance +/-")
              .append(String.format("%.0f%%", BalanceConfig.SUPPLY_TRACK_TOLERANCE * 100f)).append('\n');
        for (FloorCell cave : cells.values()) {
            if (!"cavern".equals(cave.generator)) continue;
            FloorCell rooms = cells.get("rooms_mst|" + cave.node + "|" + cave.firstDepth);
            if (rooms == null) {
                report.append(String.format("  %-8s %2d-%-3d cavern %.2f   rooms_mst -   (no ROOMS_MST floor played)%n",
                        cave.node, cave.firstDepth, cave.lastDepth, cave.healPlaced / cave.floors));
                continue;
            }
            float caveHeal  = cave.healPlaced / cave.floors;
            float roomsHeal = rooms.healPlaced / rooms.floors;
            float gap = roomsHeal <= 0f ? 0f : Math.abs(caveHeal - roomsHeal) / roomsHeal;
            report.append(String.format("  %-8s %2d-%-3d cavern %.2f   rooms_mst %.2f   gap %4.0f%% %s%n",
                    cave.node, cave.firstDepth, cave.lastDepth, caveHeal, roomsHeal, gap * 100f,
                    gap <= BalanceConfig.SUPPLY_TRACK_TOLERANCE ? "OK" : "OUT"));
        }
        report.append('\n');
    }

    /**
     * The MATCHUP REPORT (balance-overhaul order 3, CP7): per policy and depth band — the share of the
     * player's landed damage each DamageClass dealt, EFFECTIVE / RESISTED hits per floor, and hint-driven
     * (C4) switches per floor. Status ticks (burn) are not hits and are excluded.
     */
    private static void appendMatchupReport(StringBuilder report, Map<String, PolicySummary> matrix) {
        ge.tbegvadze.toon3d.entity.DamageClass[] classes = ge.tbegvadze.toon3d.entity.DamageClass.values();
        report.append("MATCHUP REPORT — per played floor; damage share by class (% of landed hit damage)\n");
        report.append("--------------------------------------------------------------------------------\n");
        StringBuilder header = new StringBuilder(String.format("%-20s %-6s %5s %6s %6s %6s", "policy", "depth",
                "floor", "eff/f", "res/f", "swt/f"));
        for (ge.tbegvadze.toon3d.entity.DamageClass damageClass : classes) {
            header.append(String.format(" %5s", damageClass.name().substring(0, Math.min(5, damageClass.name().length()))));
        }
        report.append(header).append('\n');
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        for (PolicySummary summary : matrix.values()) {
            Map<Integer, float[]> cells = new java.util.TreeMap<>();
            for (FloorLedger floor : summary.allFloors()) {
                int bandStart = ((Math.max(1, floor.depth) - 1) / band) * band + 1;
                float[] sums = cells.computeIfAbsent(bandStart, k -> new float[4 + classes.length]);
                sums[0] += 1f;
                sums[1] += floor.effectiveHits;
                sums[2] += floor.resistedHits;
                sums[3] += floor.matchupSwitches;
                for (int classIndex = 0; classIndex < classes.length; classIndex++) {
                    sums[4 + classIndex] += floor.damageByClass[classIndex];
                }
            }
            for (Map.Entry<Integer, float[]> cell : cells.entrySet()) {
                float[] sums = cell.getValue();
                float floors = sums[0];
                float totalDamage = 0f;
                for (int classIndex = 0; classIndex < classes.length; classIndex++) totalDamage += sums[4 + classIndex];
                StringBuilder row = new StringBuilder(String.format("%-20s %2d-%-3d %5.0f %6.2f %6.2f %6.2f",
                        summary.policyId, cell.getKey(), cell.getKey() + band - 1, floors,
                        sums[1] / floors, sums[2] / floors, sums[3] / floors));
                for (int classIndex = 0; classIndex < classes.length; classIndex++) {
                    float share = totalDamage <= 0f ? 0f : 100f * sums[4 + classIndex] / totalDamage;
                    row.append(String.format(" %5.0f", share));
                }
                report.append(row).append('\n');
            }
        }
        report.append("  S-SWITCH alternatives (per policy): switches/COMBAT floor | hint take rate | switches per\n");
        report.append("  COMBAT floor that showed a hint | COMBAT floors (hinted)\n");
        for (PolicySummary summary : matrix.values()) {
            int[] floorCounts = summary.combatFloorsAndHintedCombatFloors();
            report.append(String.format("  %-20s %6.2f | %6.2f | %6.2f | %d (%d)%n", summary.policyId,
                    summary.meanMatchupSwitchesPerCombatFloor(), summary.hintTakeRate(),
                    summary.matchupSwitchesPerHintedCombatFloor(), floorCounts[0], floorCounts[1]));
        }
        report.append('\n');
    }

    /**
     * The HEAVY REPORT (balance-overhaul order 4, CP7b), per policy and depth band (1-5, 6-10, 11-15, 16-ceiling):
     * heavies committed AT the player and how each ended (avoided / guarded / staggered / killed first / landed),
     * the answered share S-HEAVY bands, statuses the armour shield held off, nominal hazard damage dealt to
     * enemies (barrel blasts + fire/toxic DoT) per floor, and deaths whose killing blow was a heavy. A final
     * block lists what each heavy kind threw at TACTICAL.
     */
    private static void appendHeavyReport(StringBuilder report, Map<String, PolicySummary> matrix) {
        report.append("HEAVY REPORT — heavies committed AT the player, by how they ended (counts per band)\n");
        report.append("--------------------------------------------------------------------------------\n");
        report.append(String.format("%-20s %-6s %6s %6s %6s %6s %6s %6s %7s %6s %7s %6s%n", "policy", "depth",
                "commit", "avoid", "guard", "stagg", "killd", "landed", "answerd", "armBlk", "hazDmg/f", "heavyD"));
        int[] lastDepths = BalanceConfig.SIM_HEAVY_REPORT_BAND_LAST_DEPTHS;
        for (PolicySummary summary : matrix.values()) {
            int firstDepth = 1;
            for (int bandIndex = 0; bandIndex <= lastDepths.length; bandIndex++) {
                boolean lastBand = bandIndex == lastDepths.length;
                int lastDepth = lastBand ? Integer.MAX_VALUE : lastDepths[bandIndex];
                int[] totals = summary.heavyTotals(firstDepth, lastDepth);
                String label = lastBand ? firstDepth + "+" : firstDepth + "-" + lastDepth;
                int committed = totals[PolicySummary.HEAVY_COMMITTED];
                int answered = totals[PolicySummary.HEAVY_AVOIDED] + totals[PolicySummary.HEAVY_GUARDED]
                        + totals[PolicySummary.HEAVY_STAGGERED] + totals[PolicySummary.HEAVY_KILLED_FIRST];
                int floors = Math.max(1, totals[PolicySummary.HEAVY_FLOORS]);
                report.append(String.format("%-20s %-6s %6d %6d %6d %6d %6d %6d %7s %6d %7.1f %6d%n",
                        summary.policyId, label, committed,
                        totals[PolicySummary.HEAVY_AVOIDED], totals[PolicySummary.HEAVY_GUARDED],
                        totals[PolicySummary.HEAVY_STAGGERED], totals[PolicySummary.HEAVY_KILLED_FIRST],
                        totals[PolicySummary.HEAVY_LANDED],
                        committed == 0 ? "-" : String.format("%.2f", answered / (float) committed),
                        totals[PolicySummary.HEAVY_BLOCKED_STATUSES],
                        totals[PolicySummary.HEAVY_HAZARD_DAMAGE] / (float) floors,
                        totals[PolicySummary.HEAVY_DEATHS]));
                firstDepth = lastDepth + 1;
            }
            int[] everything = summary.heavyTotals(Integer.MIN_VALUE, Integer.MAX_VALUE);
            int unresolved = everything[PolicySummary.HEAVY_COMMITTED] - everything[PolicySummary.HEAVY_AVOIDED]
                    - everything[PolicySummary.HEAVY_GUARDED] - everything[PolicySummary.HEAVY_STAGGERED]
                    - everything[PolicySummary.HEAVY_KILLED_FIRST] - everything[PolicySummary.HEAVY_LANDED];
            report.append(String.format("%-20s %-6s committed %d, answered share %.2f, unresolved at floor end %d%n",
                    summary.policyId, "ALL", everything[PolicySummary.HEAVY_COMMITTED], summary.heavySuccessShare(),
                    unresolved));
        }
        report.append("  Heavy kinds committed at each policy (count): ");
        report.append('\n');
        ge.tbegvadze.toon3d.enemy.HeavyAttack[] kinds = ge.tbegvadze.toon3d.enemy.HeavyAttack.values();
        for (PolicySummary summary : matrix.values()) {
            int[] byKind = new int[kinds.length];
            for (FloorLedger floor : summary.allFloors()) {
                for (int kindIndex = 0; kindIndex < kinds.length; kindIndex++) {
                    byKind[kindIndex] += floor.heavyCommittedByKind[kindIndex];
                }
            }
            StringBuilder row = new StringBuilder(String.format("  %-20s", summary.policyId));
            for (int kindIndex = 0; kindIndex < kinds.length; kindIndex++) {
                row.append(String.format(" %s %d |", kinds[kindIndex].name(), byKind[kindIndex]));
            }
            report.append(row).append('\n');
        }
        report.append("  Outcomes: AVOIDED left the tiles; GUARDED front-arc hit; STAGGERED cancelled by stagger/riposte/stun;\n");
        report.append("  killd = source died first; landed = unguarded or flanked hit. S-HEAVY bands answered = all but landed.\n\n");
    }

    /**
     * The BUILD REPORT (balance-overhaul order 5, CP6): per TACTICAL row (plain + the four build biases) — runs,
     * median death depth, stalled runs, mean character level reached, keystones taken per run, and the end-of-run
     * dominant-tag distribution; then, per Perk and per Keystone, offers / picks / pick rate for plain TACTICAL
     * and for the four biased variants together. Shop-bought ranks are not offers and are not in the pick rates.
     */
    private static void appendBuildReport(StringBuilder report, Map<String, PolicySummary> matrix) {
        ge.tbegvadze.toon3d.progression.BuildTag[] tags = ge.tbegvadze.toon3d.progression.BuildTag.values();
        ge.tbegvadze.toon3d.progression.Perk[] perks = ge.tbegvadze.toon3d.progression.Perk.values();
        ge.tbegvadze.toon3d.progression.Keystone[] keystones = ge.tbegvadze.toon3d.progression.Keystone.values();
        java.util.List<PolicySummary> rows = new java.util.ArrayList<>();
        PolicySummary plain = matrix.get(BehavioralBands.TACTICAL_ID);
        if (plain != null) rows.add(plain);
        java.util.List<PolicySummary> biased = new java.util.ArrayList<>();
        for (String id : BehavioralBands.buildBiasIds()) {
            PolicySummary variant = matrix.get(id);
            if (variant != null) {
                rows.add(variant);
                biased.add(variant);
            }
        }
        if (rows.isEmpty()) return;

        report.append("BUILD REPORT — per TACTICAL build bias; dominant tag = highest-rank tag at run end\n");
        report.append("--------------------------------------------------------------------------------\n");
        report.append(String.format("%-20s %5s %7s %7s %7s %7s %6s %5s%n",
                "policy", "runs", "medDep", "stalled", "meanLvl", "keys/r", "noRank", "ties"));
        for (PolicySummary summary : rows) {
            int[] picked = summary.summedCounts(run -> run.keystonePicked, keystones.length);
            int keystonesTaken = 0;
            for (int count : picked) keystonesTaken += count;
            report.append(String.format("%-20s %5d %7.1f %7d %7.2f %7.2f %6d %5d%n",
                    summary.policyId, summary.runCount(), summary.medianEndingDepth(), summary.stalledRunCount(),
                    summary.meanFinalPlayerLevel(),
                    summary.runCount() == 0 ? 0f : keystonesTaken / (float) summary.runCount(),
                    summary.runsWithNoPerkRank(), summary.runsWithTiedDominantTag()));
        }
        report.append("  Dominant tag at run end (runs; excludes runs with no perk rank):\n");
        for (PolicySummary summary : rows) {
            int[] counts = summary.dominantTagCounts();
            StringBuilder row = new StringBuilder(String.format("  %-20s", summary.policyId));
            for (ge.tbegvadze.toon3d.progression.BuildTag tag : tags) {
                row.append(String.format(" %s %d |", tag.name(), counts[tag.ordinal()]));
            }
            report.append(row).append('\n');
        }

        report.append("  Perk pick rate per offer (picked / offered on level-up screens):\n");
        report.append(String.format("  %-22s %-15s %7s %7s %6s %9s %8s %6s%n", "perk", "tag",
                "offered", "picked", "rate", "B-offered", "B-picked", "B-rate"));
        int[] plainPerkOffered = plain == null ? new int[perks.length] : plain.summedCounts(run -> run.perkOffered, perks.length);
        int[] plainPerkPicked  = plain == null ? new int[perks.length] : plain.summedCounts(run -> run.perkPicked, perks.length);
        int[] biasedPerkOffered = new int[perks.length];
        int[] biasedPerkPicked  = new int[perks.length];
        for (PolicySummary variant : biased) {
            int[] offered = variant.summedCounts(run -> run.perkOffered, perks.length);
            int[] picked  = variant.summedCounts(run -> run.perkPicked, perks.length);
            for (int index = 0; index < perks.length; index++) {
                biasedPerkOffered[index] += offered[index];
                biasedPerkPicked[index]  += picked[index];
            }
        }
        for (ge.tbegvadze.toon3d.progression.Perk perk : perks) {
            int index = perk.ordinal();
            report.append(String.format("  %-22s %-15s %7d %7d %6s %9d %8d %6s%n", perk.name(), perk.tag.name(),
                    plainPerkOffered[index], plainPerkPicked[index], rate(plainPerkPicked[index], plainPerkOffered[index]),
                    biasedPerkOffered[index], biasedPerkPicked[index],
                    rate(biasedPerkPicked[index], biasedPerkOffered[index])));
        }

        report.append("  Keystone pick rate (picked / offered on keystone screens):\n");
        report.append(String.format("  %-22s %-15s %7s %7s %6s %9s %8s %6s%n", "keystone", "tag",
                "offered", "picked", "rate", "B-offered", "B-picked", "B-rate"));
        int[] plainKeyOffered = plain == null ? new int[keystones.length] : plain.summedCounts(run -> run.keystoneOffered, keystones.length);
        int[] plainKeyPicked  = plain == null ? new int[keystones.length] : plain.summedCounts(run -> run.keystonePicked, keystones.length);
        int[] biasedKeyOffered = new int[keystones.length];
        int[] biasedKeyPicked  = new int[keystones.length];
        for (PolicySummary variant : biased) {
            int[] offered = variant.summedCounts(run -> run.keystoneOffered, keystones.length);
            int[] picked  = variant.summedCounts(run -> run.keystonePicked, keystones.length);
            for (int index = 0; index < keystones.length; index++) {
                biasedKeyOffered[index] += offered[index];
                biasedKeyPicked[index]  += picked[index];
            }
        }
        for (ge.tbegvadze.toon3d.progression.Keystone keystone : keystones) {
            int index = keystone.ordinal();
            report.append(String.format("  %-22s %-15s %7d %7d %6s %9d %8d %6s%n", keystone.name(), keystone.tag.name(),
                    plainKeyOffered[index], plainKeyPicked[index], rate(plainKeyPicked[index], plainKeyOffered[index]),
                    biasedKeyOffered[index], biasedKeyPicked[index],
                    rate(biasedKeyPicked[index], biasedKeyOffered[index])));
        }
        report.append("  B- columns: the four build-biased variants together. noRank = runs that ended with no perk rank\n");
        report.append("  (excluded from S-BUILD's share); ties = dominant tag decided by a tie (earliest BuildTag wins).\n\n");
    }

    /** "picked/offered" as a two-decimal rate, or "-" when nothing was offered. */
    private static String rate(int picked, int offered) {
        return offered == 0 ? "-" : String.format("%.2f", picked / (float) offered);
    }

    /** One FLOOR REPORT cell's running sums. */
    private static final class FloorCell {
        final String generator;
        final String node;
        final int    firstDepth;
        final int    lastDepth;
        int   floors;
        float spawned, killed, groups, turnsToExchange, healPlaced, ammoPlanned, ammoTaken, healthIn, healthOut;
        int   exchangeFloors, exitFloors, belowHealFloor, deathsWithNoHeals;

        FloorCell(String generator, String node, int firstDepth, int lastDepth) {
            this.generator  = generator;
            this.node       = node;
            this.firstDepth = firstDepth;
            this.lastDepth  = lastDepth;
        }

        void add(FloorLedger floor) {
            floors++;
            spawned     += floor.enemiesSpawned;
            killed      += floor.enemiesKilled;
            groups      += floor.groups;
            if (floor.turnsToFirstDamageExchange >= 0) {
                turnsToExchange += floor.turnsToFirstDamageExchange;
                exchangeFloors++;
            }
            healPlaced  += floor.healValuePlaced;
            ammoPlanned += floor.ammoPlannedUnits;
            ammoTaken   += floor.ammoGained;
            healthIn    += floor.healthFractionOnEntry;
            if (floor.exited) {
                healthOut += floor.healthFractionOnExit;
                exitFloors++;
            }
            if (!floor.healFloorMet()) belowHealFloor++;
            if (floor.diedWithNoHealsHeld) deathsWithNoHeals++;
        }
    }

    /**
     * Writes the report next to the other build reports and returns its path.
     *
     * @param projectDirectory the module directory the {@code build/} folder lives under
     */
    public static Path write(Path projectDirectory, String reportText) throws IOException {
        Path directory = projectDirectory.resolve(REPORT_DIRECTORY);
        Files.createDirectories(directory);
        Path file = directory.resolve(SUMMARY_FILE);
        Files.write(file, reportText.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
