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
        report.append("Seeds per policy : ").append(BalanceConfig.SIM_SEED_COUNT).append('\n');
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
