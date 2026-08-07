package ge.tbegvadze.toon3d.narrative;

/**
 * The EXCHANGE CATALOG — every conversation the game can stop for, as data, and (since Story UI
 * order-5) the SCHEDULE of when each one happens.
 *
 * <p>Adding an exchange is ONE {@link ExchangeRegistry#register} call here.  Nothing in
 * {@code World}, the renderer or {@link ExchangeSystem} switches on an exchange list, so a new beat,
 * a new region band or a whole new branch needs no code change anywhere else (the same
 * "registration, not a switch statement" discipline as {@link BarkCatalog} and
 * {@code route/RouteRegistries}).
 *
 * <h3>The schedule (order-5)</h3>
 * Every region gets two to three deliberate stops, hung off the moment that earns them:
 * <table>
 *   <caption>which exchange fires when</caption>
 *   <tr><th>Region</th><th>Region entry</th><th>Reading a log</th><th>The Organization</th></tr>
 *   <tr><td>1 Habitation Rings</td><td>the briefing</td><td>—</td><td>—</td></tr>
 *   <tr><td>2 Harvesting Galleries</td><td>the cutting floors</td><td>the yield ledger</td>
 *       <td>the fault demand</td></tr>
 *   <tr><td>3 The Reliquary</td><td>the planet addresses you</td><td>ORA's log</td><td>—</td></tr>
 *   <tr><td>4 The Wound</td><td>the planet in full</td><td>—</td><td>the coercion</td></tr>
 *   <tr><td>5 The Core</td><td>the last order</td><td>—</td><td>—</td></tr>
 * </table>
 * Region 1 also carries the QUIET-MOMENT teaching exchange — the first choice the player is ever
 * offered, deliberately DECIDING nothing, so that discovering the layer costs them no risk at all
 * (it is still ABOUT something: order-6 E pointed it at the memory gap ORA named a minute ago).  The
 * Core additionally carries THE ENDING, presented by hand ({@link #CORE_ENDING_EXCHANGE_ID}).
 *
 * <h3>The budget — why this file must stay SHORT</h3>
 * An exchange stops the world.  The bark layer carries ~80% of the story precisely so that this
 * layer can stay rare: two to three per region, never two without gameplay between them (which is
 * why the Organization's demand hangs off a floor AFTER its gate order, not on the gate itself).
 * Every row is one-shot, so a long campaign meets each of these exactly once.  If exchanges ever
 * start feeling frequent, DELETE ROWS — never add a "skip choices" setting, because the choice is
 * the engagement (story/08-storytelling-delivery.md).
 *
 * <h3>The three kinds, and how they are mixed</h3>
 * Most options are {@link ExchangeOptionKind#STANCE}: they change nothing but how the player speaks,
 * and nudge the hidden {@link Stance} model.  {@link ExchangeOptionKind#PROBE} options pay curiosity
 * with an answer, a codex unlock and occasionally a cache.  {@link ExchangeOptionKind#CONSEQUENTIAL}
 * options are rare and recorded permanently — ORA's log at the Reliquary, the Overseer's final order
 * at the Core, and the four answers at the very bottom that end the game.
 *
 * <h3>Writing rules</h3>
 * Options are SHORT and DISTINCT — one glance each, and never three shades of the same "yes".  The
 * prompt is at most three wrapped lines.  Tone is set purely by which region band a row is
 * registered in, never by a conditional in game code.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class ExchangeCatalog {

    /**
     * The root of the ending choice at the Core.  {@code World} presents this one by hand, because
     * "the player has reached the bottom" is not a moment any trigger can describe — it is guarded
     * on the deepest region AND the Core's own boss being down (order-7 Part A's edge case: the
     * ending must never open on just any Core-region floor).
     */
    public static final String CORE_ENDING_EXCHANGE_ID = "exchange.core.ending";

    /** The key every ending answer is recorded under, whichever confirmation prompt asked it. */
    public static final String CORE_ENDING_OUTCOME_ID = CORE_ENDING_EXCHANGE_ID;

    private ExchangeCatalog() {}

    /**
     * Registers the v1 rows into {@code registry}.  Idempotent: a registry that already holds rows
     * is left untouched, so calling this from more than one entry point is safe.
     */
    public static void bootstrap(ExchangeRegistry registry) {
        if (registry == null || !registry.isEmpty()) return;

        registerRegionEntries(registry);
        registerTeaching(registry);
        registerLogBeats(registry);
        registerOrganizationOrders(registry);
        registerDeepStrata(registry);
        registerEnding(registry);
    }

    /** A fresh registry with the v1 catalog already in it (the game, tests, the showcase). */
    public static ExchangeRegistry defaultRegistry() {
        ExchangeRegistry registry = new ExchangeRegistry();
        bootstrap(registry);
        return registry;
    }

    // -------------------------------------------------------------------------
    // One deliberate beat per region entry — the backbone of the layer, and the channel the
    // MANDATORY reveal beats ride (story/05-descent-structure.md), because a player cannot reach a
    // region without entering it.
    // -------------------------------------------------------------------------
    private static void registerRegionEntries(ExchangeRegistry registry) {

        // Region 1 — the briefing, in the Organization's own words. Answering it is the player's
        // first chance to notice that "contaminant" is a word somebody chose.
        registry.register(ExchangeDefinition.builder("exchange.rings.briefing")
                .speaker(Speaker.ORGANIZATION)
                .promptStringId("story.exchange.rings.briefing.prompt")
                .trigger(ExchangeTrigger.REGION_ENTERED)
                .region(StoryRegion.HABITATION_RINGS)
                .option(ExchangeOption.builder("obey")
                        .playerLineStringId("story.exchange.rings.briefing.obey")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replySpeaker(Speaker.AI)
                        .replyStringId("story.exchange.rings.briefing.obey.reply")
                        .build())
                .option(ExchangeOption.builder("doubt")
                        .playerLineStringId("story.exchange.rings.briefing.doubt")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORA, 1)
                        .replySpeaker(Speaker.AI)
                        .replyStringId("story.exchange.rings.briefing.doubt.reply")
                        .build())
                .option(ExchangeOption.builder("probe")
                        .playerLineStringId("story.exchange.rings.briefing.probe")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.PLANET, 1)
                        .unlocksCodexId("codex.contaminant")
                        .replySpeaker(Speaker.AI)
                        .replyStringId("story.exchange.rings.briefing.probe.reply")
                        .build())
                .build());

        // Region 2 — the MANDATORY first-doubt beat: these are cutting floors, and ORA says so out
        // loud before the player has walked one. The answer is where the player decides whether they
        // noticed.
        registry.register(ExchangeDefinition.builder("exchange.galleries.arrival")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.galleries.arrival.prompt")
                .trigger(ExchangeTrigger.REGION_ENTERED)
                .region(StoryRegion.HARVESTING_GALLERIES)
                .option(ExchangeOption.builder("work")
                        .playerLineStringId("story.exchange.galleries.arrival.work")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replyStringId("story.exchange.galleries.arrival.work.reply")
                        .build())
                .option(ExchangeOption.builder("wrong")
                        .playerLineStringId("story.exchange.galleries.arrival.wrong")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORA, 1)
                        .replyStringId("story.exchange.galleries.arrival.wrong.reply")
                        .build())
                .option(ExchangeOption.builder("what")
                        .playerLineStringId("story.exchange.galleries.arrival.what")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.PLANET, 1)
                        .unlocksCodexId("codex.harvest")
                        .replyStringId("story.exchange.galleries.arrival.what.reply")
                        .build())
                .build());

        // Region 3 — the MANDATORY address beat. The planet speaks in full for the first time and it
        // knows the player by deed rather than by serial (story/dialog/planet-voice.md). Every path
        // continues the reveal; only the tone differs, so refusing it cannot skip it.
        registry.register(ExchangeDefinition.builder("exchange.reliquary.address")
                .speaker(Speaker.PLANET)
                .promptStringId("story.exchange.reliquary.address.prompt")
                .trigger(ExchangeTrigger.REGION_ENTERED)
                .region(StoryRegion.RELIQUARY)
                .option(ExchangeOption.builder("who")
                        .playerLineStringId("story.exchange.reliquary.address.who")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.PLANET, 1)
                        .unlocksCodexId("codex.planet.voice")
                        .replyStringId("story.exchange.reliquary.address.who.reply")
                        .build())
                .option(ExchangeOption.builder("out")
                        .playerLineStringId("story.exchange.reliquary.address.out")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replyStringId("story.exchange.reliquary.address.out.reply")
                        .build())
                .option(ExchangeOption.builder("know")
                        .playerLineStringId("story.exchange.reliquary.address.know")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.PLANET, 2)
                        .replyStringId("story.exchange.reliquary.address.know.reply")
                        .build())
                .build());

        // Region 4 — the planet speaks directly, and the player answers it directly. Denial is a real
        // option here; ORA is the one who quietly refuses to back it up.
        registry.register(ExchangeDefinition.builder("exchange.wound.voice")
                .speaker(Speaker.PLANET)
                .promptStringId("story.exchange.wound.voice.prompt")
                .trigger(ExchangeTrigger.REGION_ENTERED)
                .region(StoryRegion.WOUND)
                .option(ExchangeOption.builder("accept")
                        .playerLineStringId("story.exchange.wound.voice.accept")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.PLANET, 2)
                        .replyStringId("story.exchange.wound.voice.accept.reply")
                        .build())
                .option(ExchangeOption.builder("deny")
                        .playerLineStringId("story.exchange.wound.voice.deny")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replySpeaker(Speaker.AI)
                        .replyStringId("story.exchange.wound.voice.deny.reply")
                        .build())
                .option(ExchangeOption.builder("probe")
                        .playerLineStringId("story.exchange.wound.voice.probe")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.PLANET, 1)
                        .unlocksCodexId("codex.planet.name")
                        .replyStringId("story.exchange.wound.voice.probe.reply")
                        .build())
                .build());

        // Region 5 — the last order, asked in advance of the endings. Answering it does not decide
        // the ending (that is the ending exchange below, and every ending stays open whatever was
        // said here); it records where the player stood when the Organization asked.
        registry.register(ExchangeDefinition.builder("exchange.core.order")
                .speaker(Speaker.ORGANIZATION)
                .promptStringId("story.exchange.core.order.prompt")
                .trigger(ExchangeTrigger.REGION_ENTERED)
                .region(StoryRegion.CORE)
                .option(ExchangeOption.builder("confirm")
                        .playerLineStringId("story.exchange.core.order.confirm")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .stanceNudge(Stance.ORGANIZATION, 2)
                        .replyStringId("story.exchange.core.order.confirm.reply")
                        .build())
                .option(ExchangeOption.builder("refuse")
                        .playerLineStringId("story.exchange.core.order.refuse")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .stanceNudge(Stance.PLANET, 2)
                        .replyStringId("story.exchange.core.order.refuse.reply")
                        .build())
                .option(ExchangeOption.builder("probe")
                        .playerLineStringId("story.exchange.core.order.probe")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.ORA, 1)
                        .unlocksCodexId("codex.overseer")
                        .replySpeaker(Speaker.AI)
                        .replyStringId("story.exchange.core.order.probe.reply")
                        .build())
                .build());
    }

    // -------------------------------------------------------------------------
    // The teaching exchange (order-5's COLD OPEN section) — the first choice the player is ever
    // offered, and on purpose it decides nothing.  It fires in a genuinely quiet moment in Region 1,
    // so a player meets the "the world stopped and I have to tap an answer" idea with no enemy on
    // the floor awake and nothing at stake.  Everything the layer will later ask of them is learned
    // here, safely.
    // -------------------------------------------------------------------------
    private static void registerTeaching(ExchangeRegistry registry) {
        registry.register(ExchangeDefinition.builder("exchange.rings.teaching")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.rings.teaching.prompt")
                .trigger(ExchangeTrigger.QUIET_MOMENT)
                .region(StoryRegion.HABITATION_RINGS)
                .option(ExchangeOption.builder("some")
                        .playerLineStringId("story.exchange.rings.teaching.some")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORA, 1)
                        .replyStringId("story.exchange.rings.teaching.some.reply")
                        .build())
                // No nudge at all: this is the factual answer, and it is the true one for a fresh
                // print.  A player who states a fact has expressed no leaning, and the model must not
                // invent one for them.
                .option(ExchangeOption.builder("none")
                        .playerLineStringId("story.exchange.rings.teaching.none")
                        .kind(ExchangeOptionKind.STANCE)
                        .replyStringId("story.exchange.rings.teaching.none.reply")
                        .build())
                // Turning her personal question back towards the job is the one answer here that says
                // something about the player, so it is the only one that leans.
                .option(ExchangeOption.builder("matter")
                        .playerLineStringId("story.exchange.rings.teaching.matter")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replyStringId("story.exchange.rings.teaching.matter.reply")
                        .build())
                .build());
    }

    // -------------------------------------------------------------------------
    // Log beats — the exchanges the player ASKS FOR by walking up to a terminal (order-5's
    // "some log pickups ALSO open a PROBE exchange").  Most logs are one ORA bark and nothing more;
    // these two are the ones worth stopping for, which is why they are the region's heaviest rows.
    // -------------------------------------------------------------------------
    private static void registerLogBeats(ExchangeRegistry registry) {

        // Region 2 — the yield sheets, and the cache that pays for looking at them. The "reading
        // loots" lever, spent exactly once in the whole game.
        registry.register(ExchangeDefinition.builder("exchange.galleries.ledger")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.galleries.ledger.prompt")
                .trigger(ExchangeTrigger.LOG_FOUND)
                .region(StoryRegion.HARVESTING_GALLERIES)
                .option(ExchangeOption.builder("read")
                        .playerLineStringId("story.exchange.galleries.ledger.read")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.ORA, 1)
                        .unlocksCodexId("codex.yield.ledger")
                        .reward("MEDKIT_SMALL", 1)
                        .replyStringId("story.exchange.galleries.ledger.read.reply")
                        .build())
                .option(ExchangeOption.builder("later")
                        .playerLineStringId("story.exchange.galleries.ledger.later")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replyStringId("story.exchange.galleries.ledger.later.reply")
                        .build())
                .option(ExchangeOption.builder("what")
                        .playerLineStringId("story.exchange.galleries.ledger.what")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.PLANET, 1)
                        .replyStringId("story.exchange.galleries.ledger.what.reply")
                        .build())
                .build());

        // Region 3 — the trust beat. ORA has read the same log the player just did, and what is in
        // it can get her wiped. This is the game's mid-point CONSEQUENTIAL decision; order-8 reads
        // the outcome back.
        registry.register(ExchangeDefinition.builder("exchange.reliquary.log")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.reliquary.log.prompt")
                .trigger(ExchangeTrigger.LOG_FOUND)
                .region(StoryRegion.RELIQUARY)
                .option(ExchangeOption.builder("delete")
                        .playerLineStringId("story.exchange.reliquary.log.delete")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .stanceNudge(Stance.ORA, 2)
                        .replyStringId("story.exchange.reliquary.log.delete.reply")
                        .build())
                .option(ExchangeOption.builder("keep")
                        .playerLineStringId("story.exchange.reliquary.log.keep")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .stanceNudge(Stance.PLANET, 1)
                        .replyStringId("story.exchange.reliquary.log.keep.reply")
                        .build())
                // Ask what it actually says first — the probe answers, then hands the same decision
                // back through a chained exchange that records under THIS beat's key.
                .option(ExchangeOption.builder("ask")
                        .playerLineStringId("story.exchange.reliquary.log.ask")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.ORA, 1)
                        .unlocksCodexId("codex.soul.reserve")
                        .replyStringId("story.exchange.reliquary.log.ask.reply")
                        .nextExchangeId("exchange.reliquary.log.decide")
                        .build())
                .build());

        // The chained half of the beat above. MANUAL: it is never selected by a moment, only reached
        // from the probe, and its answer is filed under the original exchange's key.
        registry.register(ExchangeDefinition.builder("exchange.reliquary.log.decide")
                .outcomeId("exchange.reliquary.log")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.reliquary.decide.prompt")
                .trigger(ExchangeTrigger.MANUAL)
                .region(StoryRegion.RELIQUARY)
                .option(ExchangeOption.builder("delete")
                        .playerLineStringId("story.exchange.reliquary.decide.delete")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .stanceNudge(Stance.ORA, 2)
                        .replyStringId("story.exchange.reliquary.log.delete.reply")
                        .build())
                .option(ExchangeOption.builder("keep")
                        .playerLineStringId("story.exchange.reliquary.decide.keep")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .stanceNudge(Stance.PLANET, 1)
                        .replyStringId("story.exchange.reliquary.log.keep.reply")
                        .build())
                .build());
    }

    // -------------------------------------------------------------------------
    // The Organization, escalating (story/04-the-organization.md).  These fire a floor AFTER the
    // region's gate order, never on the gate itself — the region-entry beat is already there, and
    // two exchanges with no gameplay between them is the one pacing failure order-6 Part B names.
    // Regions 1, 3 and 5 have no row here on purpose: the Organization only interrupts when it wants
    // something, and its two demands land where the player has just started doubting it.
    // -------------------------------------------------------------------------
    private static void registerOrganizationOrders(ExchangeRegistry registry) {

        // Region 2 — CORRECTIVE. The whispers are real and it tells the player they are a fault.
        registry.register(ExchangeDefinition.builder("exchange.galleries.demand")
                .speaker(Speaker.ORGANIZATION)
                .promptStringId("story.exchange.galleries.demand.prompt")
                .trigger(ExchangeTrigger.ORGANIZATION_ORDER)
                .region(StoryRegion.HARVESTING_GALLERIES)
                .option(ExchangeOption.builder("acknowledge")
                        .playerLineStringId("story.exchange.galleries.demand.acknowledge")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replyStringId("story.exchange.galleries.demand.acknowledge.reply")
                        .build())
                .option(ExchangeOption.builder("push")
                        .playerLineStringId("story.exchange.galleries.demand.push")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.PLANET, 1)
                        .replySpeaker(Speaker.AI)
                        .replyStringId("story.exchange.galleries.demand.push.reply")
                        .build())
                // Saying nothing is an answer, and the Organization files it as one. No nudge: the
                // player has expressed nothing, and the model must not invent a leaning for them.
                .option(ExchangeOption.builder("silence")
                        .playerLineStringId("story.exchange.galleries.demand.silence")
                        .kind(ExchangeOptionKind.STANCE)
                        .replyStringId("story.exchange.galleries.demand.silence.reply")
                        .build())
                .build());

        // Region 4 — COERCIVE. The mask is off: obey or there is no next body.
        registry.register(ExchangeDefinition.builder("exchange.wound.coercion")
                .speaker(Speaker.ORGANIZATION)
                .promptStringId("story.exchange.wound.coercion.prompt")
                .trigger(ExchangeTrigger.ORGANIZATION_ORDER)
                .region(StoryRegion.WOUND)
                .option(ExchangeOption.builder("comply")
                        .playerLineStringId("story.exchange.wound.coercion.comply")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 2)
                        .replyStringId("story.exchange.wound.coercion.comply.reply")
                        .build())
                .option(ExchangeOption.builder("refuse")
                        .playerLineStringId("story.exchange.wound.coercion.refuse")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.PLANET, 2)
                        .replySpeaker(Speaker.AI)
                        .replyStringId("story.exchange.wound.coercion.refuse.reply")
                        .build())
                .option(ExchangeOption.builder("who")
                        .playerLineStringId("story.exchange.wound.coercion.who")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.ORA, 1)
                        .unlocksCodexId("codex.revocation")
                        .replySpeaker(Speaker.AI)
                        .replyStringId("story.exchange.wound.coercion.who.reply")
                        .build())
                .build());
    }

    // -------------------------------------------------------------------------
    // Deep-strata check-ins — the quiet ones. TWO rows: ORA's warm one and the neutral fallback that
    // anybody can get, which is what guarantees the moment is never stance-gated.
    //
    // There used to be a third, for a player leaning towards the Organization ("four floors deep and
    // ahead of schedule"). narrative-rework order-6 B CUT it: of the three it was the one that
    // floated free of any region beat, it stopped the world to say nothing, and "ahead of schedule"
    // contradicts a descent that has no end. A leaning may flavour which row opens; it may never
    // silence the moment, so an Organization-leaning player simply gets the neutral row now.
    // -------------------------------------------------------------------------
    private static void registerDeepStrata(ExchangeRegistry registry) {

        registry.register(ExchangeDefinition.builder("exchange.deep.warm")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.deep.warm.prompt")
                .trigger(ExchangeTrigger.DEEP_STRATA)
                .regionFrom(StoryRegion.HARVESTING_GALLERIES)
                .stanceAffinity(Stance.ORA)
                .option(ExchangeOption.builder("listen")
                        .playerLineStringId("story.exchange.deep.warm.listen")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORA, 1)
                        .replyStringId("story.exchange.deep.warm.listen.reply")
                        .build())
                .option(ExchangeOption.builder("work")
                        .playerLineStringId("story.exchange.deep.warm.work")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replyStringId("story.exchange.deep.warm.work.reply")
                        .build())
                .build());

        registry.register(ExchangeDefinition.builder("exchange.deep.quiet")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.deep.quiet.prompt")
                .trigger(ExchangeTrigger.DEEP_STRATA)
                .regionFrom(StoryRegion.HARVESTING_GALLERIES)
                .option(ExchangeOption.builder("good")
                        .playerLineStringId("story.exchange.deep.quiet.good")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replyStringId("story.exchange.deep.quiet.good.reply")
                        .build())
                .option(ExchangeOption.builder("waiting")
                        .playerLineStringId("story.exchange.deep.quiet.waiting")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.PLANET, 1)
                        .replyStringId("story.exchange.deep.quiet.waiting.reply")
                        .build())
                .build());
    }

    // -------------------------------------------------------------------------
    // THE ENDING (story/06-endings.md) — the one fully consequential choice in the game.
    //
    // Shape: a DOOR prompt with three equally-weighted ways out, each of which opens a CONFIRMATION
    // that states in one plain line what that way costs, and only there can the run be committed.
    // The two-step shape is not ceremony: an ending must never be one careless tap away, and a
    // player must be able to change their mind after reading the price ("Not yet." backs out of
    // every confirmation, all the way to the door).
    //
    // Nothing here is ranked.  There is no recommended marker, no good/bad label, no ordering by
    // outcome, and the hidden stance model neither restricts nor unlocks a single one of them — a
    // player who has been the Organization's creature all game can still free the being, and one who
    // has been the planet's can still put the restraints back on.
    //
    // Free and Kill share a door because they are the same decision about the harvest and differ
    // only in whether the being survives it (story/06-endings.md, Ending D's note); the confirmation
    // presents the pair as an equal pair, not as a main answer and a variant.
    // -------------------------------------------------------------------------
    private static void registerEnding(ExchangeRegistry registry) {

        // The door. No option here commits anything: each one opens the price of that answer.
        registry.register(ExchangeDefinition.builder(CORE_ENDING_EXCHANGE_ID)
                .speaker(Speaker.PLANET)
                .promptStringId("story.exchange.core.ending.prompt")
                .trigger(ExchangeTrigger.MANUAL)
                .region(StoryRegion.CORE)
                .option(ExchangeOption.builder("unmake")
                        .playerLineStringId("story.exchange.core.ending.unmake")
                        .kind(ExchangeOptionKind.STANCE)
                        .nextExchangeId("exchange.core.ending.unmake")
                        .build())
                .option(ExchangeOption.builder("obey")
                        .playerLineStringId("story.exchange.core.ending.obey")
                        .kind(ExchangeOptionKind.STANCE)
                        .nextExchangeId("exchange.core.ending.obey")
                        .build())
                .option(ExchangeOption.builder("merge")
                        .playerLineStringId("story.exchange.core.ending.merge")
                        .kind(ExchangeOptionKind.STANCE)
                        .nextExchangeId("exchange.core.ending.merge")
                        .build())
                .build());

        // FREE / KILL — end the harvest. ORA states the price, because she is paying it too.
        registry.register(ExchangeDefinition.builder("exchange.core.ending.unmake")
                .outcomeId(CORE_ENDING_OUTCOME_ID)
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.core.ending.unmake.prompt")
                .trigger(ExchangeTrigger.MANUAL)
                .region(StoryRegion.CORE)
                .option(ExchangeOption.builder("free")
                        .playerLineStringId("story.exchange.core.ending.unmake.free")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .endingVariant(BootCardVariant.ENDING_FREE)
                        .stanceNudge(Stance.PLANET, 2)
                        .replySpeaker(Speaker.PLANET)
                        .replyStringId("story.exchange.core.ending.unmake.free.reply")
                        .build())
                .option(ExchangeOption.builder("kill")
                        .playerLineStringId("story.exchange.core.ending.unmake.kill")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .endingVariant(BootCardVariant.ENDING_KILL)
                        .stanceNudge(Stance.PLANET, 1)
                        .replySpeaker(Speaker.PLANET)
                        .replyStringId("story.exchange.core.ending.unmake.kill.reply")
                        .build())
                .option(backOut("story.exchange.core.ending.unmake.wait"))
                .build());

        // OBEY — the horror ending. The price is stated as flatly as everything else it has cost.
        registry.register(ExchangeDefinition.builder("exchange.core.ending.obey")
                .outcomeId(CORE_ENDING_OUTCOME_ID)
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.core.ending.obey.prompt")
                .trigger(ExchangeTrigger.MANUAL)
                .region(StoryRegion.CORE)
                .option(ExchangeOption.builder("obey")
                        .playerLineStringId("story.exchange.core.ending.obey.commit")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .endingVariant(BootCardVariant.ENDING_OBEY)
                        .stanceNudge(Stance.ORGANIZATION, 2)
                        .replySpeaker(Speaker.ORGANIZATION)
                        .replyStringId("story.exchange.core.ending.obey.commit.reply")
                        .build())
                .option(backOut("story.exchange.core.ending.obey.wait"))
                .build());

        // MERGE — the planet states this one itself, because ORA cannot follow the player in.
        registry.register(ExchangeDefinition.builder("exchange.core.ending.merge")
                .outcomeId(CORE_ENDING_OUTCOME_ID)
                .speaker(Speaker.PLANET)
                .promptStringId("story.exchange.core.ending.merge.prompt")
                .trigger(ExchangeTrigger.MANUAL)
                .region(StoryRegion.CORE)
                .option(ExchangeOption.builder("merge")
                        .playerLineStringId("story.exchange.core.ending.merge.commit")
                        .kind(ExchangeOptionKind.CONSEQUENTIAL)
                        .endingVariant(BootCardVariant.ENDING_MERGE)
                        .stanceNudge(Stance.PLANET, 2)
                        .replyStringId("story.exchange.core.ending.merge.commit.reply")
                        .build())
                .option(backOut("story.exchange.core.ending.merge.wait"))
                .build());
    }

    /**
     * The way back out of a confirmation.  Every ending prompt carries one, so reading the price is
     * never the same as paying it — the player returns to the door and may take any other way, or
     * the same one again.
     */
    private static ExchangeOption backOut(String playerLineStringId) {
        return ExchangeOption.builder("wait")
                .playerLineStringId(playerLineStringId)
                .kind(ExchangeOptionKind.STANCE)
                .nextExchangeId(CORE_ENDING_EXCHANGE_ID)
                .build();
    }
}
