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
 * There is no "if region == 3" anywhere.  Each row simply declares the STORY REGION BAND it belongs
 * to, and {@link BarkSystem} selects only rows whose band contains the deepest region ever reached:
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

        registerFloorArrival(registry);
        registerRegionEntry(registry);
        registerGateOrders(registry);
        registerEnemyFamilies(registry);
        registerKills(registry);
        registerLowHealth(registry);
        registerDeepStrata(registry);
        registerIdleAndBacktrack(registry);
    }

    /** A fresh registry with the v1 catalog already in it (tests, showcases). */
    public static BarkRegistry defaultRegistry() {
        BarkRegistry registry = new BarkRegistry();
        bootstrap(registry);
        return registry;
    }

    // -------------------------------------------------------------------------
    // Floor arrival — the room you just walked into.  ORA, reactive, repeatable.
    // -------------------------------------------------------------------------
    private static void registerFloorArrival(BarkRegistry registry) {
        registry.register(row("bark.floor.rings.1", "story.bark.floor.rings.1")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).region(StoryRegion.HABITATION_RINGS).build());
        registry.register(row("bark.floor.rings.2", "story.bark.floor.rings.2")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).region(StoryRegion.HABITATION_RINGS).build());
        registry.register(row("bark.floor.galleries.1", "story.bark.floor.galleries.1")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).region(StoryRegion.HARVESTING_GALLERIES).build());
        registry.register(row("bark.floor.galleries.2", "story.bark.floor.galleries.2")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).region(StoryRegion.HARVESTING_GALLERIES).build());
        registry.register(row("bark.floor.reliquary.1", "story.bark.floor.reliquary.1")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).region(StoryRegion.RELIQUARY).build());
        registry.register(row("bark.floor.reliquary.2", "story.bark.floor.reliquary.2")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).region(StoryRegion.RELIQUARY).build());
        registry.register(row("bark.floor.wound.1", "story.bark.floor.wound.1")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).regionFrom(StoryRegion.WOUND).build());
        registry.register(row("bark.floor.wound.2", "story.bark.floor.wound.2")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).regionFrom(StoryRegion.WOUND).build());
        registry.register(row("bark.floor.core.1", "story.bark.floor.core.1")
                .trigger(BarkTrigger.FLOOR_ARRIVAL).region(StoryRegion.CORE).build());
    }

    // -------------------------------------------------------------------------
    // Region entry — one mandatory ORA beat per story region.  One-shot, critical.
    // -------------------------------------------------------------------------
    private static void registerRegionEntry(BarkRegistry registry) {
        registerRegionBeat(registry, "bark.region.rings",     "story.bark.region.rings",
                           StoryRegion.HABITATION_RINGS);
        registerRegionBeat(registry, "bark.region.galleries", "story.bark.region.galleries",
                           StoryRegion.HARVESTING_GALLERIES);
        registerRegionBeat(registry, "bark.region.reliquary", "story.bark.region.reliquary",
                           StoryRegion.RELIQUARY);
        registerRegionBeat(registry, "bark.region.wound",     "story.bark.region.wound",
                           StoryRegion.WOUND);
        registerRegionBeat(registry, "bark.region.core",      "story.bark.region.core",
                           StoryRegion.CORE);
    }

    private static void registerRegionBeat(BarkRegistry registry, String id, String textStringId,
                                           StoryRegion region) {
        registry.register(row(id, textStringId)
                .trigger(BarkTrigger.REGION_ENTERED)
                .region(region)
                .priority(BarkPriority.STORY_CRITICAL)
                .oneShot(true)
                .build());
    }

    // -------------------------------------------------------------------------
    // Region gate — the Organization, once per region, escalating.  One-shot, critical.
    // -------------------------------------------------------------------------
    private static void registerGateOrders(BarkRegistry registry) {
        registerGateOrder(registry, "bark.gate.rings",     "story.bark.gate.rings",
                          StoryRegion.HABITATION_RINGS);
        registerGateOrder(registry, "bark.gate.galleries", "story.bark.gate.galleries",
                          StoryRegion.HARVESTING_GALLERIES);
        registerGateOrder(registry, "bark.gate.reliquary", "story.bark.gate.reliquary",
                          StoryRegion.RELIQUARY);
        registerGateOrder(registry, "bark.gate.wound",     "story.bark.gate.wound",
                          StoryRegion.WOUND);
        registerGateOrder(registry, "bark.gate.core",      "story.bark.gate.core",
                          StoryRegion.CORE);
    }

    private static void registerGateOrder(BarkRegistry registry, String id, String textStringId,
                                          StoryRegion region) {
        registry.register(row(id, textStringId)
                .speaker(Speaker.ORGANIZATION)
                .trigger(BarkTrigger.REGION_GATE_ORDER)
                .region(region)
                .priority(BarkPriority.STORY_CRITICAL)
                .oneShot(true)
                .build());
    }

    // -------------------------------------------------------------------------
    // First sight of an enemy FAMILY.  Subject key = enemy/EnemyFamily.name().
    // One-shot per family, valid in every region (a family can debut at any depth).
    // -------------------------------------------------------------------------
    private static void registerEnemyFamilies(BarkRegistry registry) {
        registerFamily(registry, "UNDEAD",     "bark.family.undead",     "story.bark.family.undead");
        registerFamily(registry, "INSECT",     "bark.family.insect",     "story.bark.family.insect");
        registerFamily(registry, "MACHINE",    "bark.family.machine",    "story.bark.family.machine");
        registerFamily(registry, "ABERRATION", "bark.family.aberration", "story.bark.family.aberration");
        registerFamily(registry, "DEMON",      "bark.family.demon",      "story.bark.family.demon");
        registerFamily(registry, "GOLEM",      "bark.family.golem",      "story.bark.family.golem");
    }

    private static void registerFamily(BarkRegistry registry, String familyName,
                                       String id, String textStringId) {
        registry.register(row(id, textStringId)
                .trigger(BarkTrigger.ENEMY_FAMILY_FIRST_SEEN)
                .subjectKey(familyName)
                .oneShot(true)
                .build());
    }

    // -------------------------------------------------------------------------
    // Kills — ORA's confidence rotting across the descent.  Reactive, rate-limited.
    // -------------------------------------------------------------------------
    private static void registerKills(BarkRegistry registry) {
        registry.register(row("bark.kill.rings.1", "story.bark.kill.rings.1")
                .trigger(BarkTrigger.KILL).region(StoryRegion.HABITATION_RINGS).build());
        registry.register(row("bark.kill.rings.2", "story.bark.kill.rings.2")
                .trigger(BarkTrigger.KILL).region(StoryRegion.HABITATION_RINGS).build());
        registry.register(row("bark.kill.galleries.1", "story.bark.kill.galleries.1")
                .trigger(BarkTrigger.KILL).region(StoryRegion.HARVESTING_GALLERIES).build());
        registry.register(row("bark.kill.galleries.2", "story.bark.kill.galleries.2")
                .trigger(BarkTrigger.KILL).region(StoryRegion.HARVESTING_GALLERIES).build());
        registry.register(row("bark.kill.reliquary.1", "story.bark.kill.reliquary.1")
                .trigger(BarkTrigger.KILL).region(StoryRegion.RELIQUARY).build());
        registry.register(row("bark.kill.reliquary.2", "story.bark.kill.reliquary.2")
                .trigger(BarkTrigger.KILL).region(StoryRegion.RELIQUARY).build());
        registry.register(row("bark.kill.wound.1", "story.bark.kill.wound.1")
                .trigger(BarkTrigger.KILL).regionFrom(StoryRegion.WOUND).build());
        registry.register(row("bark.kill.wound.2", "story.bark.kill.wound.2")
                .trigger(BarkTrigger.KILL).regionFrom(StoryRegion.WOUND).build());
    }

    // -------------------------------------------------------------------------
    // Low health — one line per tone stage.
    // -------------------------------------------------------------------------
    private static void registerLowHealth(BarkRegistry registry) {
        registry.register(row("bark.lowhealth.rings", "story.bark.lowhealth.rings")
                .trigger(BarkTrigger.LOW_HEALTH).region(StoryRegion.HABITATION_RINGS).build());
        registry.register(row("bark.lowhealth.mid", "story.bark.lowhealth.mid")
                .trigger(BarkTrigger.LOW_HEALTH)
                .regionBand(StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY).build());
        registry.register(row("bark.lowhealth.deep", "story.bark.lowhealth.deep")
                .trigger(BarkTrigger.LOW_HEALTH).regionFrom(StoryRegion.WOUND).build());
    }

    // -------------------------------------------------------------------------
    // Deep-strata milestones — THE PLANET.  No rows in Region 1: that silence is the design.
    // -------------------------------------------------------------------------
    private static void registerDeepStrata(BarkRegistry registry) {
        // Region 2 — whispers: single wrong-language words, easy to blame on the deep.
        registerWhisper(registry, "bark.strata.galleries.1", "story.bark.strata.galleries.1");
        registerWhisper(registry, "bark.strata.galleries.2", "story.bark.strata.galleries.2");
        registerWhisper(registry, "bark.strata.galleries.3", "story.bark.strata.galleries.3");
        registerWhisper(registry, "bark.strata.galleries.4", "story.bark.strata.galleries.4");

        // Region 3 — address: it knows you, by deed, not by serial.
        registerPlanetLine(registry, "bark.strata.reliquary.1", "story.bark.strata.reliquary.1",
                           StoryRegion.RELIQUARY, StoryRegion.RELIQUARY);
        registerPlanetLine(registry, "bark.strata.reliquary.2", "story.bark.strata.reliquary.2",
                           StoryRegion.RELIQUARY, StoryRegion.RELIQUARY);
        registerPlanetLine(registry, "bark.strata.reliquary.3", "story.bark.strata.reliquary.3",
                           StoryRegion.RELIQUARY, StoryRegion.RELIQUARY);

        // Region 4 -> Core — communion: plain, gentle, terrible.
        registerPlanetLine(registry, "bark.strata.wound.1", "story.bark.strata.wound.1",
                           StoryRegion.WOUND, StoryRegion.CORE);
        registerPlanetLine(registry, "bark.strata.wound.2", "story.bark.strata.wound.2",
                           StoryRegion.WOUND, StoryRegion.CORE);
        registerPlanetLine(registry, "bark.strata.wound.3", "story.bark.strata.wound.3",
                           StoryRegion.WOUND, StoryRegion.CORE);
    }

    private static void registerWhisper(BarkRegistry registry, String id, String textStringId) {
        registerPlanetLine(registry, id, textStringId,
                           StoryRegion.HARVESTING_GALLERIES, StoryRegion.HARVESTING_GALLERIES);
    }

    private static void registerPlanetLine(BarkRegistry registry, String id, String textStringId,
                                           StoryRegion firstRegion, StoryRegion lastRegion) {
        registry.register(row(id, textStringId)
                .speaker(Speaker.PLANET)
                .trigger(BarkTrigger.DEEP_STRATA)
                .regionBand(firstRegion, lastRegion)
                .build());
    }

    // -------------------------------------------------------------------------
    // Idle / backtracking — pure flavour, dropped first under any pressure.
    // -------------------------------------------------------------------------
    private static void registerIdleAndBacktrack(BarkRegistry registry) {
        registry.register(row("bark.idle.rings", "story.bark.idle.rings")
                .trigger(BarkTrigger.IDLE).priority(BarkPriority.FLAVOR)
                .region(StoryRegion.HABITATION_RINGS).build());
        registry.register(row("bark.idle.mid", "story.bark.idle.mid")
                .trigger(BarkTrigger.IDLE).priority(BarkPriority.FLAVOR)
                .regionBand(StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY).build());
        registry.register(row("bark.idle.deep", "story.bark.idle.deep")
                .trigger(BarkTrigger.IDLE).priority(BarkPriority.FLAVOR)
                .regionFrom(StoryRegion.WOUND).build());

        registry.register(row("bark.backtrack.rings", "story.bark.backtrack.rings")
                .trigger(BarkTrigger.BACKTRACK).priority(BarkPriority.FLAVOR)
                .region(StoryRegion.HABITATION_RINGS).build());
        registry.register(row("bark.backtrack.mid", "story.bark.backtrack.mid")
                .trigger(BarkTrigger.BACKTRACK).priority(BarkPriority.FLAVOR)
                .regionBand(StoryRegion.HARVESTING_GALLERIES, StoryRegion.RELIQUARY).build());
        registry.register(row("bark.backtrack.deep", "story.bark.backtrack.deep")
                .trigger(BarkTrigger.BACKTRACK).priority(BarkPriority.FLAVOR)
                .regionFrom(StoryRegion.WOUND).build());
    }

    /** Shared row prologue: ORA, reactive priority — the defaults most rows keep. */
    private static BarkDefinition.Builder row(String id, String textStringId) {
        return BarkDefinition.builder(id)
                .speaker(Speaker.AI)
                .textStringId(textStringId)
                .priority(BarkPriority.REACTIVE);
    }
}
