package ge.tbegvadze.toon3d.narrative;

/**
 * The CODEX catalog (Story UI order-6 Part A) — every archived entry, as data.
 *
 * <p><b>Adding an entry is ONE {@link CodexRegistry#register} call here.</b>  Never a switch, never a
 * hardcoded string: a row names localisation ids and the lines that archive it, exactly like
 * {@link BarkCatalog} and {@link ExchangeCatalog}.  {@link CodexSystem} reads whatever is registered.
 *
 * <h3>What belongs in the codex, and what does not</h3>
 * The codex is an ARCHIVE, not a second script.  Almost every entry is the FULL TEXT of something the
 * player already met in one line out in the world — the terminal ORA read over their shoulder, the
 * word she used, the thing they asked about.  A player who never opens this screen misses no plot
 * point, because every entry here is the long version of a beat that already landed on an
 * unskippable channel.  That is the deal order-6 Part C makes with the ~80% who will never open it.
 *
 * <h3>The two unlock paths</h3>
 * <ul>
 *   <li><b>Said in the world.</b> {@code unlockedByLine(barkOrExchangeId)} — the bark fires, the
 *       document lands in the archive.  Reading costs the player nothing they did not already do.</li>
 *   <li><b>Paid for by curiosity.</b> An {@code ExchangeOption.unlocksCodexId} names an entry id
 *       directly (order-4's PROBE reward).  Those ids are load-bearing: a test asserts every one of
 *       them resolves to a row here.</li>
 * </ul>
 *
 * <h3>MEMORIES</h3>
 * The one category that is not about the facility.  Its five entries unlock off the region-entry
 * beats, so the block on the player's own past lifts exactly as the descent deepens, and the tab
 * fills in as they reassemble who they were (story/02-player-and-cloning.md).
 *
 * <p>Headless: no LibGDX imports.
 */
public final class CodexCatalog {

    private CodexCatalog() {}

    /** Registers the v1 catalog.  Idempotent: a registry that already has rows is left alone. */
    public static void bootstrap(CodexRegistry registry) {
        if (registry == null || !registry.isEmpty()) return;
        registerLogs(registry);
        registerPeople(registry);
        registerPlanet(registry);
        registerOrganization(registry);
        registerOra(registry);
        registerMemories(registry);
    }

    /** A fresh registry with the v1 catalog already in it (tests, showcases). */
    public static CodexRegistry defaultRegistry() {
        CodexRegistry registry = new CodexRegistry();
        bootstrap(registry);
        return registry;
    }

    // -------------------------------------------------------------------------
    // LOGS — the facility's paperwork, in full.  Every one of these is a terminal the player walked
    // up to and ORA gave a one-line take on; the take is reprinted at the top of the entry, so what
    // they read here is the line they remember followed by the document behind it.
    // -------------------------------------------------------------------------
    private static void registerLogs(CodexRegistry registry) {
        log(registry, "codex.log.rings.roster",        StoryRegion.HABITATION_RINGS, "bark.log.rings.1");
        log(registry, "codex.log.rings.maintenance",   StoryRegion.HABITATION_RINGS, "bark.log.rings.2");
        log(registry, "codex.log.rings.safety",        StoryRegion.HABITATION_RINGS, "bark.log.rings.4");

        log(registry, "codex.log.galleries.quota",     StoryRegion.HARVESTING_GALLERIES, "bark.log.galleries.1");
        log(registry, "codex.log.galleries.tolerance", StoryRegion.HARVESTING_GALLERIES, "bark.log.galleries.2");
        log(registry, "codex.log.galleries.manifest",  StoryRegion.HARVESTING_GALLERIES, "bark.log.galleries.4");

        log(registry, "codex.log.reliquary.service",   StoryRegion.RELIQUARY, "bark.log.reliquary.1");
        log(registry, "codex.log.reliquary.intake",    StoryRegion.RELIQUARY, "bark.log.reliquary.3");
        log(registry, "codex.log.reliquary.operator",  StoryRegion.RELIQUARY, "bark.log.reliquary.4");

        log(registry, "codex.log.wound.field",         StoryRegion.WOUND, "bark.log.wound.2");
        log(registry, "codex.log.wound.sedation",      StoryRegion.WOUND, "bark.log.wound.4");

        log(registry, "codex.log.core.resignation",    StoryRegion.CORE, "bark.log.core.2");
    }

    /**
     * One log entry.  Its ORA take is the bark's own string id — the same words she said out loud,
     * deliberately, so the archive reads as HER archive rather than a separate encyclopedia.
     */
    private static void log(CodexRegistry registry, String id, StoryRegion region, String barkId) {
        registry.register(CodexEntry.builder(id, CodexCategory.LOGS)
                .region(region)
                .aiTakeStringId("story." + barkId)
                .unlockedByLine(barkId)
                // Offered to every log, resolved per entry: a log whose subject is a word the order-3
                // ladder names is also archived by that naming line (today only the cradle service
                // log).  For a log no StoryTerm names, this adds nothing.
                .unlockedByTermIntro()
                .build());
    }

    // -------------------------------------------------------------------------
    // PEOPLE — the crew.  Four names, because four names the player can hold are worth more than a
    // roster of forty they cannot.  Each is assembled from the paperwork they left behind.
    // -------------------------------------------------------------------------
    private static void registerPeople(CodexRegistry registry) {
        person(registry, "codex.people.dunnet",    StoryRegion.HABITATION_RINGS,    "bark.log.rings.2");
        person(registry, "codex.people.veil",      StoryRegion.HARVESTING_GALLERIES, "bark.log.galleries.1");
        person(registry, "codex.people.archivist", StoryRegion.RELIQUARY,           "bark.log.reliquary.cradle");
        person(registry, "codex.people.operator",  StoryRegion.RELIQUARY,           "bark.log.reliquary.4");
    }

    private static void person(CodexRegistry registry, String id, StoryRegion region, String lineId) {
        registry.register(CodexEntry.builder(id, CodexCategory.PEOPLE)
                .region(region)
                .unlockedByLine(lineId)
                .build());
    }

    // -------------------------------------------------------------------------
    // THE PLANET — what it is and what it has been saying.  Three of these are PROBE rewards: the
    // player asked, and this is what the asking bought them (order-4's "pay them for engaging").
    // -------------------------------------------------------------------------
    private static void registerPlanet(CodexRegistry registry) {
        registry.register(CodexEntry.builder("codex.contaminant", CodexCategory.PLANET)
                .region(StoryRegion.HABITATION_RINGS)
                .unlockedByLine("bark.gate.rings")
                .unlockedByTermIntro()
                .build());
        registry.register(CodexEntry.builder("codex.planet.voice", CodexCategory.PLANET)
                .region(StoryRegion.HARVESTING_GALLERIES)
                .unlockedByLine("bark.region.galleries")
                .build());
        registry.register(CodexEntry.builder("codex.planet.name", CodexCategory.PLANET)
                .region(StoryRegion.RELIQUARY)
                .build());
        registry.register(CodexEntry.builder("codex.planet.wound", CodexCategory.PLANET)
                .region(StoryRegion.WOUND)
                .unlockedByLine("bark.region.wound")
                .unlockedByTermIntro()
                .build());
    }

    // -------------------------------------------------------------------------
    // THE ORGANIZATION — Deepworks in its own words.  The entries are deliberately dry: the horror
    // of this tab is that none of it is written by a villain, it is all written by a business.
    // -------------------------------------------------------------------------
    private static void registerOrganization(CodexRegistry registry) {
        registry.register(CodexEntry.builder("codex.harvest", CodexCategory.ORGANIZATION)
                .region(StoryRegion.HARVESTING_GALLERIES)
                .build());
        registry.register(CodexEntry.builder("codex.yield.ledger", CodexCategory.ORGANIZATION)
                .region(StoryRegion.HARVESTING_GALLERIES)
                .unlockedByLine("bark.log.galleries.yield")
                .unlockedByTermIntro()
                .build());
        registry.register(CodexEntry.builder("codex.soul.reserve", CodexCategory.ORGANIZATION)
                .region(StoryRegion.RELIQUARY)
                .unlockedByLine("bark.log.reliquary.cradle")
                .unlockedByTermIntro()
                .build());
        registry.register(CodexEntry.builder("codex.overseer", CodexCategory.ORGANIZATION)
                .region(StoryRegion.RELIQUARY)
                .unlockedByTermIntro()
                .build());
        registry.register(CodexEntry.builder("codex.revocation", CodexCategory.ORGANIZATION)
                .region(StoryRegion.WOUND)
                .unlockedByLine("bark.gate.wound")
                .build());
    }

    // -------------------------------------------------------------------------
    // ORA — what she is, what she is permitted to say, and what she works out anyway.  Filed under
    // her own tab because by the Reliquary the player is asking about her, not about the facility.
    // -------------------------------------------------------------------------
    private static void registerOra(CodexRegistry registry) {
        registry.register(CodexEntry.builder("codex.ora.count", CodexCategory.ORA)
                .region(StoryRegion.HABITATION_RINGS)
                // The cold open that used to archive this became narrative-rework order-2's
                // IntroBeat; the beat that is actually ABOUT the count is the one that explains the
                // number on the wake card, and order-3's naming line for "instance" follows it.
                .unlockedByLine("bark.intro." + IntroBeat.REPRINT_COUNTER.getCatalogKey())
                .unlockedByTermIntro()
                .build());
        registry.register(CodexEntry.builder("codex.ora.build", CodexCategory.ORA)
                .region(StoryRegion.RELIQUARY)
                .unlockedByLine("bark.region.reliquary")
                .build());
        registry.register(CodexEntry.builder("codex.ora.leash", CodexCategory.ORA)
                .region(StoryRegion.RELIQUARY)
                .unlockedByLine("bark.gate.reliquary")
                .build());
    }

    // -------------------------------------------------------------------------
    // MEMORIES — the player's original self.  One entry per region, unlocked by the region-entry
    // beat, so the tab fills exactly as the block lifts.  Nobody hands these to the player and no
    // choice earns them: going deeper is what remembers.
    // -------------------------------------------------------------------------
    private static void registerMemories(CodexRegistry registry) {
        for (StoryRegion region : StoryRegion.values()) {
            registry.register(CodexEntry.builder("codex.memory." + region.getCatalogKey(),
                                                 CodexCategory.MEMORIES)
                    .region(region)
                    .unlockedByLine("bark.region." + region.getCatalogKey())
                    .build());
        }
    }
}
