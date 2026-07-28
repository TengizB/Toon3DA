package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * The matrix runner (new-game-balancr order 9): plays every (policy, seed) pair and reduces the
 * results to {@link PolicySummary} distributions the behavioural bands assert on.
 *
 * <p>Runs are independent, so the matrix is executed in parallel — but each run is driven entirely
 * by its own seed, so the RESULT is identical whether one thread or twelve produced it. Determinism
 * is the whole point: a band that moved because a machine had more cores would prove nothing.
 */
public final class BalanceSimulator {

    private BalanceSimulator() {}

    /** Builds a policy instance for one run. Policies carry per-run RNG, so they are not shared. */
    public interface PolicyFactory {
        String id();
        PlayerPolicy create(long runSeed);
    }

    /** The three registered policies: the skill floor, the skill ceiling, and the regression case. */
    public static List<PolicyFactory> standardPolicies() {
        List<PolicyFactory> factories = new ArrayList<>();
        factories.add(factory("NAIVE",                seed -> new NaivePolicy(seed)));
        factories.add(factory("TACTICAL",             seed -> new TacticalPolicy(seed)));
        factories.add(factory("HOARDER-START-WEAPON", seed -> new HoarderStartWeaponPolicy(seed)));
        return factories;
    }

    /** The single policy the S-GATE band is stated on — the headline guarantee. */
    public static PolicyFactory hoarderPolicy() {
        return factory("HOARDER-START-WEAPON", seed -> new HoarderStartWeaponPolicy(seed));
    }

    public static PolicyFactory tacticalPolicy() {
        return factory("TACTICAL", seed -> new TacticalPolicy(seed));
    }

    public static PolicyFactory naivePolicy() {
        return factory("NAIVE", seed -> new NaivePolicy(seed));
    }

    /**
     * Plays {@code seedCount} runs of one policy.
     *
     * @param seedOffset added to the run index to form the run seed, so different bands can sample
     *                   different (still reproducible) slices of seed space
     */
    public static PolicySummary runPolicy(PolicyFactory factory, int seedCount,
                                          long seedOffset, SimSettings settings) {
        List<RunLedger> ledgers = java.util.stream.IntStream.range(0, seedCount)
                .parallel()
                .mapToObj(runIndex -> {
                    long runSeed = seedOffset + runIndex;
                    return new SimWorld(runSeed, factory.create(runSeed), settings).play();
                })
                .sorted(java.util.Comparator.comparingLong(ledger -> ledger.runSeed))
                .collect(java.util.stream.Collectors.toList());
        return new PolicySummary(factory.id(), ledgers);
    }

    /** Plays the full matrix at the shipping sample size — the {@code ./gradlew balanceSim} workload. */
    public static Map<String, PolicySummary> runStandardMatrix() {
        return runMatrix(standardPolicies(), BalanceConfig.SIM_SEED_COUNT,
                         DEFAULT_SEED_OFFSET, SimSettings.standard());
    }

    /** Plays an arbitrary matrix. Keyed by policy id, in the order the factories were listed. */
    public static Map<String, PolicySummary> runMatrix(List<PolicyFactory> factories, int seedCount,
                                                       long seedOffset, SimSettings settings) {
        Map<String, PolicySummary> summaries = new LinkedHashMap<>();
        for (PolicyFactory factory : factories) {
            summaries.put(factory.id(), runPolicy(factory, seedCount, seedOffset, settings));
        }
        return summaries;
    }

    /**
     * Seed-space origin for the standard matrix. Fixed so the reported distributions are the same
     * numbers every time anyone runs the gate — a moving sample is not a regression test.
     */
    public static final long DEFAULT_SEED_OFFSET = 0xB0A1D0000L;

    private static PolicyFactory factory(String id, java.util.function.LongFunction<PlayerPolicy> builder) {
        return new PolicyFactory() {
            @Override public String id() { return id; }
            @Override public PlayerPolicy create(long runSeed) { return builder.apply(runSeed); }
        };
    }
}
