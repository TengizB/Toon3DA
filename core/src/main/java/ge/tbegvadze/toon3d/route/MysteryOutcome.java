package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * The hidden outcome table at the heart of a MYSTERY node (route-map order-9). A MYSTERY icon is
 * always '?'; you find out only by dropping in. The outcome is rolled at ENTRY (not at map-gen) from
 * the node's fixed {@code nodeSeed}, so a scanner can reveal a category TONE — a genuine preview of
 * which BUCKET the sector falls in — without ever leaking the exact result. Expected value is
 * deliberately NEUTRAL (some jackpots, some duds) so MYSTERY stays a real gamble, never a strictly-
 * good pick.
 *
 * <p>Each row carries its pool weight, the SCAN tone bucket it reveals (several outcomes share a
 * bucket, so a scan narrows but never pinpoints), a run-telemetry token logged even though the
 * outcome was hidden (so the post-run route story is complete), and an optional delegate
 * {@code profileId}. {@link MysteryProfile} forwards to that profile when set, or builds the outcome's
 * floor directly otherwise — so adding an outcome is one enum row plus (usually) a delegate id. The
 * generic first-frame reveal sting is applied by {@link MysteryProfile}, not stored per outcome. Pure
 * / headless — no LibGDX imports.
 */
public enum MysteryOutcome {

    /** The dream pull: a lightly-guarded room stuffed with loot (a super-cache). Built directly. */
    VAULT_JACKPOT(RouteMapConstants.MYSTERY_WEIGHT_VAULT, ScanTone.REWARD_RICH, "VAULT", null),

    /** The explorer's gamble: server rooms + lock-and-key, rewards those who search. Built directly. */
    SECRET_WARREN(RouteMapConstants.MYSTERY_WEIGHT_SECRET_WARREN, ScanTone.REWARD_RICH, "WARREN", null),

    /** A skill/patience test: dense hazards (traps degrade to barrels), loot at the end. Built directly. */
    TRAP_GAUNTLET(RouteMapConstants.MYSTERY_WEIGHT_TRAP_GAUNTLET, ScanTone.HIGH_RISK, "TRAP", null),

    /** Looks calm, then a combat-heavy fight: delegates to the ELITE hotzone (lockdown degrade). */
    AMBUSH(RouteMapConstants.MYSTERY_WEIGHT_AMBUSH, ScanTone.HIGH_RISK, "AMBUSH", EliteProfile.PROFILE_ID),

    /** The "nothing dangerous, something interesting" pull: delegates to the safe MED-BAY (EVENT degrade). */
    LORE_SIGNAL(RouteMapConstants.MYSTERY_WEIGHT_LORE_SIGNAL, ScanTone.QUIET, "LORE", RestProfile.PROFILE_ID),

    /** The bad-but-survivable pull: a failing sector — hazards, unlit, no reward, a shortcut. Built directly. */
    MALFUNCTION(RouteMapConstants.MYSTERY_WEIGHT_MALFUNCTION, ScanTone.QUIET, "MALFUNCTION", null);

    /**
     * Scan tone buckets a scanner reveals. Deliberately coarser than the outcome list: multiple
     * outcomes map to one tone, so a scan is informed gambling — it says "reads as reward-rich" or
     * "reads as high risk", never which exact outcome waits.
     */
    public enum ScanTone {
        REWARD_RICH("READS: REWARD-RICH"),
        HIGH_RISK("READS: HIGH RISK / HIGH REWARD"),
        QUIET("READS: QUIET SECTOR");

        private final String label;

        ScanTone(String label) {
            this.label = label;
        }

        /** Human-facing readout shown on a scanned MYSTERY card. */
        public String label() {
            return label;
        }
    }

    // Independent sub-stream so the outcome roll never correlates with the node's other rolls.
    private static final long OUTCOME_STREAM_SALT = 9L;

    private final float    weight;
    private final ScanTone scanTone;
    private final String   statToken;
    private final String   delegateProfileId;

    MysteryOutcome(float weight, ScanTone scanTone, String statToken, String delegateProfileId) {
        this.weight            = weight;
        this.scanTone          = scanTone;
        this.statToken         = statToken;
        this.delegateProfileId = delegateProfileId;
    }

    /** The scan tone bucket this outcome falls in. */
    public ScanTone scanTone() {
        return scanTone;
    }

    /** Short run-telemetry token (e.g. "VAULT") logged when this outcome resolves. */
    public String statToken() {
        return statToken;
    }

    /** The profile id this outcome delegates to, or {@code null} to build its floor directly. */
    public String delegateProfileId() {
        return delegateProfileId;
    }

    /**
     * Rolls the outcome for a MYSTERY node deterministically from its {@code nodeSeed}. Because the
     * roll draws from the fixed node seed (never visit order), the same node yields the same outcome
     * every time it is entered on a given run — and a scanner can preview its {@link #scanTone()}
     * from the same seed without the exact result being reachable.
     */
    public static MysteryOutcome rollFor(long nodeSeed) {
        MysteryOutcome[] table = values();
        float[] weights = new float[table.length];
        for (int index = 0; index < table.length; index++) {
            weights[index] = table[index].weight;
        }
        RouteRng rng = new RouteRng(GameMath.mixSeed(nodeSeed, OUTCOME_STREAM_SALT));
        return table[GameMath.weightedChoiceIndex(weights, rng.nextFloat01())];
    }

    /** The scan tone label a scanner would reveal for a MYSTERY node with this {@code nodeSeed}. */
    public static String scanToneLabel(long nodeSeed) {
        return rollFor(nodeSeed).scanTone().label();
    }
}
