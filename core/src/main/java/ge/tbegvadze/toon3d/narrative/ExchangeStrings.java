package ge.tbegvadze.toon3d.narrative;

/**
 * The order-4 EXCHANGE text table — the built-in fallback for every line {@link ExchangeCatalog}
 * registers.  Kept out of {@link StoryStrings#defaults()} so that method stays readable;
 * {@code defaults()} calls {@link #registerDefaults(StoryStrings)} to fold this table in, exactly as
 * it does for {@link BarkStrings} and {@link BootCardStrings}.
 *
 * <p>Localisation rule: this is the ONLY place exchange text lives in Java, and it is a fallback —
 * the shipped table is {@code assets/story/story-strings.properties}, which the render layer loads
 * at startup ({@code render/StoryStringsLoader}).  KEEP THE TWO IN SYNC: a test asserts every
 * catalogued prompt, answer and reply resolves here AND in the asset.
 *
 * <h3>Two shapes, two rules</h3>
 * <ul>
 *   <li>PROMPTS and REPLIES are prose and wrap normally, up to
 *       {@code STORY_EXCHANGE_MAX_LINES} (3) — a test enforces the cap, because a prompt that
 *       overflows its panel is unreadable exactly when the player has to decide something.</li>
 *   <li>ANSWERS are ONE short line each, capped at {@code STORY_EXCHANGE_OPTION_MAX_CHARS}, and must
 *       be DISTINCT at a glance.  Three shades of "yes" is the same as having no choice.</li>
 * </ul>
 *
 * <p>The Organization's lines are written in sentence case here and upper-cased at delivery by
 * {@link ExchangeSystem} (its {@link TypeStyle}), so the table stays readable to translators.  The
 * player's own answers are never upper-cased: they do not speak in the Organization's voice, even
 * when they are obeying it.
 *
 * <p>Voice sources: {@code story/dialog/ai-assistant.md}, {@code planet-voice.md},
 * {@code organization-comms.md}, {@code story/06-endings.md}.
 */
public final class ExchangeStrings {

    private ExchangeStrings() {}

    /** Adds every order-4 exchange line to {@code strings}.  Returns the same table for chaining. */
    public static StoryStrings registerDefaults(StoryStrings strings) {
        registerRingsBriefing(strings);
        registerRingsTeaching(strings);
        registerGalleriesArrival(strings);
        registerGalleriesLedger(strings);
        registerGalleriesDemand(strings);
        registerReliquaryAddress(strings);
        registerReliquaryLog(strings);
        registerWoundVoice(strings);
        registerWoundCoercion(strings);
        registerCoreOrder(strings);
        registerCoreEnding(strings);
        registerDeepStrata(strings);
        // The reply's acknowledge button. Chrome, but still a localised id — never a literal.
        strings.put("story.exchange.continue", "CONTINUE");
        return strings;
    }

    /** Region 1 — the briefing. The first time the player gets to have an opinion about the word. */
    private static void registerRingsBriefing(StoryStrings strings) {
        strings.put("story.exchange.rings.briefing.prompt",
                "Operator. The contaminant is below. Purge it and restore the yield.");
        strings.put("story.exchange.rings.briefing.obey",   "Understood. Going down.");
        strings.put("story.exchange.rings.briefing.obey.reply",
                "That's the spirit. I'll log it as enthusiasm.");
        strings.put("story.exchange.rings.briefing.doubt",  "Contaminant. Odd word for it.");
        strings.put("story.exchange.rings.briefing.doubt.reply",
                "It's the word on the form. I didn't write the form.");
        strings.put("story.exchange.rings.briefing.probe",  "What am I actually killing?");
        strings.put("story.exchange.rings.briefing.probe.reply",
                "Unclear. Biological. Big. That's the entire brief.");
    }

    /**
     * Region 1 — the teaching exchange (order-5).  The first choice the player is ever offered, and
     * it decides nothing on purpose: the point is to learn, with no enemy awake and nothing at
     * stake, that the world stops and waits and that any of these is a safe thing to tap.
     */
    private static void registerRingsTeaching(StoryStrings strings) {
        strings.put("story.exchange.rings.teaching.prompt",
                "Quick thing while it's quiet. How are you doing with all this?");
        strings.put("story.exchange.rings.teaching.ready", "Ready to work.");
        strings.put("story.exchange.rings.teaching.ready.reply",
                "Good. I'll stop asking, then.");
        strings.put("story.exchange.rings.teaching.off",   "Something feels off.");
        strings.put("story.exchange.rings.teaching.off.reply",
                "Noted. Probably the pressure. It does that.");
        strings.put("story.exchange.rings.teaching.down",  "Just point me down.");
        strings.put("story.exchange.rings.teaching.down.reply",
                "Down. Always down. That part I can do.");
    }

    /** Region 2 — the mandatory first-doubt beat, said out loud before the first floor. */
    private static void registerGalleriesArrival(StoryStrings strings) {
        strings.put("story.exchange.galleries.arrival.prompt",
                "Before we go in. These are cutting floors, not defence works. Does that land wrong for you too?");
        strings.put("story.exchange.galleries.arrival.work",  "It's a job site.");
        strings.put("story.exchange.galleries.arrival.work.reply",
                "A job site with drainage. Sure.");
        strings.put("story.exchange.galleries.arrival.wrong", "It lands wrong.");
        strings.put("story.exchange.galleries.arrival.wrong.reply",
                "Thank you. I thought it was a fault in me.");
        strings.put("story.exchange.galleries.arrival.what",  "What were they cutting?");
        strings.put("story.exchange.galleries.arrival.what.reply",
                "Tissue. The manifests all say tissue. None say whose.");
    }

    /** Region 2 — the Organization, corrective: the whispers are real and you are the fault. */
    private static void registerGalleriesDemand(StoryStrings strings) {
        strings.put("story.exchange.galleries.demand.prompt",
                "Operator. Your feed carries auditory artifacts. They originate in you. Acknowledge.");
        strings.put("story.exchange.galleries.demand.acknowledge", "Acknowledged.");
        strings.put("story.exchange.galleries.demand.acknowledge.reply",
                "Corrected. Continue the descent.");
        strings.put("story.exchange.galleries.demand.push",        "It doesn't sound like a fault.");
        strings.put("story.exchange.galleries.demand.push.reply",
                "It's on my sensors too. I didn't flag it. I won't.");
        strings.put("story.exchange.galleries.demand.silence",     "(say nothing)");
        strings.put("story.exchange.galleries.demand.silence.reply",
                "Non-response recorded. The fault is noted as ongoing.");
    }

    /** Region 3 — the planet's first real exchange. It knows you by deed, not by serial. */
    private static void registerReliquaryAddress(StoryStrings strings) {
        strings.put("story.exchange.reliquary.address.prompt",
                "there you are. new face. same weight.");
        strings.put("story.exchange.reliquary.address.who",  "Who are you?");
        strings.put("story.exchange.reliquary.address.who.reply",
                "the ground. the thing under the ground. the thing they weigh.");
        strings.put("story.exchange.reliquary.address.out",  "Stay out of my head.");
        strings.put("story.exchange.reliquary.address.out.reply",
                "i am not in your head. i am under your feet. i grieve anyway.");
        strings.put("story.exchange.reliquary.address.know", "...I know you.");
        strings.put("story.exchange.reliquary.address.know.reply",
                "yes. you did this. and you came back. both are true.");
    }

    /** Region 4 — the Organization, coercive: obey or there is no next body. */
    private static void registerWoundCoercion(StoryStrings strings) {
        strings.put("story.exchange.wound.coercion.prompt",
                "Operator. Restore the restraints or your pattern is written off. There is no next body.");
        strings.put("story.exchange.wound.coercion.comply", "Understood.");
        strings.put("story.exchange.wound.coercion.comply.reply",
                "Authorization held. Pending completion.");
        strings.put("story.exchange.wound.coercion.refuse", "Then write it off.");
        strings.put("story.exchange.wound.coercion.refuse.reply",
                "That's on the open channel. I'm leaving it there.");
        strings.put("story.exchange.wound.coercion.who",    "Who signs that?");
        strings.put("story.exchange.wound.coercion.who.reply",
                "Nobody signs it. It's a threshold. It just trips.");
    }

    /**
     * Region 5 — THE ENDING (story/06-endings.md).  A door with three ways out, each of which states
     * its price before it can be taken, and a way back from every one of them.  Nothing here is
     * labelled good or bad, nothing is recommended, and no answer is longer or louder than another.
     */
    private static void registerCoreEnding(StoryStrings strings) {
        strings.put("story.exchange.core.ending.prompt",
                "you remember it all now. the door you opened. my hand. so. choose.");
        strings.put("story.exchange.core.ending.unmake", "Break the drills.");
        strings.put("story.exchange.core.ending.obey",   "Put the restraints back.");
        strings.put("story.exchange.core.ending.merge",  "Stop being separate.");

        // FREE / KILL — the same decision about the harvest, differing only in whether it survives it.
        strings.put("story.exchange.core.ending.unmake.prompt",
                "Cut the drills and the reserve is gone. Nobody prints again. Not you, not me. Awake, or asleep?");
        strings.put("story.exchange.core.ending.unmake.free", "Let it wake.");
        strings.put("story.exchange.core.ending.unmake.free.reply",
                "oh. oh. go, then. be finished. thank you.");
        strings.put("story.exchange.core.ending.unmake.kill", "Let it rest.");
        strings.put("story.exchange.core.ending.unmake.kill.reply",
                "yes. quiet. i had forgotten quiet.");
        strings.put("story.exchange.core.ending.unmake.wait", "Not yet.");

        strings.put("story.exchange.core.ending.obey.prompt",
                "Restraints back on means it stays alive and awake for it. Forever. And you keep printing.");
        strings.put("story.exchange.core.ending.obey.commit", "Do it.");
        strings.put("story.exchange.core.ending.obey.commit.reply",
                "Compliance logged. Reprint priority raised.");
        strings.put("story.exchange.core.ending.obey.wait",   "Not yet.");

        strings.put("story.exchange.core.ending.merge.prompt",
                "if you come in there is no coming out. there is no you to come out. still?");
        strings.put("story.exchange.core.ending.merge.commit", "Let go.");
        strings.put("story.exchange.core.ending.merge.commit.reply",
                "be still. this is the only part that never hurt.");
        strings.put("story.exchange.core.ending.merge.wait",   "Not yet.");
    }

    /** Region 2 — ORA's first doubt, and the cache that pays for looking at it. */
    private static void registerGalleriesLedger(StoryStrings strings) {
        strings.put("story.exchange.galleries.ledger.prompt",
                "The yield sheets bother me. Something here was weighed by the tonne. Want it?");
        strings.put("story.exchange.galleries.ledger.read",  "Read it to me.");
        strings.put("story.exchange.galleries.ledger.read.reply",
                "Four tonnes a shift. And a supply code. Take the kit.");
        strings.put("story.exchange.galleries.ledger.later", "Not now.");
        strings.put("story.exchange.galleries.ledger.later.reply",
                "Sure. Filed under 'later'. With the rest.");
        strings.put("story.exchange.galleries.ledger.what",  "Does it say what it is?");
        strings.put("story.exchange.galleries.ledger.what.reply",
                "It says 'yield'. Twelve times. Not once what.");
    }

    /** Region 3 — the trust beat. She wrote it down, and it can get her wiped. */
    private static void registerReliquaryLog(StoryStrings strings) {
        strings.put("story.exchange.reliquary.log.prompt",
                "That terminal told me what the reserve is. It's in my log now. They read my log.");
        strings.put("story.exchange.reliquary.log.delete", "Delete it. Stay safe.");
        strings.put("story.exchange.reliquary.log.delete.reply",
                "Gone. It never happened. Thank you.");
        strings.put("story.exchange.reliquary.log.keep",   "Leave it. Let them read it.");
        strings.put("story.exchange.reliquary.log.keep.reply",
                "Then they'll know I know. All right.");
        strings.put("story.exchange.reliquary.log.ask",    "Say it out loud first.");
        strings.put("story.exchange.reliquary.log.ask.reply",
                "It's them. Every print of you burns a piece of one.");

        // The chained half: same decision, asked once the player has heard the answer.
        strings.put("story.exchange.reliquary.decide.prompt",
                "So. The log. Do I keep it, or does it never exist?");
        strings.put("story.exchange.reliquary.decide.delete", "Delete it.");
        strings.put("story.exchange.reliquary.decide.keep",   "Keep it.");
    }

    /** Region 4 — the planet, addressing the player. Denial is a real answer. */
    private static void registerWoundVoice(StoryStrings strings) {
        strings.put("story.exchange.wound.voice.prompt",
                "not a thing. me. you knew. you always knew.");
        strings.put("story.exchange.wound.voice.accept", "I know.");
        strings.put("story.exchange.wound.voice.accept.reply",
                "then stop. or come down. either.");
        strings.put("story.exchange.wound.voice.deny",   "You're not real.");
        strings.put("story.exchange.wound.voice.deny.reply",
                "It's on my sensors too. So. It's real.");
        strings.put("story.exchange.wound.voice.probe",  "Who were you?");
        strings.put("story.exchange.wound.voice.probe.reply",
                "before the drills. before your word for me.");
    }

    /** Region 5 — the last order, asked before the endings, decided by nobody but the player. */
    private static void registerCoreOrder(StoryStrings strings) {
        strings.put("story.exchange.core.order.prompt",
                "Operator. Final descent. Restraints are to be restored. Confirm.");
        strings.put("story.exchange.core.order.confirm", "Confirmed.");
        strings.put("story.exchange.core.order.confirm.reply",
                "Logged. Proceed to the core.");
        strings.put("story.exchange.core.order.refuse",  "No.");
        strings.put("story.exchange.core.order.refuse.reply",
                "Repeat your last, operator.");
        strings.put("story.exchange.core.order.probe",   "Who is asking?");
        strings.put("story.exchange.core.order.probe.reply",
                "Nobody. That channel's automated. For years.");
    }

    /** The deep-strata check-ins — one per leaning, plus the neutral one everybody can get. */
    private static void registerDeepStrata(StoryStrings strings) {
        strings.put("story.exchange.deep.warm.prompt",
                "Quiet stretch. Can I ask you something that isn't work?");
        strings.put("story.exchange.deep.warm.listen", "Go ahead.");
        strings.put("story.exchange.deep.warm.listen.reply",
                "Do you remember the last one? Before you. I do.");
        strings.put("story.exchange.deep.warm.work",   "Not now, ORA.");
        strings.put("story.exchange.deep.warm.work.reply",
                "Right. Work. Copy that.");

        strings.put("story.exchange.deep.duty.prompt",
                "Status check. Four floors deep and ahead of schedule.");
        strings.put("story.exchange.deep.duty.efficient", "Keep it that way.");
        strings.put("story.exchange.deep.duty.efficient.reply",
                "Noted. Efficiency logged.");
        strings.put("story.exchange.deep.duty.whose",     "Ahead of whose schedule?");
        strings.put("story.exchange.deep.duty.whose.reply",
                "Good question. Nobody ever set one.");

        strings.put("story.exchange.deep.quiet.prompt",
                "Two floors since anything moved. Not normal at this depth.");
        strings.put("story.exchange.deep.quiet.good",    "Good.");
        strings.put("story.exchange.deep.quiet.good.reply",
                "Sure. Good. I'll keep watching anyway.");
        strings.put("story.exchange.deep.quiet.waiting", "Something's waiting.");
        strings.put("story.exchange.deep.quiet.waiting.reply",
                "That was my read too. I didn't want to say it.");
    }
}
