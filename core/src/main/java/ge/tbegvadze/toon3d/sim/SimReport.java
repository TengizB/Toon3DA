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
                "policy", "S-gap", "emergency", "bossTurns"));
        for (PolicySummary summary : matrix.values()) {
            report.append(String.format("%-22s %10.3f %10.3f %12.1f%n",
                    summary.policyId,
                    summary.meanScarcityGapVersusModel(),
                    summary.emergencySupplyFloorFraction(),
                    summary.meanBossFloorTurns()));
        }
        report.append('\n');

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
