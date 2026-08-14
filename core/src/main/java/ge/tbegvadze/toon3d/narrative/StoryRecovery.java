package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.List;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * MISSED-BEAT RECOVERY (narrative-rework order-9 C) — what happens to a line the player closed
 * before they could possibly have read it.
 *
 * <p>Every {@code STORY_CRITICAL} one-shot beat in this game gets exactly ONE chance to land, and
 * the layer has always known when that chance was wasted: {@link StoryTelemetry} already measures a
 * dismissal inside {@code STORY_TELEMETRY_FAST_DISMISS_SECONDS} and, until now, only counted it.
 * This class is what acts on it.
 *
 * <h3>The rule, and it is the whole class</h3>
 * <b>MECHANICS may be repeated once, on evidence.  STORY is never repeated, only archived.</b>
 * <ul>
 *   <li>A missed <b>STORY</b> beat — a region entry, a reveal log, a gate order — goes to the
 *       archive and nowhere else ({@link StoryProgress#recordMissedBeat}).  It appears on the
 *       "you may have missed this" shelf, on a screen the player has to choose to open.  Re-showing
 *       a story beat out in the world is how a narrative layer becomes something a player starts
 *       trying to escape from.</li>
 *   <li>A missed <b>TEACHING</b> beat — a control hint — is a different thing entirely: somebody who
 *       did not read it is not bored, they are about to be stuck.  It joins the re-teach lane
 *       ({@link #consumeRecoverableTopic()}), which is the evidence order-4's competence model
 *       consumes.  "They didn't read it" is EVIDENCE A there, and it is the one kind of evidence
 *       that does not have to wait for the player to fail at something first.</li>
 * </ul>
 *
 * <h3>What this class deliberately does NOT do</h3>
 * It never re-delivers anything itself.  The re-teach LINE is order-4's — a shorter, blunter,
 * differently-angled second telling, because verbatim repetition is exactly what players report as
 * nagging — and until that order ships, a missed teaching beat is recorded and offered, and nothing
 * says it again.  Recording the evidence and choosing the words are two jobs, and this is the first
 * one.
 *
 * <p>Non-critical lines are ignored outright.  A flavour quip somebody swiped away was not missed;
 * it was declined, which is a player using the layer correctly.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class StoryRecovery {

    private final BarkRegistry  registry;
    private final StoryProgress progress;

    /**
     * Teaching subjects whose line was closed unread and which order-4 may re-teach, oldest first.
     * In-memory and per-session on purpose: a re-teach that arrives three days after the mistake is
     * not help, it is a lecture, so the evidence expires with the sitting that produced it.
     */
    private final List<String> recoverableTopics = new ArrayList<>();

    public StoryRecovery(BarkRegistry registry, StoryProgress progress) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        if (progress == null) throw new IllegalArgumentException("progress must not be null");
        this.registry = registry;
        this.progress = progress;
    }

    /**
     * Files a line the player dismissed before they could have read it, and routes it down whichever
     * of the two lanes it belongs in.  Called by the engine from the same place the tuning counter
     * is stamped, so the two can never disagree about what "missed" means.
     *
     * @param barkId the line that was on screen
     * @return true when the beat was recovered somewhere (archived, or queued for a re-teach)
     */
    public boolean recordFastDismiss(String barkId) {
        BarkDefinition row = registry.getById(barkId);
        // Only a MANDATORY beat is worth recovering. Everything else the player closed quickly was
        // declined rather than missed, and chasing a declined flavour line is how a story layer
        // starts arguing with its audience.
        if (row == null || row.getPriority() != BarkPriority.STORY_CRITICAL) return false;

        if (isTeachingBeat(row)) {
            String topic = row.getSubjectKey();
            if (topic == null || recoverableTopics.contains(topic)) return false;
            recoverableTopics.add(topic);
            return true;
        }
        if (!progress.recordMissedBeat(row.getId())) return false;
        // The shelf appears in the archive the first time there is anything on it, and not before.
        // A player who reads what they are shown never learns this row exists, which is exactly
        // right: it is a safety net, and a safety net nobody needed should be invisible.
        progress.unlockCodexEntry(StoryUiConstants.STORY_MISSED_SHELF_CODEX_ID);
        return true;
    }

    /**
     * True when this row teaches a CONTROL rather than telling the player something about the world.
     * The distinction is the trigger, not the text: a teaching beat is the one kind of line whose
     * whole value is still there the second time it is said.
     */
    private static boolean isTeachingBeat(BarkDefinition row) {
        return row.getTrigger() == BarkTrigger.CONTROL_HINT;
    }

    /**
     * Takes the next teaching subject eligible for a re-teach ({@code ControlHint.name()}), or null.
     *
     * <p>THE SEAM.  Order-9 supplies the evidence; order-4's competence model owns what happens next
     * — which words come back, the one-re-teach-ever cap, the per-floor rate limit and the rule that
     * the second telling is never the first one repeated.  Until that lands, the queue simply fills
     * and nothing consumes it, which is the correct failure mode: silence.
     */
    public String consumeRecoverableTopic() {
        if (recoverableTopics.isEmpty()) return null;
        return recoverableTopics.remove(0);
    }

    /** How many teaching topics are waiting on the re-teach lane (tests / tuning). */
    public int getRecoverableTopicCount() {
        return recoverableTopics.size();
    }

    /** Drops the session's re-teach evidence — a fresh sitting starts nobody on a warning. */
    public void clearRecoverableTopics() {
        recoverableTopics.clear();
    }
}
