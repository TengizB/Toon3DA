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
        registerTermIntros(strings);
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
     * THE VOCABULARY LADDER (narrative-rework order-3) — one line per {@link StoryTerm}, and the only
     * place in the game a proper noun of this fiction is allowed to arrive for the first time.
     *
     * <p>Each one performs the NAMED step: the word, attached to the plain description the player
     * already has, at a moment they can see the thing being named.  Two rules shape every line here
     * and both are load-bearing — <b>one new word per line</b> (so a panel never introduces two
     * unfamiliar things at once), and <b>no line explains a thing the player cannot see yet</b>.  A
     * naming line is not lore: it is the price of every jargon line that follows it.
     */
    private static void registerTermIntros(StoryStrings strings) {
        // Region 1 — the eight words the first hour runs on, in the order they are named.
        strings.put("story.term.reprint",
                                                     "The paperwork calls that a reprint. You're on your second.");
        strings.put("story.term.deepworks",
                                                     "This is the Deepworks. Forty years of digging, straight down.");
        strings.put("story.term.erebus",
                                                     "The rock it's dug into is Erebus. Survey named it in an afternoon.");
        strings.put("story.term.contaminant",
                                                     "They call it the contaminant. That's the word on the form, not a description.");
        strings.put("story.term.overseer",
                                                     "The voice on that channel is the Overseer. It's a desk, not a person.");
        strings.put("story.term.instance",
                                                     "They file each of you as an instance. That's what the number is.");
        strings.put("story.term.serial",
                                                     "Crew wear serials. So do you - check your cuff sometime.");
        strings.put("story.term.checkpoint",
                                                     "A checkpoint is the last stored read of you. They build from that.");

        // Below Region 1 — each word arrives with the region that makes it mean something.
        strings.put("story.term.yield",
                                                     "Yield is whatever these floors were cutting out and sending up.");
        strings.put("story.term.reserve",
                                                     "The reserve is the fuel. Metered, finite, and nobody says of what.");
        strings.put("story.term.cradle",
                                                     "The machines in the racks are Cradles. A body comes out of one.");
        strings.put("story.term.pattern",
                                                     "Your pattern is the stored you. The data. Not this body.");
        strings.put("story.term.wound",
                                                     "Nobody calls this part anything. The old notes say the Wound.");
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
        // The observation is the line and the dryness is only the delivery (order-6 C, doctrine D4):
        // the lunch order used to end on "Soup. Bold.", which was the whole content.
        strings.put("story.bark.log.rings.3",        "A lunch order, dated the last day. Nobody came to collect it.");
        strings.put("story.bark.log.rings.4",        "Sixty pages of safety notices. Four on ladders. None on the deep levels.");

        strings.put("story.bark.log.galleries.1",    "Extraction quotas. Someone missed theirs. Twice.");
        strings.put("story.bark.log.galleries.2",    "Tolerance sheet. It lists 'live weight'. Live.");
        strings.put("story.bark.log.galleries.4",    "Transport manifest. Down empty, up full. Daily.");
        // Replaces the cut "form for reporting screaming" row (order-6 B): the two rows above already
        // carried the "the paperwork knew" beat, and the codex entry says it better.
        strings.put("story.bark.log.galleries.5",    "Floor plan. Every gallery slopes to a drain. Every one.");
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
        // PLAIN before proper (order-6 C): this row shows the player the tag on their own cuff and
        // never says "serial" — the word itself belongs to the ladder's naming line, which fires the
        // first time something that used to be crew walks at them.
        strings.put("story.bark.floor.rings.3",      "Crew bunks. Tags still in the racks - same kind of tag you're wearing.");
        // "yield" is banded to the Harvesting Galleries by the order-3 ladder; up here the order is
        // still just an order — stated in full, because the brief is the one thing she can explain.
        strings.put("story.bark.floor.rings.4",      "Clear it, restart the works, go home. That's the whole brief.");

        // She has sensors, not a nose (order-6 C): the old row smelled the room and reached for a
        // modern idiom to do it, which is two rules broken in one line.
        strings.put("story.bark.floor.galleries.1",  "The air reads wrong in here. Organic, and a lot of it. Touch nothing.");
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
        // The cloning reveal used to live in this row's subordinate clause; order-2's cold open now
        // spends three lines on it, so the first region beat is free to do what every other region
        // beat does — say what this band of the facility WAS (order-6 C).
        strings.put("story.bark.region.rings",       "Habitation rings. People lived up here. The job's below them.");
        // Plain first: this row states what happened here, and the ladder's naming line (order-3)
        // supplies the word "yield" one panel later.
        strings.put("story.bark.region.galleries",   "Harvesting galleries. This is where the cutting happened.");
        strings.put("story.bark.region.reliquary",   "The Reliquary. This is where they print you. And me.");
        strings.put("story.bark.region.wound",       "The drills are still in it. It's still bleeding.");
        strings.put("story.bark.region.core",        "We're at the core. Whatever's next, I'm with you.");

        strings.put("story.bark.gate.rings",         "Operator. Go down. Purge the contaminant.");
        strings.put("story.bark.gate.galleries",     "There's noise in your head. It's a fault. Ignore it.");
        strings.put("story.bark.gate.reliquary",     "You've stopped answering. We own your next body.");
        strings.put("story.bark.gate.wound",         "Pattern flagged. Reprint authorization: pending.");
        strings.put("story.bark.gate.core",          "You're not an operator now. You're a loss.");
    }

    /**
     * THE BESTIARY VOICE (narrative-rework order-5) — the four kinds of row {@link BestiaryCatalog}
     * registers.  Every one of them does two jobs at once: it says what the thing IS, which is the
     * story ({@code story/00-premise.md} — the moral geometry of the bestiary is the whole premise),
     * and how to survive it, which is why the player reads it.
     *
     * <p>No line here states a NUMBER.  She says slow, thick, fast, armoured; the HUD owns numbers,
     * and a line that quotes one goes stale the next time the balance moves.
     */
    private static void registerFamilies(StoryStrings strings) {
        // The EARLY family line: name it, then say how it kills you. Both halves, one line.
        strings.put("story.bark.family.undead",
                                                     "Crew serials on those. They're slow - back up and let it walk into your shot.");
        strings.put("story.bark.family.insect",
                                                     "Grown here, out of the walls. They come in numbers - don't get surrounded.");
        strings.put("story.bark.family.machine",
                                                     "Site security, still on shift. Armoured from the front - make it turn.");
        strings.put("story.bark.family.aberration",
                                                     "Nothing on file matches that. It's slow and it hits hard - stay at range.");
        strings.put("story.bark.family.demon",
                                                     "Not crew, not machinery. It's fast - don't let it reach you first.");
        strings.put("story.bark.family.golem",
                                                     "Mineral, and moving. The walls are standing up - and they turn slowly.");

        // The REFRAME, from the Wound down. By Region 4 the player knows how to fight; what changes
        // is what they are told they have been fighting. Each row carries its family's column of the
        // premise's system-as-story table.
        strings.put("story.bark.family.undead.deep",
                                                     "That was crew. The conditions here killed them, not the thing below us.");
        strings.put("story.bark.family.insect.deep",
                                                     "Those were grown out of this place, to be cut up. We made them.");
        strings.put("story.bark.family.machine.deep",
                                                     "That one is ours. Company security, still guarding a job nobody finished.");
        strings.put("story.bark.family.aberration.deep",
                                                     "Still nothing on file. I've stopped assuming that means it isn't ours.");
        strings.put("story.bark.family.demon.deep",
                                                     "That wasn't an enemy. That was a body defending itself.");
        strings.put("story.bark.family.golem.deep",
                                                     "The rock is standing up to stop us. I'd call that fair.");

        registerArchetypes(strings);
        registerAbilities(strings);
        registerBossBeats(strings);
    }

    /**
     * One line per enemy archetype, said the first time the player sees that TYPE — ORA's
     * translation of its {@code EnemyType.tacticalVerb()}, which is the designer sentence and stays
     * the source of truth for what each of these must convey.
     *
     * <p>The rule that shapes all twenty: a line names ONE behaviour.  Where an archetype does two
     * things she says the one that kills players, and the rest is the archive's problem.
     */
    private static void registerArchetypes(StoryStrings strings) {
        strings.put("story.bark.enemy.plague_hulk",
                                                     "Big one. Don't trade punches - shoot it while it's walking.");
        strings.put("story.bark.enemy.eye_tyrant",
                                                     "It only fires straight down a row or a column. Step off its line.");
        strings.put("story.bark.enemy.gore_biter",
                                                     "They come as a pack. Find a doorway and make them queue.");
        strings.put("story.bark.enemy.shell_brute",
                                                     "It charges in a straight line. Step aside, then hit it while it recovers.");
        strings.put("story.bark.enemy.mire_wraith",
                                                     "The acid stacks. Kill it first, argue later.");
        strings.put("story.bark.enemy.iron_stalker",
                                                     "That's a step up. Spend something on it or walk around it.");
        strings.put("story.bark.enemy.acid_drone",
                                                     "It keeps backing off. Corner it - it can't kite through a wall.");
        strings.put("story.bark.enemy.void_shroud",
                                                     "It goes for your back. Turn and face it before it gets there.");
        strings.put("story.bark.enemy.ghoul",
                                                     "Slow, and it never stops. Keep walking and never let it corner you.");
        strings.put("story.bark.enemy.crawler",
                                                     "Fast, thin. Drop it now, before the rest arrive.");
        strings.put("story.bark.enemy.revenant",
                                                     "Fast and heavy. Do not let that one reach you.");
        strings.put("story.bark.enemy.vortex_eye",
                                                     "Short range only. Break its line or get right on top of it.");
        strings.put("story.bark.enemy.blight_corruptor",
                                                     "Thick. Wear it down from range; don't stand in front of it.");
        strings.put("story.bark.enemy.auric_sentinel",
                                                     "Those shards are its armour and its ammo. Strip them fast.");
        strings.put("story.bark.enemy.cinderforge_colossus",
                                                     "It re-hardens the second you stop shooting. Don't stop shooting.");
        strings.put("story.bark.enemy.rimeshell_lancer",
                                                     "Armoured until it fires. Bait the lance, step off the lane, then hit it.");
        strings.put("story.bark.enemy.verdant_spiresower",
                                                     "The spires heal it. Break those first or it never dies.");
        strings.put("story.bark.enemy.overseer",
                                                     "It telegraphs everything. Watch one full cycle before you commit.");
        strings.put("story.bark.enemy.corruptor",
                                                     "It tells you what's coming before it does it. Learn that, then punish it.");
        // "Patterns" was the behaviour kind, but "pattern" is also the stored-you, which the ladder
        // bands to the Reliquary — and this row is region-unrestricted, so it said a Region-3 word on
        // floor one.  The audit cannot tell two senses of a word apart, and it should not have to.
        strings.put("story.bark.enemy.hell_baron",
                                                     "Timing, not reflexes. Read the mark, then move.");
    }

    /**
     * One line per special ability, said the first time the player watches one RESOLVE.  Five
     * abilities ship and the game has never explained any of them; each of these is a rule players
     * otherwise learn by dying, which is why all five are mandatory beats.
     */
    private static void registerAbilities(StoryStrings strings) {
        strings.put("story.bark.ability.self_destruct",
                                                     "It's counting down to blow. Get two tiles clear or kill it now.");
        strings.put("story.bark.ability.summon",
                                                     "It's calling more in. Kill the caller or you'll be here all day.");
        strings.put("story.bark.ability.area_strike",
                                                     "That hits the whole cross, not just your tile. Get off the line.");
        strings.put("story.bark.ability.buff_self",
                                                     "It just made itself harder. Whatever you were doing, do it faster.");
        strings.put("story.bark.ability.debuff_player",
                                                     "That did something to you, not to your health. Check the bar.");
    }

    /**
     * The first boss: one line on the way in, and one on the way out.  The second is the REVEAL —
     * the room is on the plans, which means somebody drew it, approved it, and kept the shifts
     * walking past it.
     */
    private static void registerBossBeats(StoryStrings strings) {
        strings.put("story.bark.boss.first",
                                                     "This one's on the plans. They built the room around it.");
        strings.put("story.bark.boss.first.dead",
                                                     "They knew it was here. They built a door for it and sent shifts past anyway.");
    }

    /** Kills — ORA's confidence rotting across the descent.  Rare, and rarely a joke. */
    private static void registerKills(StoryStrings strings) {
        strings.put("story.bark.kill.rings.1",       "See? Contaminant. Nothing violence can't purge.");
        // This row EARNS its jargon: the line is ABOUT the vocabulary somebody chose, which is the
        // one way a word is allowed to be used before the player is comfortable with it.
        strings.put("story.bark.kill.rings.3",       "Logged as hostile stock. That's the word they use.");
        strings.put("story.bark.kill.rings.4",       "One down. I'd cheer, but I'm a professional.");
        // Replaces the cut "Textbook. Efficient." row (order-6 B), which said nothing under its joke.
        strings.put("story.bark.kill.rings.5",       "Down. I'd feel better if I knew what it used to be.");

        strings.put("story.bark.kill.galleries.1",   "It screamed on the same note as the room. That's twice now.");
        strings.put("story.bark.kill.galleries.2",   "Specimen neutralised. That word is working hard.");
        strings.put("story.bark.kill.galleries.3",   "It stopped screaming before it stopped moving.");
        // Replaces the cut "let's pretend nothing else heard it" row (order-6 B), which described a
        // noise-aggro rule the game does not have.  A line that states a rule that is not real is
        // worse than no line: the player plays around it and is punished for listening.
        strings.put("story.bark.kill.galleries.5",   "Harvest tag on its leg. It was inventory before it was a threat.");

        strings.put("story.bark.kill.reliquary.1",   "It stopped moving. I keep waiting to feel better.");
        strings.put("story.bark.kill.reliquary.2",   "That one had a serial. I won't read it out.");
        strings.put("story.bark.kill.reliquary.3",   "A failed print. They made it, then left it in here.");
        strings.put("story.bark.kill.reliquary.4",   "It came out of a Cradle. The same way you do.");

        // This row used to be word-for-word story.bark.family.demon.deep (D5: no two shipped rows say
        // the same thing).  The one-shot REVEAL keeps that sentence; this repeatable row now carries the
        // deliberate echo of kill.rings.3 instead — the word she used up top, refused down here.
        strings.put("story.bark.kill.wound.1",       "I logged that as a kill. It wasn't a hostile.");
        strings.put("story.bark.kill.wound.2",       "I'm not calling that a purge. Not anymore.");
        strings.put("story.bark.kill.wound.3",       "It only came at you because we are the wound.");
        strings.put("story.bark.kill.wound.4",       "Immune response. That's all any of them ever were.");
    }

    /** Low health — the one moment ORA is allowed to sound frightened. */
    private static void registerLowHealth(StoryStrings strings) {
        strings.put("story.bark.lowhealth.rings.1",  "Ten percent and still walking at it. Bold. Dumb.");
        // Was "the Cradle bill for this is mine" — a Region-3 word, two regions early (failure F6).
        strings.put("story.bark.lowhealth.rings.2",  "You're leaking. Patch that before I have to log it.");
        strings.put("story.bark.lowhealth.mid.1",    "Careful. Please. I don't want to count you again.");
        // Banded to the Reliquary and below by order-6 C: "I know what now" is only true AFTER the
        // cradle log, so in the Galleries it claimed knowledge she has not been given yet.
        strings.put("story.bark.lowhealth.mid.2",    "Every reprint costs something. I know what now.");
        // ...which leaves the Galleries a row short, so this one holds the band's depth.  A low-health
        // line that tells the player what to DO is the best kind of low-health line.
        strings.put("story.bark.lowhealth.mid.3",    "Back off and patch yourself up. I'll watch the corridor.");
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
        strings.put("story.bark.idle.rings.1",       "Take your time. Whatever's down there has waited longer than us.");
        strings.put("story.bark.idle.rings.2",       "I can wait. I'm contractually excellent at waiting.");
        strings.put("story.bark.idle.mid.1",         "Standing still doesn't make it quieter. I checked.");
        // Replaces the cut "the resonance stops when you stop" row (order-6 B), which described a
        // phenomenon the player has no way to observe.  This one rides a rule that IS real: the world
        // is turn-based, so nothing moves while the player does not.
        strings.put("story.bark.idle.mid.3",         "You've stopped. Fine by me. It's quieter when you're still.");
        strings.put("story.bark.idle.deep.1",        "I don't like it when you stop moving down here.");
        strings.put("story.bark.idle.deep.2",        "It's waiting too. That's the part I don't like.");

        strings.put("story.bark.backtrack.rings.1",  "We've walked this one. I made a note. Twice now.");
        strings.put("story.bark.backtrack.rings.2",  "Lost? Say nothing. I'll log it as a patrol route.");
        strings.put("story.bark.backtrack.mid.1",    "Same corridor. Feels more occupied than last time.");
        strings.put("story.bark.backtrack.mid.2",    "You cleared this. It doesn't feel cleared, does it.");
        strings.put("story.bark.backtrack.deep.1",   "You keep circling. So does it.");
        // Replaces the cut "it moves the rooms" row (order-6 B) — the rooms do not move.  What IS
        // true of a backtracking player this deep is that the route only ever runs one way.
        strings.put("story.bark.backtrack.deep.3",   "Circling. If you're looking for a way up, there isn't one.");
    }
}
