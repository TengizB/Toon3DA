package ge.tbegvadze.toon3d.narrative;

/**
 * The order-3 BOOT-CARD text table — the built-in fallback for every line {@link BootCardCatalog}
 * registers.  Kept out of {@link StoryStrings#defaults()} so that method stays readable;
 * {@code defaults()} calls {@link #registerDefaults(StoryStrings)} to fold this table in, exactly
 * as it does for {@link BarkStrings}.
 *
 * <p>Localisation rule: this is the ONLY place boot-card text lives in Java, and it is a fallback —
 * the shipped table is {@code assets/story/story-strings.properties}, which the render layer loads
 * at startup ({@code render/StoryStringsLoader}).  KEEP THE TWO IN SYNC: a test asserts every
 * catalogued card and wake line resolves here AND in the asset.
 *
 * <h3>Two voices, two rules</h3>
 * <ul>
 *   <li>SYSTEM lines are flat machine status.  Each must fit on ONE panel line
 *       ({@code STORY_LINE_MAX_CHARS}) because a wrapped status readout looks broken rather than
 *       cold — a test enforces the cap.  They never editorialise and never mention the player.</li>
 *   <li>ORA's wake lines are prose and wrap normally, up to the card's two-line cap.  They may
 *       contain {@link BootCardSystem#INSTANCE_TOKEN}, which is substituted with the formatted
 *       reprint number at present time.</li>
 * </ul>
 *
 * <p>Voice sources: {@code story/dialog/system-cards.md}, {@code story/dialog/ai-assistant.md},
 * {@code story/06-endings.md}.
 */
public final class BootCardStrings {

    private BootCardStrings() {}

    /** Adds every order-3 boot-card line to {@code strings}.  Returns the same table for chaining. */
    public static StoryStrings registerDefaults(StoryStrings strings) {
        registerSystemCards(strings);
        registerWakeLines(strings);
        strings.put("story.boot.continue", "CONTINUE");
        return strings;
    }

    /**
     * The machine voice.  The default card frames every run; the three endgame cards exist to
     * subvert it, which only works because they are printed in the same flat, unaware register.
     */
    private static void registerSystemCards(StoryStrings strings) {
        // Default reprint — the card the player sees on every single run, first one included.
        strings.put("story.boot.system.reprint.1", "PREVIOUS INSTANCE - TERMINATED");
        strings.put("story.boot.system.reprint.2", "SOUL RESERVE .......... SUFFICIENT");
        strings.put("story.boot.system.reprint.3", "REPRINTING FROM LAST CHECKPOINT");

        // FREE IT — the reserve went to buying the being's freedom. Then nothing reloads.
        strings.put("story.boot.system.free.1",    "PREVIOUS INSTANCE - TERMINATED");
        strings.put("story.boot.system.free.2",    "SOUL RESERVE .......... DEPLETED");
        strings.put("story.boot.system.free.3",    "NO CHECKPOINT AUTHORIZED FOR REPRINT");

        // KILL IT — the same absence by the other road: no source left to print a pattern from.
        strings.put("story.boot.system.kill.1",    "PREVIOUS INSTANCE - TERMINATED");
        strings.put("story.boot.system.kill.2",    "PATTERN SOURCE ........ UNRECOVERABLE");
        strings.put("story.boot.system.kill.3",    "NO CHECKPOINT AUTHORIZED FOR REPRINT");

        // OBEY — a promotion, in the Organization's flat voice. The reward is more of the same.
        strings.put("story.boot.system.obey.1",    "INSTANCE - RETAINED");
        strings.put("story.boot.system.obey.2",    "REPRINT PRIORITY ...... RAISED");
        strings.put("story.boot.system.obey.3",    "STANDING ASSIGNMENT: EREBUS, INDEFINITE");
    }

    /**
     * ORA on waking you up — the one story beat the player is guaranteed to get, every run.  The
     * tone arc lives entirely in which region these are registered under (see BootCardCatalog).
     */
    private static void registerWakeLines(StoryStrings strings) {
        // Region 1 — cheerful; your deaths are a scoreboard and she is proud of the numbers.
        strings.put("story.boot.wake.rings.1",     "Morning, sunshine. Reprint {instance}. New record.");
        strings.put("story.boot.wake.rings.2",     "You're up. Print {instance}, all limbs accounted for.");
        strings.put("story.boot.wake.rings.3",     "Back on your feet. I logged that one as a stumble.");
        strings.put("story.boot.wake.rings.4",     "Reprint {instance}. Don't worry, I keep the good copies.");

        // Region 2 — distracted; she has started asking what the printers run on.
        strings.put("story.boot.wake.galleries.1", "Reprint {instance}. Hey - what do the printers run on?");
        strings.put("story.boot.wake.galleries.2", "You're awake. The reserve ticked down again. Odd.");
        strings.put("story.boot.wake.galleries.3", "Print {instance}. The line item says 'yield'. Of what?");
        strings.put("story.boot.wake.galleries.4", "Up you get. I asked about the reserve. Nobody answered.");

        // Region 3 — cracking; she knows what the Cradles are fed and can't un-know it.
        strings.put("story.boot.wake.reliquary.1", "Reprint {instance}. That's {instance} pieces of something alive.");
        strings.put("story.boot.wake.reliquary.2", "You're awake. I'm not counting for fun anymore.");
        strings.put("story.boot.wake.reliquary.3", "Print {instance}. I read what the Cradles are fed. Sorry.");
        strings.put("story.boot.wake.reliquary.4", "Back again. Something down there paid for that.");

        // Region 4 — quiet, protective; she is keeping things off the log for you now.
        strings.put("story.boot.wake.wound.1",     "You're awake. I didn't tell them what you found.");
        strings.put("story.boot.wake.wound.2",     "Print {instance}. I left it out of the log. All of it.");
        strings.put("story.boot.wake.wound.3",     "You're up. It goes quiet every time you die.");
        strings.put("story.boot.wake.wound.4",     "Back. I've stopped filing these. Let them ask.");

        // Region 5 — the Core; nothing left to hedge.
        strings.put("story.boot.wake.core.1",      "You're awake. Last one, I think. Either way, I'm here.");
        strings.put("story.boot.wake.core.2",      "Print {instance}. Whatever you choose down here, I'm with you.");
        strings.put("story.boot.wake.core.3",      "You're up. They can't reprint this part of me out.");
    }
}
