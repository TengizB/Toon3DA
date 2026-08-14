package ge.tbegvadze.toon3d.narrative;

/**
 * The order-6 CODEX text table - the built-in fallback for every entry {@link CodexCatalog}
 * registers, plus the codex screen's own chrome labels.  Kept out of {@link StoryStrings#defaults()}
 * so that method stays readable; {@code defaults()} calls {@link #registerDefaults(StoryStrings)} to
 * fold this table in.
 *
 * <p>Localisation rule (order-7 Part C): this is the ONLY place codex text lives in Java, and it is
 * a fallback - the shipped table is {@code assets/story/story-strings.properties}, which the render
 * layer loads at startup.  KEEP THE TWO IN SYNC: a test asserts every catalog entry resolves in both.
 *
 * <h3>Why the text here is long, when nothing else in the game is</h3>
 * The codex is the one screen where long-form writing is allowed, and that is the whole deal order-6
 * strikes: barks and exchanges stay one line each BECAUSE this exists for the players who want more.
 * Nothing here is required - every entry is the long version of something that already landed on an
 * unskippable channel, so a player who never opens the archive misses no plot point.
 *
 * <p>PARAGRAPHS: a body may contain {@link CodexSystem#PARAGRAPH_SEPARATOR} to break itself into
 * paragraphs.  The properties format is one line per id, so a literal newline is not available; the
 * separator is what gives the detail view real paragraph breaks anyway.
 *
 * <p>Voice sources: {@code story/00-premise.md}, {@code 02-player-and-cloning.md},
 * {@code 03-the-planet.md}, {@code 04-the-organization.md}, {@code story/dialog/*}.
 */
public final class CodexStrings {

    private CodexStrings() {}

    /** Adds every order-6 codex string to {@code strings}.  Returns the same table for chaining. */
    public static StoryStrings registerDefaults(StoryStrings strings) {
        registerChrome(strings);
        registerCategories(strings);
        registerComposedPages(strings);
        registerLogs(strings);
        registerPeople(strings);
        registerPlanet(strings);
        registerOrganization(strings);
        registerOra(strings);
        registerMemories(strings);
        return strings;
    }

    /**
     * THE COMPOSED PAGES (narrative-rework order-9) - the two entries the game writes for itself.
     *
     * <p>Their BODIES are assembled at open time, so what lives here is the frame around them: the
     * titles, the four block headings, the region rows the recap picks between, and the sentence
     * that stands in for a block with nothing in it yet.  The registered body strings are the
     * fallback shown if no composer is wired in - a composed entry is still a fully translatable row.
     *
     * <p>The JOB rows are the Organization's five gate transmissions, rendered as ORA would repeat
     * them back rather than as they were transmitted: she is summarising an order, not re-issuing
     * one.  That they CHANGE across the descent is the whole reason this block is worth a page - the
     * escalation from "restore the site" to "return the asset" is legible on one screen, which is
     * the only place in the game it ever is.
     *
     * <p>Nothing here says what to do next.  Where the player IS and what they were TOLD; never an
     * objective, a marker or a next step (docs/narrative-authority.txt SECTION 7).
     */
    private static void registerComposedPages(StoryStrings strings) {
        strings.put("story.codex.recap.title",  "WHERE WE'D GOT TO");
        strings.put("story.codex.recap.source", "ORA, continuous");
        strings.put("story.codex.recap.body",   "Nothing to summarise yet. We've only just started.");

        strings.put("story.codex.missed.title",  "THINGS I SAID QUICKLY");
        strings.put("story.codex.missed.source", "ORA, repeated on request");
        strings.put("story.codex.missed.body",   "Nothing here. You've been reading them.");

        // The four blocks, in the order she says them.
        strings.put("story.recap.heading.where",   "WHERE WE ARE");
        strings.put("story.recap.heading.job",     "THE JOB");
        strings.put("story.recap.heading.know",    "WHAT WE KNOW");
        strings.put("story.recap.heading.decided", "WHAT YOU DECIDED");
        strings.put("story.recap.nothing",         "Nothing yet.");

        // WHERE WE ARE - the deepest region reached, in plain words.  No floor number and no depth
        // reading: this is her placing the player, not a readout.
        strings.put("story.recap.where.rings",
                                                "The habitation rings. The part of this place people lived in.");
        strings.put("story.recap.where.galleries",
                                                "The cutting floors, under the rings. This is where the work happened.");
        strings.put("story.recap.where.reliquary",
                                                "The Reliquary. Racks of stored patterns, and the machines that print them.");
        strings.put("story.recap.where.wound",
                                                "Below the racks, where the facility stops behaving like one.");
        strings.put("story.recap.where.core",
                                                "The core. The thing the whole site was built on top of.");

        // THE JOB - the standing order as most recently transmitted.  Four escalations and a
        // write-off, in her mouth.
        strings.put("story.recap.job.rings",
                                                "Descend and restore the site. That's the entire brief they sent.");
        strings.put("story.recap.job.galleries",
                                                "Same order, plus a correction: extraction here is routine and within tolerance.");
        strings.put("story.recap.job.reliquary",
                                                "They've reminded us your reprints are a benefit, and that you're under review.");
        strings.put("story.recap.job.wound",
                                                "The order changed. Restore the restraints. It supersedes the purge order.");
        strings.put("story.recap.job.core",
                                                "The pattern is under write-off review. They want the asset back in restraint.");
    }

    /** The codex screen's own labels - chrome, but still localised, like every other label. */
    private static void registerChrome(StoryStrings strings) {
        strings.put("story.codex.title",            "ARCHIVE");
        strings.put("story.codex.back",             "< BACK");
        strings.put("story.codex.empty",            "Nothing recovered here yet.");
        strings.put("story.codex.new",              "NEW");
        strings.put("story.codex.complete",         "COMPLETE");
        strings.put("story.codex.take",             "ORA's note");
        strings.put("story.codex.setting.text",     "TEXT");
        strings.put("story.codex.setting.reveal",   "REVEAL");
        strings.put("story.codex.setting.motion",   "MOTION");
        strings.put("story.codex.setting.audio",    "SOUND");
        // Setting VALUES, indexed by the settings model's own enums / booleans.
        strings.put("story.codex.setting.text.0",   "NORMAL");
        strings.put("story.codex.setting.text.1",   "LARGE");
        strings.put("story.codex.setting.text.2",   "LARGEST");
        strings.put("story.codex.setting.reveal.0", "PACED");
        strings.put("story.codex.setting.reveal.1", "INSTANT");
        strings.put("story.codex.setting.motion.0", "FULL");
        strings.put("story.codex.setting.motion.1", "REDUCED");
        strings.put("story.codex.setting.audio.0",  "OFF");
        strings.put("story.codex.setting.audio.1",  "ON");
    }

    private static void registerCategories(StoryStrings strings) {
        strings.put("story.codex.category.logs",         "LOGS");
        strings.put("story.codex.category.people",       "PEOPLE");
        strings.put("story.codex.category.planet",       "THE PLANET");
        strings.put("story.codex.category.organization", "THE ORGANIZATION");
        strings.put("story.codex.category.ora",          "ORA");
        strings.put("story.codex.category.memories",     "MEMORIES");
    }

    // -------------------------------------------------------------------------
    // LOGS - the paperwork in full.  Dry, procedural, and damning precisely because none of it was
    // written to be read this way.
    // -------------------------------------------------------------------------
    private static void registerLogs(StoryStrings strings) {
        entry(strings, "codex.log.rings.roster",
              "SHIFT ROSTER - RING 4",
              "Personnel terminal, Habitation Rings",
              "Rotation closed 06:00. Forty-one signed out at the gate, forty-one "
            + "accounted for, zero outstanding. The sheet is complete and the shift is "
            + "clean.||Underneath, in a different hand: they signed out at the gate. "
            + "Nobody checked whether they left the gate. There is no roster for the "
            + "lifts and there never has been.");

        entry(strings, "codex.log.rings.maintenance",
              "MAINTENANCE LOG - VIBRATION",
              "Engineering terminal, Habitation Rings",
              "Ticket 8812. Low-frequency vibration reported on rings three and four, "
            + "night cycle only. Structural inspected the mounts twice and found nothing "
            + "out of tolerance. Closed as no fault.||Ticket 8813, reopened by the same "
            + "engineer: it is not the mounts. It is not machinery. It stops when the "
            + "drills stop. Closed as no fault.||Ticket 8814. Closed as no fault. "
            + "Requester advised to stop filing.");

        entry(strings, "codex.log.rings.safety",
              "SAFETY NOTICE - REVISION 11",
              "Compliance terminal, Habitation Rings",
              "Sixty pages. Hand protection, lifting posture, the correct way to report a "
            + "near miss, the correct way to report a near miss that involves a "
            + "supervisor.||Nothing about the deep. Nothing about the noise. Nothing "
            + "about what the extraction floors are extracting, in a document that "
            + "devotes four pages to the safe handling of a stepladder.");

        entry(strings, "codex.log.galleries.quota",
              "EXTRACTION QUOTA - CYCLE 44",
              "Foreman terminal, Harvesting Galleries",
              "Cycle target met on eleven of twelve faces. Face nine short by nine "
            + "percent, second consecutive cycle. Foreman noted as underperforming; "
            + "correction scheduled.||Face nine is the one where the material screams "
            + "when it is cut. There is a form for that. It is not on this sheet.");

        entry(strings, "codex.log.galleries.tolerance",
              "TOLERANCE SHEET - LIVE WEIGHT",
              "Quality terminal, Harvesting Galleries",
              "Acceptable moisture, acceptable inclusion density, acceptable live weight "
            + "at point of cut. Reject anything under the live-weight minimum: dead "
            + "material renders at a loss.||The whole document is a specification for "
            + "keeping something alive right up until the moment it is processed, "
            + "written by somebody who never once used the word alive.");

        entry(strings, "codex.log.galleries.manifest",
              "TRANSPORT MANIFEST - DAILY",
              "Logistics terminal, Harvesting Galleries",
              "Down: empty racks, cutting stock, consumables, personnel. Up: full racks, "
            + "sealed, refrigerated, destination Reliquary intake.||Every day for "
            + "eleven years. The manifest has no column for what is in the racks. It has "
            + "a column for the weight, and a column for how much the weight is worth.");

        entry(strings, "codex.log.reliquary.service",
              "CRADLE SERVICE LOG - BANK 7",
              "Cradle terminal, The Reliquary",
              "Routine service on printing cradles 7-1 through 7-9. Pattern buffers "
            + "flushed, feed lines purged, reserve draw within nominal.||Cradle 7-4 is "
            + "assigned to your serial. The column beside it counts how many times it "
            + "has run. The number is not small, and you have no memory of any of them "
            + "except the last one.");

        entry(strings, "codex.log.reliquary.intake",
              "FUEL INTAKE SHEET",
              "Refinery terminal, The Reliquary",
              "Intake volume, render efficiency, waste fraction, output to cradle banks "
            + "one through nine. Signed off daily by a technician whose name has been "
            + "the same for four years.||Nowhere on the sheet does it say what is being "
            + "rendered, or where it comes from. Every other document in this facility "
            + "names its inputs. This one has a blank where the source should be, and "
            + "the blank is printed into the form.");

        entry(strings, "codex.log.reliquary.operator",
              "OPERATOR'S NOTE - UNFILED",
              "Personal terminal, The Reliquary",
              "It is not a report. Somebody sat down at a shift terminal and wrote: I "
            + "have done this descent nine times. I am told I have done it nine times. "
            + "I remember four.||Then: if you are reading this you are me, and they have "
            + "printed you again, and you should know that the thing at the bottom is "
            + "not attacking us.||The handwriting matches the sample on your own "
            + "personnel file.");

        entry(strings, "codex.log.wound.field",
              "FIELD NOTE - SUBJECT STATUS",
              "Survey terminal, The Wound",
              "Drill assembly four, depth two thousand one hundred metres, penetration "
            + "into stratum designated soft.||Below that, handwritten: the subject is "
            + "awake. It has been awake for the entire programme. Recommend immediate "
            + "suspension of extraction pending review.||Stamped: REVIEWED. NO CHANGE.");

        entry(strings, "codex.log.wound.sedation",
              "SEDATION SCHEDULE",
              "Medical terminal, The Wound",
              "Hourly dosing at forty-one injection sites, continuous, no scheduled end "
            + "date. Dosage revised upward eleven times across the programme as "
            + "tolerance developed.||Forty years of hourly doses. Somebody worked out "
            + "the compound, somebody built the pumps, somebody signed the reorder every "
            + "quarter, and not one of them wrote down what they were sedating.");

        entry(strings, "codex.log.core.resignation",
              "RESIGNATION - NEVER SUBMITTED",
              "Abandoned terminal, The Core",
              "Addressed to a director who was three reorganisations gone by the time it "
            + "was written. It gives one month's notice, thanks the department, and asks "
            + "that the writer's pattern be deleted from the cradle registry rather than "
            + "held.||It was saved as a draft and left open. The cursor is still in the "
            + "last line, which reads: I would rather stay dead than be brought back to "
            + "keep doing this.");
    }

    // -------------------------------------------------------------------------
    // PEOPLE - four names, assembled from what they left behind.
    // -------------------------------------------------------------------------
    private static void registerPeople(StoryStrings strings) {
        entry(strings, "codex.people.dunnet",
              "DUNNET, STRUCTURAL",
              "Reconstructed from maintenance tickets",
              "Filed the same fault three times in eleven days and was told to stop "
            + "filing. His last ticket is four words long: it is not machinery.||He was "
            + "right, and being right cost him a note on his record. There is no "
            + "resignation on file and no transfer either. His locker was still assigned "
            + "to him on the day the rings emptied.");

        entry(strings, "codex.people.veil",
              "VEIL, FACE NINE FOREMAN",
              "Reconstructed from quota sheets",
              "Missed her extraction quota twice running, which in this facility is a "
            + "disciplinary matter rather than a question.||Face nine is the face where "
            + "the material screams. Nine percent is roughly the fraction of a shift a "
            + "person loses if they stop the cutter every time they hear it. She was "
            + "corrected. The next cycle's numbers are perfect.");

        entry(strings, "codex.people.archivist",
              "THE CRADLE ENGINEER",
              "Reconstructed from an unsigned note",
              "Whoever wrote the cradle-engineer's note understood the whole thing years "
            + "before anybody else did: that the reserve the printers draw on is not a "
            + "chemical, that it is being taken out of something alive, and that every "
            + "reprint spends a piece of it.||They did not report it and they did not "
            + "leave. They kept servicing the cradles, and they wrote it all down where "
            + "an operator would eventually walk past it.");

        entry(strings, "codex.people.operator",
              "THE PREVIOUS OPERATOR",
              "Reconstructed from an unfiled note",
              "Nine descents. Four remembered. The note they left is addressed to "
            + "whoever is printed next, which they knew would be themselves.||You have "
            + "the same serial format, the same handwriting sample and the same "
            + "assignment. The only honest way to file this entry is under other people, "
            + "which is a decision the archive is not qualified to make.");
    }

    // -------------------------------------------------------------------------
    // THE PLANET - what it is, and what it has been trying to say.
    // -------------------------------------------------------------------------
    private static void registerPlanet(StoryStrings strings) {
        entry(strings, "codex.contaminant",
              "CONTAMINANT",
              "Operational vocabulary, Deepworks",
              "The word the Organization uses for everything down here that moves. It "
            + "appears in the standing order, in the hazard notices and in the incident "
            + "forms, and it is never once defined.||A word that is never defined is a "
            + "word doing work. This one does the work of making a thing an infection "
            + "instead of an inhabitant, which is the difference between clearing a site "
            + "and doing something to somebody.");

        entry(strings, "codex.planet.voice",
              "THE VOICE",
              "Recovered from pattern-integrity flags",
              "It is not audio. Nothing registers on the suit's microphones and nothing "
            + "shows on the logs, and yet the words arrive, in the wrong order, in a "
            + "grammar that keeps sliding.||It is not speaking a language it learned. It "
            + "is reaching for one, out of the only minds it has ever been close to: "
            + "the ones that came down here with drills.");

        entry(strings, "codex.planet.name",
              "THE NAME",
              "Recovered in communion",
              "It has one. It is not a word and it does not survive being written down; "
            + "the closest the archive can get is a shape and a duration.||What can be "
            + "written down is this: the survey designation this world carries on every "
            + "Deepworks document was assigned by a survey team in an afternoon, and the "
            + "thing living inside it had a name for itself long before anyone arrived "
            + "to give it a different one.");

        entry(strings, "codex.planet.wound",
              "THE WOUND",
              "Direct observation, drill stratum",
              "Below the refinery the facility stops being a facility. The walls are "
            + "warm and they are not walls. The drill assemblies do not end in rock.||"
            + "Forty years of continuous extraction from a living body, with sedation "
            + "scheduled hourly because the body kept waking up. Everything the "
            + "Organization built here is an apparatus for keeping something alive and "
            + "quiet while pieces of it are removed.");
    }

    // -------------------------------------------------------------------------
    // THE ORGANIZATION - in its own words, which is the worst thing about it.
    // -------------------------------------------------------------------------
    private static void registerOrganization(StoryStrings strings) {
        entry(strings, "codex.harvest",
              "THE HARVEST",
              "Operations summary, Deepworks",
              "Extraction runs on twelve faces across the gallery band, cut to tolerance, "
            + "racked, sealed and lifted to the Reliquary for rendering. Output feeds the "
            + "printing cradles and the reserve.||Stated plainly and with no adjectives, "
            + "which is how it appears in every internal document: a supply chain that "
            + "begins in a living thing and ends in a person walking out of a cradle "
            + "believing they were always alive.");

        entry(strings, "codex.yield.ledger",
              "THE YIELD LEDGER",
              "Accounting terminal, Harvesting Galleries",
              "Yield per face, render efficiency, cost per reprint, all to two decimal "
            + "places. The tissue is remarkable: it regenerates, it accepts damage and "
            + "recovers, it can be cut from indefinitely if the cutting is paced.||The "
            + "ledger treats that as the entire discovery. Nowhere does anyone write "
            + "down the obvious corollary - that a thing which heals is a thing that is "
            + "being hurt, over and over, on a schedule set by this spreadsheet.");

        entry(strings, "codex.soul.reserve",
              "THE RESERVE",
              "Cradle engineer's note, The Reliquary",
              "Every printing cradle draws on what the paperwork calls the reserve. It is "
            + "metered, it is finite, and it is the only input the refinery sheets refuse "
            + "to name.||The reserve is the planet. Not its minerals - the thing itself. "
            + "Each reprint renders a measured piece of a living being into the pattern "
            + "that walks out of the cradle.||You have been printed before. This is what "
            + "it was made of.");

        entry(strings, "codex.overseer",
              "THE OVERSEER",
              "Comms analysis",
              "The voice on the standing channel is not one person. The routing headers "
            + "change between transmissions, the phrasing does not, and the authority is "
            + "identical whoever is holding it.||It is a role, filled in rotation, "
            + "reading from a script written to be readable by anyone. That is why it "
            + "never sounds cruel. Nobody in the chair is cruel. The chair is.");

        entry(strings, "codex.revocation",
              "REPRINT REVOCATION",
              "Standing policy, Deepworks",
              "Reprint authorization is a benefit of employment and is contingent on "
            + "compliance. A pattern flagged for non-compliance may be held, suspended, "
            + "or revoked at the Organization's discretion.||Read once, it is a "
            + "personnel clause. Read twice, it is the sentence in which a company "
            + "explains that it owns whether you get to exist tomorrow, and that it is "
            + "considering.");
    }

    // -------------------------------------------------------------------------
    // ORA - the voice in the ear, and what she is underneath it.
    // -------------------------------------------------------------------------
    private static void registerOra(StoryStrings strings) {
        entry(strings, "codex.ora.count",
              "SHE KEEPS COUNT",
              "Observed across reprints",
              "She greets every print with the number and then makes a joke about it. "
            + "The joke is not the point; the counting is.||Nothing in her assignment "
            + "requires her to track how many times you have died. She does it because "
            + "she is the only continuous thing in your life, and somebody has to hold "
            + "the total when you cannot.");

        entry(strings, "codex.ora.build",
              "WHAT SHE IS",
              "Reliquary pattern archives",
              "Her pattern is held in the same archive as yours, in the same racks, "
            + "drawn from the same reserve. She is not equipment attached to the suit. "
            + "She is a thing that was made in this building, out of what this building "
            + "renders.||She has known that for longer than you have, and she has been "
            + "cheerful at you the entire time.");

        entry(strings, "codex.ora.leash",
              "WHAT SHE IS ALLOWED TO SAY",
              "Comms audit",
              "Her channel is monitored and her vocabulary is shaped: certain words come "
            + "out of her mouth as the Organization's words, and certain conclusions come "
            + "out as jokes.||Watch when the jokes stop. She does not lie to you; she "
            + "goes quiet, or she says a true thing sideways, and both of those are her "
            + "telling you as much as she is able to.");
    }

    // -------------------------------------------------------------------------
    // MEMORIES - the block lifting, one region at a time.  These are the player's own, and they are
    // the only entries in the archive that nobody wrote down.
    // -------------------------------------------------------------------------
    private static void registerMemories(StoryStrings strings) {
        entry(strings, "codex.memory.rings",
              "FRAGMENT: THE GATE",
              "Recovered on descent",
              "Signing out at a gate, in a queue, with a card that had your face on it. "
            + "Somebody ahead of you complained about the lifts.||It is a completely "
            + "ordinary memory and you cannot place which of your lives it belongs to.");

        entry(strings, "codex.memory.galleries",
              "FRAGMENT: THE SOUND",
              "Recovered on descent",
              "You have heard the note the cutters make on face nine before. Not on a "
            + "recording. Standing there, with a tool in your hands.||You remember "
            + "reaching to stop the cutter. You do not remember whether you did.");

        entry(strings, "codex.memory.reliquary",
              "FRAGMENT: THE CRADLE",
              "Recovered on descent",
              "Waking up on a rack, cold, with the feed lines still attached and somebody "
            + "reading a serial number off a clipboard.||You have woken up like that "
            + "more times than the number of descents you can account for. The gap "
            + "between those two figures is the shape of what was taken out of you.");

        entry(strings, "codex.memory.wound",
              "FRAGMENT: THE ORDER",
              "Recovered on descent",
              "You were down here before, in the warm part, and the order was the same "
            + "order it is now.||You remember arguing. You remember being told that the "
            + "contaminant is a hazard and that hazards are cleared. You remember "
            + "clearing it.");

        entry(strings, "codex.memory.core",
              "FRAGMENT: WHO YOU WERE",
              "Recovered at the core",
              "Before the serial, before the cradle, before any of the numbers: a person "
            + "who took a contract on a deep-survey world because the reprint benefit "
            + "meant the job could not really kill them.||That person has been dead for "
            + "a long time. Everything since has been printed out of the thing they were "
            + "sent down to clear.");
    }

    /** Registers one entry's three ids, using the {@link CodexEntry} id-mirroring convention. */
    private static void entry(StoryStrings strings, String entryId,
                              String title, String source, String body) {
        strings.put("story." + entryId + ".title",  title);
        strings.put("story." + entryId + ".source", source);
        strings.put("story." + entryId + ".body",   body);
    }
}
