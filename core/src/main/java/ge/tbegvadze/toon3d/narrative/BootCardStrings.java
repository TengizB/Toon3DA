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
        // THE FIRST PRINT (narrative-rework order-2 A/B) — the first four lines of this fiction that
        // anybody ever reads, on the launch screen and then on the card itself.  Not one proper noun
        // of the setting between them: a person died, that person is you, there is a copy, the copy
        // is being made.  Everything the game says afterwards is built on somebody having parsed
        // these, so they are plainer than the machine will ever be again.
        strings.put("story.boot.system.firstprint.1", "OPERATOR ............. DECEASED");
        strings.put("story.boot.system.firstprint.2", "BODY RECOVERY ........ NOT POSSIBLE");
        strings.put("story.boot.system.firstprint.3", "BACKUP COPY .......... ON FILE");
        strings.put("story.boot.system.firstprint.4", "PRINTING NEW BODY .... STAND BY");

        // Default reprint — the card the player sees on every run after their first print.  Plain
        // words only: the reserve line moved out of here and into the band below, because on the
        // surface it was a Region-3 reveal spent on somebody with no idea what it meant.
        strings.put("story.boot.system.reprint.1", "PREVIOUS BODY ........ DECEASED");
        strings.put("story.boot.system.reprint.2", "BACKUP COPY .......... ON FILE");
        strings.put("story.boot.system.reprint.3", "PRINTING NEW BODY .... STAND BY");

        // The same card from the Harvesting Galleries down, where ORA has started asking what the
        // printers run on.  The player now reads SOUL RESERVE on every card for the rest of the
        // game, understanding it — which is the beat the old card was throwing away on screen one.
        strings.put("story.boot.system.reprint.deep.1", "PREVIOUS BODY ........ DECEASED");
        strings.put("story.boot.system.reprint.deep.2", "SOUL RESERVE ......... SUFFICIENT");
        strings.put("story.boot.system.reprint.deep.3", "PRINTING NEW BODY .... STAND BY");

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
        // RESERVED — the first print.  Thirty seconds after a stranger's death notice, so she does
        // not lead with a name: she tells them they are alright and that somebody is with them, and
        // introduces herself properly the moment they have control (BarkStrings' cold open).
        strings.put("story.boot.wake.first.1",     "Easy. Give the eyes a second. I'm right here.");

        // RESERVED — the player's own first deaths.  This is where the loop is explained, in the
        // plainest words the game owns, and it is not a joke about a new record.
        strings.put("story.boot.wake.firstdeath.1",
                                                   "You died. It's alright. That's what the copy is for.");
        strings.put("story.boot.wake.firstdeath.2",
                                                   "Nothing you were carrying came back. You did. Again.");
        strings.put("story.boot.wake.firstdeath.report",
                                                   "There's a REPORT plate if you want to see what got you.");

        // RESERVED — the counter milestones (narrative-rework order-9 D).  Every tenth print, one
        // line about the number itself.  It starts as a joke about round numbers and ends as the
        // thing she will not say out loud, which is the slowest turn in the game: the same gag,
        // told six times across a hundred deaths, arriving somewhere else each time.
        strings.put("story.boot.wake.milestone.10",  "Print ten. Round numbers. I'd get you a cake.");
        strings.put("story.boot.wake.milestone.20",  "Print twenty. You've been at this a while now.");
        strings.put("story.boot.wake.milestone.30",  "Print thirty. I've stopped rounding it off in my head.");
        strings.put("story.boot.wake.milestone.40",  "Print forty. I know what that is now. I'd rather not say it.");
        strings.put("story.boot.wake.milestone.50",  "Fifty. I keep the number because somebody should.");
        strings.put("story.boot.wake.milestone.100", "A hundred. I'm not going to pretend that's nothing.");

        // RESERVED — the RE-ENTRY lines (narrative-rework order-9 A).  One sentence to somebody who
        // has been away for a day or a week: where they are, and what they were last told.  Never
        // what to do next — the moment one of these acquires an objective it is a quest log wearing
        // her voice.  Written as picking a conversation back up, because that is what it is.
        strings.put("story.boot.wake.reentry.rings",
                                                   "You're back at the rings. Same job: down, find it, restart the works.");
        strings.put("story.boot.wake.reentry.galleries",
                                                   "We were in the cutting floors. Working out what they were cutting.");
        strings.put("story.boot.wake.reentry.reliquary",
                                                   "The Reliquary. We'd just found out what the printers run on.");
        strings.put("story.boot.wake.reentry.wound",
                                                   "We were in the warm part. Where it stops being a facility.");
        strings.put("story.boot.wake.reentry.core",
                                                   "The Core. You know what's down there now. Whenever you're ready.");

        // Region 1 — warm and matter-of-fact.  She reads the number out and checks the body over.
        //
        // She does NOT score the death (order-6 C, doctrine D6).  "New record" and "I keep the good
        // copies" were the friend of the character celebrating their death on the screen that exists
        // to tell them they died — and the second one also claimed she stores patterns, which she does
        // not; the Cradles do.  The counter still climbs.  It just stopped cheering.
        strings.put("story.boot.wake.rings.1",     "Morning. Print {instance}. All limbs where they should be.");
        // Rewritten with .1 rather than because of a fault of its own: .1's new tail says the same
        // thing "all limbs accounted for" said, and two rows of one pool must never be one line (D5).
        strings.put("story.boot.wake.rings.2",     "You're up. Print {instance}. Feed lines are clear.");
        strings.put("story.boot.wake.rings.3",     "Back on your feet. I logged that one as a stumble.");
        strings.put("story.boot.wake.rings.4",     "Print {instance}. Same as the last one, near enough.");

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
