package ge.tbegvadze.toon3d.narrative;

/**
 * The v1 BARK CATALOG (Story UI order-2) — every one-liner the game can say, as data.
 *
 * <p>Adding a line is ONE {@link BarkRegistry#register} call here.  Nothing in {@code World}, the
 * renderer or {@link BarkSystem} switches on a bark list, so a new line, a new region band or a new
 * speaker for an existing moment needs no code change anywhere else (the same "registration, not a
 * switch statement" discipline as {@code route/RouteRegistries}).
 *
 * <h3>How the tone shift is encoded</h3>
 * There is no "if region == 3" anywhere.  Each row declares the STORY REGION BAND it belongs to,
 * and {@link BarkSystem} selects only rows whose band contains the deepest region ever reached:
 * <ul>
 *   <li>ORA parrots the Organization ("contaminant", "purge", "yield") in the Habitation Rings and
 *       the Galleries, cracks in the Reliquary, and refuses the words from the Wound down.</li>
 *   <li>The Planet has NO rows in Region 1 — its silence is the absence of data, exactly as
 *       {@code story/dialog/planet-voice.md} specifies.  It whispers single words in Region 2,
 *       addresses you in Region 3, and speaks plainly from Region 4 down.</li>
 *   <li>The Organization speaks only at region gates, one cold line per region, escalating
 *       procedural -&gt; corrective -&gt; coercive -&gt; denial.</li>
 * </ul>
 *
 * <h3>How the humour is balanced</h3>
 * Every row carries a {@link BarkTone}.  Most are {@link BarkTone#LORE} — they tell the player
 * something about the place, the Organization, ORA or the planet — and a minority are
 * {@link BarkTone#LEVITY}, her dry asides.  Lore outweighs levity by selection weight
 * ({@code StoryUiConstants.STORY_BARK_WEIGHT_*}), so she is funny sometimes and meaningful most of
 * the time.  Rebalance in the constants file, never by editing selection code.  Pools are also kept
 * DEEP (4-5 lines per region per repeatable moment) so the no-repeat memory always has somewhere to
 * go; the deeper the pool, the longer before the player hears anything twice.
 *
 * <p>ONE-SHOT rows (region entry, gate orders, first sight of an enemy family) carry
 * {@code oneShot(true)} and are keyed by their row id in the persistent seen-set, so they fire once
 * ever and never replay when a later run re-enters a cleared region.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class BarkCatalog {

    private BarkCatalog() {}

    /**
     * Registers the v1 rows into {@code registry}.  Idempotent: a registry that already holds rows
     * is left untouched, so calling this from more than one entry point is safe.
     */
    public static void bootstrap(BarkRegistry registry) {
        if (registry == null || !registry.isEmpty()) return;

        registerIntroBeats(registry);
        // THE VOCABULARY LADDER (narrative-rework order-3): one naming line per StoryTerm, each on
        // an existing trigger.  Registered from the term table itself, so adding a proper noun to
        // this fiction is one enum row plus its string and never an edit in here.
        StoryTermCatalog.registerIntroLines(registry);
        registerControlHints(registry);
        registerFloorArrival(registry);
        registerRegionEntry(registry);
        registerGateOrders(registry);
        // THE BESTIARY VOICE (narrative-rework order-5): the family lines, one line per archetype,
        // one per special ability and the first boss's pair — all registered by looping the enemy
        // layer's own enums, so a new family or archetype cannot ship without a voice.
        BestiaryCatalog.bootstrap(registry);
        registerLogTakes(registry);
        registerKills(registry);
        registerLowHealth(registry);
        registerDeepStrata(registry);
        registerIdleAndBacktrack(registry);
        registerCodexCompletion(registry);
    }

    /** A fresh registry with the v1 catalog already in it (tests, showcases). */
    public static BarkRegistry defaultRegistry() {
        BarkRegistry registry = new BarkRegistry();
        bootstrap(registry);
        return registry;
    }

    // -------------------------------------------------------------------------
    // ORA'S INTRODUCTION (narrative-rework order-2 D) — the cold open plus the four beats that
    // finish the job later, one row per {@link IntroBeat}.  Three lines on run 1 answer who is
    // talking, what happened to the player and why their memory came back short; two on run 2 say
    // the thing that makes her matter (she is not printed, so she is the only one who remembers the
    // last body); the rest hang off a door, a terminal, a pickup and the first quiet stretch, so she
    // is met being useful rather than delivering a biography.
    //
    // Every row is one-shot ever and STORY_CRITICAL: an introduction that loses a race with a
    // flavour quip is an introduction that never happened.  Region 1 only for the run-start rows —
    // a veteran on their fortieth reprint gets none of this.
    // -------------------------------------------------------------------------
    private static void registerIntroBeats(BarkRegistry registry) {
        for (IntroBeat beat : IntroBeat.values()) {
            String id = "bark.intro." + beat.getCatalogKey();
            registry.register(row(id, storyIdFor(id))
                    .trigger(beat.getTrigger())
                    .subjectKey(beat.getSubjectKey())
                    .region(StoryRegion.HABITATION_RINGS)
                    .priority(BarkPriority.STORY_CRITICAL)
                    .oneShot(true)
                    .build());
        }
    }

    // -------------------------------------------------------------------------
    // TUTORIAL THROUGH ORA (order-5) — the game's entire tutorial, one line per control, taught the
    // first time that control is actually needed.  There is no tutorial screen and there will not be
    // one: a diagram of twelve buttons is something a player skips, and a voice that explains one
    // thing at the moment it matters is something they read.  Every row is one-shot ever (the flag
    // is persistent, so a reprint never re-teaches) and STORY_CRITICAL, because a line that teaches
    // a control must never be dropped as chatter.  Region-unrestricted on purpose: a player may not
    // hold a second gun until the Reliquary, and the hint must still be there when they do.
    // -------------------------------------------------------------------------
    private static void registerControlHints(BarkRegistry registry) {
        for (ControlHint hint : ControlHint.values()) {
            String id = "bark.control." + hint.getCatalogKey();
            registry.register(row(id, storyIdFor(id))
                    .trigger(BarkTrigger.CONTROL_HINT)
                    .subjectKey(hint.getSubjectKey())
                    .priority(BarkPriority.STORY_CRITICAL)
                    .oneShot(true)
                    .build());
        }
    }

    // -------------------------------------------------------------------------
    // LOG TAKES (order-5) — ORA's one-line reaction to whatever the player just read off a facility
    // terminal.  The full text of a log is order-6's codex; this channel is the part a player who
    // never opens a menu still gets, which is why the pools are deep and repeatable.
    //
    // Two rows are different: the yield report in the Galleries and the cradle-engineer log in the
    // Reliquary are the MANDATORY reveal beats of their regions (story/05-descent-structure.md), so
    // they are one-shot and STORY_CRITICAL and win their pool the first time the player reads
    // anything down there.
    // -------------------------------------------------------------------------
    private static void registerLogTakes(BarkRegistry registry) {
        logLine(registry, "bark.log.rings.1", StoryRegion.HABITATION_RINGS, BarkTone.LORE);
        logLine(registry, "bark.log.rings.2", StoryRegion.HABITATION_RINGS, BarkTone.LORE);
        logLine(registry, "bark.log.rings.3", StoryRegion.HABITATION_RINGS, BarkTone.LEVITY);
        logLine(registry, "bark.log.rings.4", StoryRegion.HABITATION_RINGS, BarkTone.LORE);

        logLine(registry, "bark.log.galleries.1", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        logLine(registry, "bark.log.galleries.2", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        logLine(registry, "bark.log.galleries.4", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        // .3 (the screaming-report form) was CUT by narrative-rework order-6 B as a duplicate of the
        // two rows above; .5 holds the band at four repeatable rows.
        logLine(registry, "bark.log.galleries.5", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);

        logLine(registry, "bark.log.reliquary.1", StoryRegion.RELIQUARY, BarkTone.LORE);
        logLine(registry, "bark.log.reliquary.2", StoryRegion.RELIQUARY, BarkTone.LORE);
        logLine(registry, "bark.log.reliquary.3", StoryRegion.RELIQUARY, BarkTone.LORE);
        logLine(registry, "bark.log.reliquary.4", StoryRegion.RELIQUARY, BarkTone.LORE);

        for (int lineNumber = 1; lineNumber <= 4; lineNumber++) {
            String id = "bark.log.wound." + lineNumber;
            registry.register(row(id, storyIdFor(id))
                    .trigger(BarkTrigger.LOG_FOUND).region(StoryRegion.WOUND).build());
        }
        for (int lineNumber = 1; lineNumber <= 4; lineNumber++) {
            String id = "bark.log.core." + lineNumber;
            registry.register(row(id, storyIdFor(id))
                    .trigger(BarkTrigger.LOG_FOUND).region(StoryRegion.CORE).build());
        }

        // The two mandatory log beats: the yield report ("they're mining something that heals") and
        // the cradle engineer's note (the soul-fuel reveal).
        mandatoryLogBeat(registry, "bark.log.galleries.yield", StoryRegion.HARVESTING_GALLERIES);
        mandatoryLogBeat(registry, "bark.log.reliquary.cradle", StoryRegion.RELIQUARY);
    }

    private static void logLine(BarkRegistry registry, String id, StoryRegion region, BarkTone tone) {
        registry.register(row(id, storyIdFor(id))
                .trigger(BarkTrigger.LOG_FOUND).region(region).tone(tone).build());
    }

    private static void mandatoryLogBeat(BarkRegistry registry, String id, StoryRegion region) {
        registry.register(row(id, storyIdFor(id))
                .trigger(BarkTrigger.LOG_FOUND)
                .region(region)
                .priority(BarkPriority.STORY_CRITICAL)
                .oneShot(true)
                .build());
    }

    // -------------------------------------------------------------------------
    // Floor arrival — ORA reads the room you just walked into.  Repeatable.
    // -------------------------------------------------------------------------
    private static void registerFloorArrival(BarkRegistry registry) {
        floorLine(registry, "bark.floor.rings.1", StoryRegion.HABITATION_RINGS, BarkTone.LEVITY);
        floorLine(registry, "bark.floor.rings.2", StoryRegion.HABITATION_RINGS, BarkTone.LORE);
        floorLine(registry, "bark.floor.rings.3", StoryRegion.HABITATION_RINGS, BarkTone.LORE);
        floorLine(registry, "bark.floor.rings.4", StoryRegion.HABITATION_RINGS, BarkTone.LORE);
        // .5 ("air's breathable down here, you're welcome") was CUT by narrative-rework order-6 B: a
        // joke with no observation under it, about a thing she did not do and nothing in the fiction
        // suggests would be otherwise.  The band keeps four rows, and .1 keeps the levity.

        floorLine(registry, "bark.floor.galleries.1", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        floorLine(registry, "bark.floor.galleries.2", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        floorLine(registry, "bark.floor.galleries.3", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        floorLine(registry, "bark.floor.galleries.4", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        floorLine(registry, "bark.floor.galleries.5", StoryRegion.HARVESTING_GALLERIES, BarkTone.LEVITY);

        floorLine(registry, "bark.floor.reliquary.1", StoryRegion.RELIQUARY, BarkTone.LORE);
        floorLine(registry, "bark.floor.reliquary.2", StoryRegion.RELIQUARY, BarkTone.LORE);
        floorLine(registry, "bark.floor.reliquary.3", StoryRegion.RELIQUARY, BarkTone.LORE);
        floorLine(registry, "bark.floor.reliquary.4", StoryRegion.RELIQUARY, BarkTone.LORE);
        floorLine(registry, "bark.floor.reliquary.5", StoryRegion.RELIQUARY, BarkTone.LEVITY);

        // The Wound and the Core: she has stopped joking about any of this.
        registry.register(row("bark.floor.wound.1", "story.bark.floor.wound.1")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).regionFrom(StoryRegion.WOUND).build());
        registry.register(row("bark.floor.wound.2", "story.bark.floor.wound.2")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).regionFrom(StoryRegion.WOUND).build());
        registry.register(row("bark.floor.wound.3", "story.bark.floor.wound.3")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).regionFrom(StoryRegion.WOUND).build());
        registry.register(row("bark.floor.wound.4", "story.bark.floor.wound.4")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).regionFrom(StoryRegion.WOUND).build());
        floorLine(registry, "bark.floor.core.1", StoryRegion.CORE, BarkTone.LORE);
        floorLine(registry, "bark.floor.core.2", StoryRegion.CORE, BarkTone.LORE);
    }

    private static void floorLine(BarkRegistry registry, String id, StoryRegion region, BarkTone tone) {
        registry.register(row(id, storyIdFor(id))
                .trigger(BarkTrigger.FLOOR_ARRIVAL).region(region).tone(tone).build());
    }

    // -------------------------------------------------------------------------
    // Region entry — one mandatory ORA beat per story region.  One-shot, critical.
    // -------------------------------------------------------------------------
    private static void registerRegionEntry(BarkRegistry registry) {
        for (StoryRegion region : StoryRegion.values()) {
            String id = "bark.region." + region.getCatalogKey();
            registry.register(row(id, storyIdFor(id))
                    .trigger(BarkTrigger.REGION_ENTERED)
                    .region(region)
                    .priority(BarkPriority.STORY_CRITICAL)
                    .oneShot(true)
                    .build());
        }
    }

    // -------------------------------------------------------------------------
    // Region gate — the Organization, once per region, escalating.  One-shot, critical.
    //
    // THE ORGANIZATION, REWORKED (narrative-rework order-7, doctrine D7): each of these five is a
    // transmission with a JOB in it and at least one claim about the WORLD that an object somewhere
    // in the build disproves — the LIE LEDGER in docs/story-ui-system.txt is that pairing, written
    // down.  The rules the pool obeys are content rules, not code ones, and they live in that doc:
    // it never speaks twice on a floor, never answers a question about itself, never addresses ORA,
    // and is never cruel.  Nothing it says is corrected on screen by narration.
    //
    // ORA'S ASIDES ride the SAME moment behind a subject key, which is the established idiom for a
    // second line on one gameplay beat (the vocabulary ladder's namings do it too).  A gate order
    // asked for with no subject can therefore still only ever select the Organization's own row.
    // -------------------------------------------------------------------------

    /**
     * The subject key ORA's gate asides are registered and requested under.  {@code World} asks for
     * it immediately after the order itself, so her comment lands behind the words she is commenting
     * on rather than in front of them.
     */
    public static final String GATE_ASIDE_SUBJECT_KEY = "ORA_GATE_ASIDE";

    private static void registerGateOrders(BarkRegistry registry) {
        for (StoryRegion region : StoryRegion.values()) {
            String id = "bark.gate." + region.getCatalogKey();
            registry.register(row(id, storyIdFor(id))
                    .speaker(Speaker.ORGANIZATION)
                    .trigger(BarkTrigger.REGION_GATE_ORDER)
                    .region(region)
                    .priority(BarkPriority.STORY_CRITICAL)
                    .oneShot(true)
                    .build());
        }
        // Two asides, not five: she comments when there is something in the paperwork to point at,
        // and stays quiet the rest of the time.  Both are STORY_CRITICAL because the moment they
        // hang off happens exactly once — a dropped aside is an aside nobody ever hears.
        gateAside(registry, "bark.gate.rings.ora", StoryRegion.HABITATION_RINGS);
        gateAside(registry, "bark.gate.wound.ora", StoryRegion.WOUND);

        // THE OVERSEER IS A DESK (order-7 C).  ORA volunteers the EVIDENCE — she has been reading the
        // routing headers — from the Reliquary down, on an ordinary floor arrival.  The MECHANISM
        // (what a routing header is and why it means nobody is speaking) is the reply to the Core
        // order's probe, so a player who wants it asks for it and everybody else still gets the fact.
        registry.register(row("bark.org.desk", storyIdFor("bark.org.desk"))
                .trigger(BarkTrigger.FLOOR_ARRIVAL)
                .regionFrom(StoryRegion.RELIQUARY)
                .priority(BarkPriority.STORY_CRITICAL)
                .oneShot(true)
                .build());
    }

    private static void gateAside(BarkRegistry registry, String id, StoryRegion region) {
        registry.register(row(id, storyIdFor(id))
                .trigger(BarkTrigger.REGION_GATE_ORDER)
                .subjectKey(GATE_ASIDE_SUBJECT_KEY)
                .region(region)
                .priority(BarkPriority.STORY_CRITICAL)
                .oneShot(true)
                .build());
    }

    // -------------------------------------------------------------------------
    // Kills — ORA's confidence rotting across the descent.  Reactive, heavily rate-limited.
    // -------------------------------------------------------------------------
    private static void registerKills(BarkRegistry registry) {
        killLine(registry, "bark.kill.rings.1", StoryRegion.HABITATION_RINGS, BarkTone.LORE);
        killLine(registry, "bark.kill.rings.3", StoryRegion.HABITATION_RINGS, BarkTone.LORE);
        killLine(registry, "bark.kill.rings.4", StoryRegion.HABITATION_RINGS, BarkTone.LEVITY);
        // .2 ("Textbook. Efficient.") was CUT by narrative-rework order-6 B — it said nothing under
        // its joke; .5 is its replacement and restores the band to four rows.
        killLine(registry, "bark.kill.rings.5", StoryRegion.HABITATION_RINGS, BarkTone.LORE);

        killLine(registry, "bark.kill.galleries.1", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        killLine(registry, "bark.kill.galleries.2", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        killLine(registry, "bark.kill.galleries.3", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);
        // .4 (the noise-aggro joke) was CUT by narrative-rework order-6 B; .5 replaces it.
        killLine(registry, "bark.kill.galleries.5", StoryRegion.HARVESTING_GALLERIES, BarkTone.LORE);

        killLine(registry, "bark.kill.reliquary.1", StoryRegion.RELIQUARY, BarkTone.LORE);
        killLine(registry, "bark.kill.reliquary.2", StoryRegion.RELIQUARY, BarkTone.LORE);
        killLine(registry, "bark.kill.reliquary.3", StoryRegion.RELIQUARY, BarkTone.LORE);
        killLine(registry, "bark.kill.reliquary.4", StoryRegion.RELIQUARY, BarkTone.LORE);

        for (int lineNumber = 1; lineNumber <= 4; lineNumber++) {
            String id = "bark.kill.wound." + lineNumber;
            registry.register(row(id, storyIdFor(id))
                    .trigger(BarkTrigger.KILL).regionFrom(StoryRegion.WOUND).build());
        }
    }

    private static void killLine(BarkRegistry registry, String id, StoryRegion region, BarkTone tone) {
        registry.register(row(id, storyIdFor(id))
                .trigger(BarkTrigger.KILL).region(region).tone(tone).build());
    }

    // -------------------------------------------------------------------------
    // Low health — two lines per tone stage.  Never a joke below the Galleries.
    //
    // narrative-rework order-6 C re-banded mid.2 to the Reliquary and below: "every reprint costs
    // something, I know what now" is only true once she has read the cradle log, so in the Galleries
    // it had her knowing a thing the story has not told her (or the player) yet.  mid.3 is what keeps
    // the Galleries two rows deep after that move — a band with one row is a band that goes silent
    // for the rest of the save the first time it fires.
    // -------------------------------------------------------------------------
    private static void registerLowHealth(BarkRegistry registry) {
        lowHealthLine(registry, "bark.lowhealth.rings.1",
                      StoryRegion.HABITATION_RINGS, StoryRegion.HABITATION_RINGS, BarkTone.LEVITY);
        lowHealthLine(registry, "bark.lowhealth.rings.2",
                      StoryRegion.HABITATION_RINGS, StoryRegion.HABITATION_RINGS, BarkTone.LEVITY);
        lowHealthLine(registry, "bark.lowhealth.mid.1",
                      StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY, BarkTone.LORE);
        lowHealthLine(registry, "bark.lowhealth.mid.3",
                      StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY, BarkTone.LORE);
        lowHealthLine(registry, "bark.lowhealth.mid.2",
                      StoryRegion.RELIQUARY, StoryRegion.CORE, BarkTone.LORE);
        lowHealthLine(registry, "bark.lowhealth.deep.1",
                      StoryRegion.WOUND, StoryRegion.CORE, BarkTone.LORE);
        lowHealthLine(registry, "bark.lowhealth.deep.2",
                      StoryRegion.WOUND, StoryRegion.CORE, BarkTone.LORE);
    }

    private static void lowHealthLine(BarkRegistry registry, String id,
                                      StoryRegion firstRegion, StoryRegion lastRegion, BarkTone tone) {
        registry.register(row(id, storyIdFor(id))
                .trigger(BarkTrigger.LOW_HEALTH).regionBand(firstRegion, lastRegion).tone(tone).build());
    }

    // -------------------------------------------------------------------------
    // Deep-strata milestones — THE PLANET.  No rows in Region 1: that silence is the design.
    // Every planet line is LORE; it has never made a joke in its life.
    // -------------------------------------------------------------------------
    private static void registerDeepStrata(BarkRegistry registry) {
        // Region 2 — whispers: single wrong-language words, easy to blame on the deep.
        for (int lineNumber = 1; lineNumber <= 4; lineNumber++) {
            planetLine(registry, "bark.strata.galleries." + lineNumber,
                       StoryRegion.HARVESTING_GALLERIES, StoryRegion.HARVESTING_GALLERIES);
        }
        // Region 3 — address: it knows you, by deed, not by serial.
        for (int lineNumber = 1; lineNumber <= 4; lineNumber++) {
            planetLine(registry, "bark.strata.reliquary." + lineNumber,
                       StoryRegion.RELIQUARY, StoryRegion.RELIQUARY);
        }
        // Region 4 -> Core — communion: plain, gentle, terrible.
        for (int lineNumber = 1; lineNumber <= 4; lineNumber++) {
            planetLine(registry, "bark.strata.wound." + lineNumber,
                       StoryRegion.WOUND, StoryRegion.CORE);
        }
    }

    private static void planetLine(BarkRegistry registry, String id,
                                   StoryRegion firstRegion, StoryRegion lastRegion) {
        registry.register(row(id, storyIdFor(id))
                .speaker(Speaker.PLANET)
                .trigger(BarkTrigger.DEEP_STRATA)
                .regionBand(firstRegion, lastRegion)
                .build());
    }

    // -------------------------------------------------------------------------
    // Idle / backtracking — the flavour pools, dropped first under any pressure and on the
    // longest cooldowns in the table.  This is where ORA is allowed to be funny most often.
    // -------------------------------------------------------------------------
    private static void registerIdleAndBacktrack(BarkRegistry registry) {
        flavourLine(registry, BarkTrigger.IDLE, "bark.idle.rings.1",
                    StoryRegion.HABITATION_RINGS, StoryRegion.HABITATION_RINGS, BarkTone.LEVITY);
        flavourLine(registry, BarkTrigger.IDLE, "bark.idle.rings.2",
                    StoryRegion.HABITATION_RINGS, StoryRegion.HABITATION_RINGS, BarkTone.LEVITY);
        flavourLine(registry, BarkTrigger.IDLE, "bark.idle.mid.1",
                    StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY, BarkTone.LEVITY);
        // idle.mid.2 (the unobservable "resonance") was CUT by narrative-rework order-6 B; .3 replaces
        // it and keeps the band two rows deep.
        flavourLine(registry, BarkTrigger.IDLE, "bark.idle.mid.3",
                    StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY, BarkTone.LORE);
        flavourLine(registry, BarkTrigger.IDLE, "bark.idle.deep.1",
                    StoryRegion.WOUND, StoryRegion.CORE, BarkTone.LORE);
        flavourLine(registry, BarkTrigger.IDLE, "bark.idle.deep.2",
                    StoryRegion.WOUND, StoryRegion.CORE, BarkTone.LORE);

        flavourLine(registry, BarkTrigger.BACKTRACK, "bark.backtrack.rings.1",
                    StoryRegion.HABITATION_RINGS, StoryRegion.HABITATION_RINGS, BarkTone.LEVITY);
        flavourLine(registry, BarkTrigger.BACKTRACK, "bark.backtrack.rings.2",
                    StoryRegion.HABITATION_RINGS, StoryRegion.HABITATION_RINGS, BarkTone.LEVITY);
        flavourLine(registry, BarkTrigger.BACKTRACK, "bark.backtrack.mid.1",
                    StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY, BarkTone.LORE);
        flavourLine(registry, BarkTrigger.BACKTRACK, "bark.backtrack.mid.2",
                    StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY, BarkTone.LORE);
        flavourLine(registry, BarkTrigger.BACKTRACK, "bark.backtrack.deep.1",
                    StoryRegion.WOUND, StoryRegion.CORE, BarkTone.LORE);
        // backtrack.deep.2 ("it moves the rooms") was CUT by narrative-rework order-6 B — the rooms do
        // not move, and a line that tells the player the world does something it does not do is worse
        // than silence.  .3 replaces it and keeps the band two rows deep.
        flavourLine(registry, BarkTrigger.BACKTRACK, "bark.backtrack.deep.3",
                    StoryRegion.WOUND, StoryRegion.CORE, BarkTone.LORE);
    }

    private static void flavourLine(BarkRegistry registry, BarkTrigger trigger, String id,
                                    StoryRegion firstRegion, StoryRegion lastRegion, BarkTone tone) {
        registry.register(row(id, storyIdFor(id))
                .trigger(trigger)
                .priority(BarkPriority.FLAVOR)
                .regionBand(firstRegion, lastRegion)
                .tone(tone)
                .build());
    }

    // -------------------------------------------------------------------------
    // CODEX COMPLETION (order-6 Part A) — the "completion perk", which is deliberately just ORA
    // noticing.  One one-shot line per category, region-unrestricted because a player may fill a tab
    // at any depth, and REACTIVE rather than critical: it is a nice thing to hear, and a player who
    // never sees it has lost nothing.  Paying completion in POWER would turn an opt-in archive into
    // a chore, which is the one thing order-6 must not do.
    // -------------------------------------------------------------------------
    private static void registerCodexCompletion(BarkRegistry registry) {
        for (CodexCategory category : CodexCategory.values()) {
            String id = "bark.codex.complete." + category.getCatalogKey();
            registry.register(row(id, storyIdFor(id))
                    .trigger(BarkTrigger.CODEX_COMPLETE)
                    .subjectKey(category.getCatalogKey())
                    .oneShot(true)
                    .build());
        }
    }

    /** Shared row prologue: ORA, reactive priority, lore tone — the defaults most rows keep. */
    private static BarkDefinition.Builder row(String id, String textStringId) {
        return BarkDefinition.builder(id)
                .speaker(Speaker.AI)
                .textStringId(textStringId)
                .priority(BarkPriority.REACTIVE)
                .tone(BarkTone.LORE);
    }

    /**
     * The localisation id for a row id.  Row ids and string ids are kept in lockstep by
     * construction ({@code bark.foo.1} -> {@code story.bark.foo.1}), so a new line cannot be
     * registered against a mistyped string id — a test resolves every one of them.
     */
    private static String storyIdFor(String barkId) {
        return "story." + barkId;
    }
}
