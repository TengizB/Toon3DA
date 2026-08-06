package ge.tbegvadze.toon3d.narrative;

/**
 * The order-2 BARK text table — the built-in fallback for every line {@link BarkCatalog} registers.
 * Kept out of {@link StoryStrings#defaults()} so that method stays readable; {@code defaults()}
 * calls {@link #registerDefaults(StoryStrings)} to fold this table in.
 *
 * <p>Localisation rule (order-7 Part C): this is the ONLY place bark text lives in Java, and it is a
 * fallback — the shipped table is {@code assets/story/story-strings.properties}, which the render
 * layer loads at startup ({@code render/StoryStringsLoader}).  KEEP THE TWO IN SYNC: a test asserts
 * every catalog row resolves here, and that every line still wraps inside the two-line bark cap.
 *
 * <h3>What these lines are for</h3>
 * The bark channel carries ~80% of the story, so most lines TELL something — what this place was,
 * what the Organization did here, what ORA is working out, what the planet is.  A minority are her
 * dry asides.  The split is declared per row as a {@link BarkTone} in {@link BarkCatalog}, and the
 * pools are deliberately deep so a moment that fires often never repeats itself.
 *
 * <p>Voice sources: {@code story/dialog/ai-assistant.md}, {@code planet-voice.md},
 * {@code organization-comms.md}.  Tone shifts across the five regions are expressed purely by WHICH
 * region band a line is registered in — ORA parrots "contaminant" in the Habitation Rings and
 * refuses the word by the Wound.
 */
public final class BarkStrings {

    private BarkStrings() {}

    /** Adds every order-2 bark line to {@code strings}.  Returns the same table for chaining. */
    public static StoryStrings registerDefaults(StoryStrings strings) {
        registerIntroBeats(strings);
        registerControlHints(strings);
        registerLogTakes(strings);
        registerFloorArrival(strings);
        registerRegionAndGate(strings);
        registerFamilies(strings);
        registerKills(strings);
        registerLowHealth(strings);
        registerPlanet(strings);
        registerIdleAndBacktrack(strings);
        registerCodexCompletion(strings);
        return strings;
    }

    /**
     * Codex completion (order-6) — ORA noticing that the player filled a shelf of the archive.  The
     * whole reward, and deliberately so: warm, one line, never a fanfare and never a bonus.
     */
    private static void registerCodexCompletion(StoryStrings strings) {
        strings.put("story.bark.codex.complete.logs",
                                                     "You read all the paperwork. Every page. Wow.");
        strings.put("story.bark.codex.complete.people",
                                                     "You know all their names now. Somebody should.");
        strings.put("story.bark.codex.complete.planet",
                                                     "That's everything it's managed to tell us.");
        strings.put("story.bark.codex.complete.organization",
                                                     "The whole file on them. It's worse assembled.");
        strings.put("story.bark.codex.complete.ora",
                                                     "You looked me up. All of it. Thank you, actually.");
        strings.put("story.bark.codex.complete.memories",
                                                     "That's all of you I could get back. It's enough.");
    }

    /**
     * ORA'S INTRODUCTION (narrative-rework order-2 D) — one line per {@link IntroBeat}, and between
     * them the whole answer to "who is this person talking to me".  A name is not an introduction:
     * these say what she IS, what happened to the player, why their head is empty, and what she does
     * — spread across the first two runs and the moments of floor one, never front-loaded.
     */
    private static void registerIntroBeats(StoryStrings strings) {
        // Run 1, in this order: who is talking (and WHAT she is), what happened to the player, and
        // the gap in their head — the sentence that makes every explanation she gives afterwards
        // something the character needs rather than something the game is telling the player.
        strings.put("story.bark.intro.greeting",
                                                     "That's you awake. I'm ORA - the assistant in your suit.");
        strings.put("story.bark.intro.what_happened",
                                                     "You died about an hour ago. They keep a copy. This is the new you.");
        strings.put("story.bark.intro.the_gap",
                                                     "Your memory came back short. Normal. I'll fill in the gaps as we go.");

        // Run 2 — the pair that makes her matter, and the last time she introduces herself at all.
        strings.put("story.bark.intro.continuity",
                                                     "Back already. I don't get printed, by the way - I just reload.");
        strings.put("story.bark.intro.memory",
                                                     "Which makes me the only one here who remembers your last one.");

        // The distributed half: each one hangs off a thing that just happened in front of the
        // player, so it reads as her noticing rather than as a lecture arriving on a schedule.
        strings.put("story.bark.intro.doors",
                                                     "I can do doors and locks. Guns are your department.");
        strings.put("story.bark.intro.carried_items",
                                                     "Anything you carry is yours until you die. Then it stays here and you don't.");
        strings.put("story.bark.intro.reprint_counter",
                                                     "That number on the wake card is which print you are. I keep the count.");
        strings.put("story.bark.intro.paperwork",
                                                     "I read the paperwork so you don't have to. Roster: forty-one out, nobody back.");
    }

    /**
     * The whole tutorial (order-5), one line per control, said the first time it is needed.  Each
     * must NAME the control plainly enough to act on and still sound like her — a line that reads as
     * a manual entry has spent the character to say something a diagram could have said.
     */
    private static void registerControlHints(StoryStrings strings) {
        strings.put("story.bark.control.move",       "Tap forward to step. Everything good is down.");
        strings.put("story.bark.control.fire",       "It's awake. Fire button, bottom right. Go on.");
        strings.put("story.bark.control.reload",     "You're dry. Reload before it notices, ideally.");
        strings.put("story.bark.control.heal",       "You're carrying a medkit. Heal. Now would be good.");
        strings.put("story.bark.control.switch_weapon",
                                                     "Two guns now. Switch weapon cycles them.");
        strings.put("story.bark.control.inventory",  "Open the bag when you get a second. It fills up.");
    }

    /**
     * Log takes (order-5) — ORA's one-line reaction to a terminal the player walked up to.  This is
     * the channel that carries the facility's paperwork to a player who will never open a codex, so
     * every line is a fact about what was DONE here, not a mood.
     */
    private static void registerLogTakes(StoryStrings strings) {
        // The roster itself moved to the one-shot intro beat above (order-2 D), which is the first
        // log the player ever reads.  Say it once (doctrine D5): this row is now a different note.
        strings.put("story.bark.log.rings.1",        "Shift notes. Someone's handwriting stops mid-word.");
        strings.put("story.bark.log.rings.2",        "Maintenance log. Complaints about noise below.");
        strings.put("story.bark.log.rings.3",        "A lunch order, dated the last day. Soup. Bold.");
        strings.put("story.bark.log.rings.4",        "Safety notice, sixty pages. None about the deep.");

        strings.put("story.bark.log.galleries.1",    "Extraction quotas. Someone missed theirs. Twice.");
        strings.put("story.bark.log.galleries.2",    "Tolerance sheet. It lists 'live weight'. Live.");
        strings.put("story.bark.log.galleries.3",    "A form for reporting screaming. Box was ticked.");
        strings.put("story.bark.log.galleries.4",    "Transport manifest. Down empty, up full. Daily.");
        strings.put("story.bark.log.galleries.yield",
                                                     "Yield report. They're mining something that heals.");

        strings.put("story.bark.log.reliquary.1",    "Cradle service log. Your name is in the column.");
        strings.put("story.bark.log.reliquary.2",    "Pattern integrity report. Mine's flagged. Lovely.");
        strings.put("story.bark.log.reliquary.3",    "Fuel intake sheet. Nobody wrote where it's from.");
        strings.put("story.bark.log.reliquary.4",    "An operator's note. The handwriting is yours.");
        strings.put("story.bark.log.reliquary.cradle",
                                                     "The 'reserve' is the planet. They burn it to make you.");

        strings.put("story.bark.log.wound.1",        "Drill maintenance. Depth in metres. Into what.");
        strings.put("story.bark.log.wound.2",        "A field note. It says 'the subject is awake'.");
        strings.put("story.bark.log.wound.3",        "Someone stopped writing halfway through a word.");
        strings.put("story.bark.log.wound.4",        "Sedation schedule. Hourly. For forty years.");

        strings.put("story.bark.log.core.1",         "Nothing is filed down here. Only its memory.");
        strings.put("story.bark.log.core.2",         "The last log is a resignation. Never submitted.");
        strings.put("story.bark.log.core.3",         "Someone wrote 'I'm sorry' and left it running.");
        strings.put("story.bark.log.core.4",         "A checklist for re-chaining. Step one is you.");
    }

    /** Arriving on a new floor — ORA reads the room.  Mostly what this place WAS. */
    private static void registerFloorArrival(StoryStrings strings) {
        strings.put("story.bark.floor.rings.1",      "Objective's down. Everything good is always down.");
        strings.put("story.bark.floor.rings.2",      "Evacuation notices, still fresh. They left mid-shift.");
        strings.put("story.bark.floor.rings.3",      "Crew bunks. Serial tags on them, same format as yours.");
        strings.put("story.bark.floor.rings.4",      "Clean sweep, then home. Purge it, restore the yield.");
        strings.put("story.bark.floor.rings.5",      "Air's breathable down here. You're welcome, I suppose.");

        strings.put("story.bark.floor.galleries.1",  "Okay, the smell in here is a war crime. Touch nothing.");
        strings.put("story.bark.floor.galleries.2",  "They were cutting something out of these walls. By weight.");
        strings.put("story.bark.floor.galleries.3",  "Extraction tolerances on the door. This was a factory.");
        strings.put("story.bark.floor.galleries.4",  "The specimens never got in here. They were grown in here.");
        strings.put("story.bark.floor.galleries.5",  "Four safety notices. None of them mention the screaming.");

        strings.put("story.bark.floor.reliquary.1",  "This is where they bring me back. I thought that was nice.");
        strings.put("story.bark.floor.reliquary.2",  "Cradle banks below. Try not to look at the racks.");
        strings.put("story.bark.floor.reliquary.3",  "Pattern archives. Your file is in here. Mine is too.");
        strings.put("story.bark.floor.reliquary.4",  "This is a refinery. They render the reserve into fuel.");
        strings.put("story.bark.floor.reliquary.5",  "I'd make a joke here. I've got nothing. Give me a minute.");

        strings.put("story.bark.floor.wound.1",      "This isn't facility anymore. This is tissue.");
        strings.put("story.bark.floor.wound.2",      "I'm not going to call it a contaminant. I can't.");
        strings.put("story.bark.floor.wound.3",      "The drills are still in it. They never stopped drilling.");
        strings.put("story.bark.floor.wound.4",      "It's warm in here. That isn't machinery. That's a body.");

        strings.put("story.bark.floor.core.1",       "Whatever you choose down here, I'm still here.");
        strings.put("story.bark.floor.core.2",       "No more layers after this one. Just it, and us.");
    }

    /** Region entry (ORA) and the Organization's cold order at each gate.  All one-shot beats. */
    private static void registerRegionAndGate(StoryStrings strings) {
        strings.put("story.bark.region.rings",       "New body, same job. Down we go. I'll keep count.");
        strings.put("story.bark.region.galleries",   "Harvesting galleries. The yield is measured by weight.");
        strings.put("story.bark.region.reliquary",   "The Reliquary. This is where they print you. And me.");
        strings.put("story.bark.region.wound",       "The drills are still in it. It's still bleeding.");
        strings.put("story.bark.region.core",        "We're at the core. Whatever's next, I'm with you.");

        strings.put("story.bark.gate.rings",         "Operator. Go down. Purge the contaminant.");
        strings.put("story.bark.gate.galleries",     "There's noise in your head. It's a fault. Ignore it.");
        strings.put("story.bark.gate.reliquary",     "You've stopped answering. We own your next body.");
        strings.put("story.bark.gate.wound",         "Pattern flagged. Reprint authorization: pending.");
        strings.put("story.bark.gate.core",          "You're not an operator now. You're a loss.");
    }

    /** First sight of an enemy family — one-shot, and each one tells you what the thing IS. */
    private static void registerFamilies(StoryStrings strings) {
        strings.put("story.bark.family.undead",      "Those are crew serials. Old ones. Filing error, surely.");
        strings.put("story.bark.family.insect",      "Specimens. They scream on the same note as the room.");
        strings.put("story.bark.family.machine",     "Salvage drones. They still think they're working.");
        strings.put("story.bark.family.aberration",  "Nothing on file matches that. Nothing at all.");
        strings.put("story.bark.family.demon",       "That's not crew and it isn't machinery. Keep back.");
        strings.put("story.bark.family.golem",       "Mineral, and moving. The walls are standing up now.");
    }

    /** Kills — ORA's confidence rotting across the descent.  Rare, and rarely a joke. */
    private static void registerKills(StoryStrings strings) {
        strings.put("story.bark.kill.rings.1",       "See? Contaminant. Nothing violence can't purge.");
        strings.put("story.bark.kill.rings.2",       "Textbook. Efficient. The yield thanks you.");
        strings.put("story.bark.kill.rings.3",       "Logged as hostile stock. That's the word they use.");
        strings.put("story.bark.kill.rings.4",       "One down. I'd cheer, but I'm a professional.");

        strings.put("story.bark.kill.galleries.1",   "It screamed on the room's note. ...Just me? Great.");
        strings.put("story.bark.kill.galleries.2",   "Specimen neutralised. That word is working hard.");
        strings.put("story.bark.kill.galleries.3",   "There's a harvest tag on it. It was inventory once.");
        strings.put("story.bark.kill.galleries.4",   "That was loud. Let's pretend nothing else heard it.");

        strings.put("story.bark.kill.reliquary.1",   "It stopped moving. I keep waiting to feel better.");
        strings.put("story.bark.kill.reliquary.2",   "That one had a serial. I won't read it out.");
        strings.put("story.bark.kill.reliquary.3",   "A failed print. They made it, then left it in here.");
        strings.put("story.bark.kill.reliquary.4",   "It came out of a Cradle. The same way you do.");

        strings.put("story.bark.kill.wound.1",       "That wasn't an enemy. That was a body defending itself.");
        strings.put("story.bark.kill.wound.2",       "I'm not calling that a purge. Not anymore.");
        strings.put("story.bark.kill.wound.3",       "It only came at you because we are the wound.");
        strings.put("story.bark.kill.wound.4",       "Immune response. That's all any of them ever were.");
    }

    /** Low health — the one moment ORA is allowed to sound frightened. */
    private static void registerLowHealth(StoryStrings strings) {
        strings.put("story.bark.lowhealth.rings.1",  "Ten percent and still walking at it. Bold. Dumb.");
        strings.put("story.bark.lowhealth.rings.2",  "You're leaking. The Cradle bill for this is mine.");
        strings.put("story.bark.lowhealth.mid.1",    "Careful. Please. I don't want to count you again.");
        strings.put("story.bark.lowhealth.mid.2",    "Every reprint costs something. I know what now.");
        strings.put("story.bark.lowhealth.deep.1",   "Stop. Please stop. I can't watch them print you again.");
        strings.put("story.bark.lowhealth.deep.2",   "If you fall here, it pays for you. Don't make it.");
    }

    /** Deep strata: the planet.  Silent in Region 1 — that absence is the design. */
    private static void registerPlanet(StoryStrings strings) {
        strings.put("story.bark.strata.galleries.1", "...down...");
        strings.put("story.bark.strata.galleries.2", "...again...");
        strings.put("story.bark.strata.galleries.3", "...you smell the same...");
        strings.put("story.bark.strata.galleries.4", "...I know this weight...");
        strings.put("story.bark.strata.reliquary.1", "There you are. New face. Same weight.");
        strings.put("story.bark.strata.reliquary.2", "You know what they burn to wake you? Me.");
        strings.put("story.bark.strata.reliquary.3", "A piece of me. Every time you die.");
        strings.put("story.bark.strata.reliquary.4", "You are not from the surface. You worked here.");
        strings.put("story.bark.strata.wound.1",     "You heard me screaming. No one else did.");
        strings.put("story.bark.strata.wound.2",     "I could not tell your hands from theirs.");
        strings.put("story.bark.strata.wound.3",     "They don't want me dead. Dead, I bleed nothing.");
        strings.put("story.bark.strata.wound.4",     "They took part of you too. I kept it.");
    }

    /** Idle and backtracking — the only pools that are mostly levity, and the rarest of all. */
    private static void registerIdleAndBacktrack(StoryStrings strings) {
        strings.put("story.bark.idle.rings.1",       "Take your time. The contaminant isn't going anywhere.");
        strings.put("story.bark.idle.rings.2",       "I can wait. I'm contractually excellent at waiting.");
        strings.put("story.bark.idle.mid.1",         "Standing still doesn't make it quieter. I checked.");
        strings.put("story.bark.idle.mid.2",         "The resonance stops when you stop. Did you notice?");
        strings.put("story.bark.idle.deep.1",        "I don't like it when you stop moving down here.");
        strings.put("story.bark.idle.deep.2",        "It's waiting too. That's the part I don't like.");

        strings.put("story.bark.backtrack.rings.1",  "We've walked this one. I made a note. Twice now.");
        strings.put("story.bark.backtrack.rings.2",  "Lost? Say nothing. I'll log it as a patrol route.");
        strings.put("story.bark.backtrack.mid.1",    "Same corridor. Feels more occupied than last time.");
        strings.put("story.bark.backtrack.mid.2",    "You cleared this. It doesn't feel cleared, does it.");
        strings.put("story.bark.backtrack.deep.1",   "You keep circling. So does it.");
        strings.put("story.bark.backtrack.deep.2",   "It moves the rooms. I'm fairly sure it moves the rooms.");
    }
}
