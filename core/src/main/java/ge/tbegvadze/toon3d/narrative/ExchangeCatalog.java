package ge.tbegvadze.toon3d.narrative;

/**
 * The v1 EXCHANGE CATALOG (Story UI order-4) — every conversation the game can stop for, as data.
 *
 * <p>Adding an exchange is ONE {@link ExchangeRegistry#register} call here.  Nothing in
 * {@code World}, the renderer or {@link ExchangeSystem} switches on an exchange list, so a new beat,
 * a new region band or a whole new branch needs no code change anywhere else (the same
 * "registration, not a switch statement" discipline as {@link BarkCatalog} and
 * {@code route/RouteRegistries}).
 *
 * <h3>The budget — why this file is SHORT</h3>
 * An exchange stops the world.  The bark layer carries ~80% of the story precisely so that this
 * layer can stay rare: one deliberate beat per region entry, plus a couple of quieter deep-strata
 * check-ins.  Every row is one-shot, so a long campaign meets each of these exactly once.  If
 * exchanges ever start feeling frequent, DELETE ROWS — never add a "skip choices" setting, because
 * the choice is the engagement (story/08-storytelling-delivery.md).
 *
 * <h3>The three kinds, and how they are mixed</h3>
 * Most options are {@link ExchangeOptionKind#STANCE}: they change nothing but how the player speaks,
 * and nudge the hidden {@link Stance} model.  {@link ExchangeOptionKind#PROBE} options pay curiosity
 * with an answer, a codex unlock and occasionally a cache.  {@link ExchangeOptionKind#CONSEQUENTIAL}
 * options are rare and recorded permanently — in v1 that is ORA's log at the Reliquary and the
 * Overseer's final order at the Core.
 *
 * <h3>Writing rules</h3>
 * Options are SHORT and DISTINCT — one glance each, and never three shades of the same "yes".  The
 * prompt is at most three wrapped lines.  Tone is set purely by which region band a row is
 * registered in, never by a conditional in game code.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class ExchangeCatalog {

    private ExchangeCatalog() {}

    /**
     * Registers the v1 rows into {@code registry}.  Idempotent: a registry that already holds rows
     * is left untouched, so calling this from more than one entry point is safe.
     */
    public static void bootstrap(ExchangeRegistry registry) {
        if (registry == null || !registry.isEmpty()) return;

        registerRegionEntries(registry);
        registerDeepStrata(registry);
    }

    /** A fresh registry with the v1 catalog already in it (the game, tests, the showcase). */
    public static ExchangeRegistry defaultRegistry() {
        ExchangeRegistry registry = new ExchangeRegistry();
        bootstrap(registry);
        return registry;
    }

    // -------------------------------------------------------------------------
    // One deliberate beat per region entry — the backbone of the layer.
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

        // Region 2 — ORA's first doubt, offered as a question rather than stated. Reading the ledger
        // pays out a cache: the "reading loots" lever, spent exactly once.
        registry.register(ExchangeDefinition.builder("exchange.galleries.ledger")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.galleries.ledger.prompt")
                .trigger(ExchangeTrigger.REGION_ENTERED)
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

        // Region 3 — the trust beat. ORA has written down something that can get her wiped, and asks
        // the player to decide what happens to it. This is the v1 CONSEQUENTIAL exchange.
        registry.register(ExchangeDefinition.builder("exchange.reliquary.log")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.reliquary.log.prompt")
                .trigger(ExchangeTrigger.REGION_ENTERED)
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
        // the ending (order-5 owns that, and every ending stays open); it records where the player
        // stood when the Organization asked.
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
    // Deep-strata check-ins — the quiet ones. Three rows, one per leaning plus a neutral fallback,
    // so the SAME moment reads differently for a player who has been warm to ORA and one who has
    // been all business.  The neutral row is what guarantees the moment is never stance-gated.
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

        registry.register(ExchangeDefinition.builder("exchange.deep.duty")
                .speaker(Speaker.AI)
                .promptStringId("story.exchange.deep.duty.prompt")
                .trigger(ExchangeTrigger.DEEP_STRATA)
                .regionFrom(StoryRegion.HARVESTING_GALLERIES)
                .stanceAffinity(Stance.ORGANIZATION)
                .option(ExchangeOption.builder("efficient")
                        .playerLineStringId("story.exchange.deep.duty.efficient")
                        .kind(ExchangeOptionKind.STANCE)
                        .stanceNudge(Stance.ORGANIZATION, 1)
                        .replyStringId("story.exchange.deep.duty.efficient.reply")
                        .build())
                .option(ExchangeOption.builder("whose")
                        .playerLineStringId("story.exchange.deep.duty.whose")
                        .kind(ExchangeOptionKind.PROBE)
                        .stanceNudge(Stance.ORA, 1)
                        .replyStringId("story.exchange.deep.duty.whose.reply")
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
}
