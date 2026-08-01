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
 * <p>Voice sources: {@code story/dialog/ai-assistant.md}, {@code planet-voice.md},
 * {@code organization-comms.md}.  Tone shifts across the five regions are expressed purely by WHICH
 * region band a line is registered in ({@link BarkCatalog}) — ORA parrots "contaminant" in the
 * Habitation Rings and refuses the word by the Wound; the planet has no lines at all in Region 1.
 */
public final class BarkStrings {

    private BarkStrings() {}

    /** Adds every order-2 bark line to {@code strings}.  Returns the same table for chaining. */
    public static StoryStrings registerDefaults(StoryStrings strings) {

        // --- Floor arrival: ORA on the room you just walked into (region-gated tone) -----------
        strings.put("story.bark.floor.rings.1",      "Objective's down. Everything good is always down.");
        strings.put("story.bark.floor.rings.2",      "Clean sweep, then home. Purge the contaminant.");
        strings.put("story.bark.floor.galleries.1",  "Okay, the smell in here is a war crime. Touch nothing.");
        strings.put("story.bark.floor.galleries.2",  "They were cutting something out of these walls.");
        strings.put("story.bark.floor.reliquary.1",  "This is where they bring me back. I thought that was nice.");
        strings.put("story.bark.floor.reliquary.2",  "Cradle banks below. Try not to look at the racks.");
        strings.put("story.bark.floor.wound.1",      "This isn't facility anymore. This is tissue.");
        strings.put("story.bark.floor.wound.2",      "I'm not going to call it a contaminant. I can't.");
        strings.put("story.bark.floor.core.1",       "Whatever you choose down here, I'm still here.");

        // --- Region entry: ORA marks the new stratum (one-shot, mandatory beat) ----------------
        strings.put("story.bark.region.rings",       "New body, same job. Down we go. I'll keep count.");
        strings.put("story.bark.region.galleries",   "Harvesting galleries. The yield is measured by weight.");
        strings.put("story.bark.region.reliquary",   "The Reliquary. This is where they print you. And me.");
        strings.put("story.bark.region.wound",       "The drills are still in it. It's still bleeding.");
        strings.put("story.bark.region.core",        "We're at the core. Whatever's next, I'm with you.");

        // --- Region gate: the Organization's cold order (one-shot, rare, ALL-CAPS at delivery) --
        strings.put("story.bark.gate.rings",         "Operator. Go down. Purge the contaminant.");
        strings.put("story.bark.gate.galleries",     "There's noise in your head. It's a fault. Ignore it.");
        strings.put("story.bark.gate.reliquary",     "You've stopped answering. We own your next body.");
        strings.put("story.bark.gate.wound",         "Pattern flagged. Reprint authorization: pending.");
        strings.put("story.bark.gate.core",          "You're not an operator now. You're a loss.");

        // --- First sight of an enemy family (one-shot per family) -------------------------------
        strings.put("story.bark.family.undead",      "Those are crew serials. Old ones. Filing error.");
        strings.put("story.bark.family.insect",      "Specimens. They scream on the same note as the room.");
        strings.put("story.bark.family.machine",     "Salvage drones. They still think they're working.");
        strings.put("story.bark.family.aberration",  "Nothing on file matches that. Nothing at all.");
        strings.put("story.bark.family.demon",       "That's not crew and it isn't machinery. Keep back.");
        strings.put("story.bark.family.golem",       "Mineral. It's mineral, and it's walking at you.");

        // --- Kills: occasional, rate-limited, tone tracks the descent ---------------------------
        strings.put("story.bark.kill.rings.1",       "See? Contaminant. Nothing violence can't purge.");
        strings.put("story.bark.kill.rings.2",       "Textbook. Efficient. The yield thanks you.");
        strings.put("story.bark.kill.galleries.1",   "It screamed on the room's note. ...Just me? Great.");
        strings.put("story.bark.kill.galleries.2",   "Specimen neutralised. That word is working hard.");
        strings.put("story.bark.kill.reliquary.1",   "It stopped moving. I keep waiting to feel better.");
        strings.put("story.bark.kill.reliquary.2",   "That one had a serial. I won't read it out.");
        strings.put("story.bark.kill.wound.1",       "That wasn't an enemy. That was a body defending itself.");
        strings.put("story.bark.kill.wound.2",       "I'm not calling that a purge. Not anymore.");

        // --- Low health ------------------------------------------------------------------------
        strings.put("story.bark.lowhealth.rings",    "Ten percent and still walking at it. Bold. Dumb.");
        strings.put("story.bark.lowhealth.mid",      "Careful. Please. I don't want to count you again.");
        strings.put("story.bark.lowhealth.deep",     "Stop. Please stop. I can't watch them print you again.");

        // --- Deep strata: the planet, silent in Region 1, growing with the descent --------------
        strings.put("story.bark.strata.galleries.1", "...down...");
        strings.put("story.bark.strata.galleries.2", "...again...");
        strings.put("story.bark.strata.galleries.3", "...you smell the same...");
        strings.put("story.bark.strata.galleries.4", "...I know this weight...");
        strings.put("story.bark.strata.reliquary.1", "There you are. New face. Same weight.");
        strings.put("story.bark.strata.reliquary.2", "You know what they burn to wake you? Me.");
        strings.put("story.bark.strata.reliquary.3", "A piece of me. Every time you die.");
        strings.put("story.bark.strata.wound.1",     "You heard me screaming. No one else did.");
        strings.put("story.bark.strata.wound.2",     "I could not tell your hands from theirs.");
        strings.put("story.bark.strata.wound.3",     "They don't want me dead. Dead, I bleed nothing.");

        // --- Idle / backtracking: lowest-priority flavour ---------------------------------------
        strings.put("story.bark.idle.rings",         "Take your time. The contaminant isn't going anywhere.");
        strings.put("story.bark.idle.mid",           "Standing still doesn't make it quieter. I checked.");
        strings.put("story.bark.idle.deep",          "I don't like it when you stop moving down here.");
        strings.put("story.bark.backtrack.rings",    "We've walked this one. I made a note. Twice now.");
        strings.put("story.bark.backtrack.mid",      "Same corridor. Feels more occupied than last time.");
        strings.put("story.bark.backtrack.deep",     "You keep circling. So does it.");

        return strings;
    }
}
