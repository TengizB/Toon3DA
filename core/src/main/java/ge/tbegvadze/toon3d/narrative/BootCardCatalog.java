package ge.tbegvadze.toon3d.narrative;

/**
 * The v1 BOOT-CARD CATALOG (Story UI order-3) — every reprint card the game can print, as data.
 *
 * <p>Adding an ORA reprint line is ONE {@link BootCardRegistry#registerWakeLine} call here; adding
 * an ending card is ONE {@link BootCardRegistry#registerCard} call.  Nothing in {@code World}, the
 * renderer or {@link BootCardSystem} switches on a card list, so a new line, a new region band or a
 * whole new ending needs no code change anywhere else (the same "registration, not a switch
 * statement" discipline as {@code route/RouteRegistries} and {@link BarkCatalog}).
 *
 * <h3>The two voices on this screen</h3>
 * <ul>
 *   <li>The SYSTEM lines never editorialise.  They report machine state, flatly, and are identical
 *       on the player's first boot and their hundredth — which is the point: "SOUL RESERVE ..........
 *       SUFFICIENT" means nothing on run 1 and means <em>there is still enough of the captive being
 *       left to burn on you</em> by run 40 (story/dialog/system-cards.md).</li>
 *   <li>ORA's wake-up line is the story beat, and it is region-gated exactly like a bark: cheerful
 *       and counting deaths in the Habitation Rings, distracted about what the printers run on in
 *       the Galleries, cracked in the Reliquary, quiet and protective from the Wound down.  There is
 *       no "if region == 3" anywhere — the arc is expressed purely by which band a line is
 *       registered in (story/dialog/ai-assistant.md).</li>
 * </ul>
 *
 * <h3>The endgame subversion</h3>
 * The endgame variants exist to invert the card the player has seen a hundred times.  FREE and KILL
 * print a depleted reserve and no authorized checkpoint, and then the run does not reload; OBEY
 * prints a promotion, which is the horror; MERGE registers no card at all, because the interface
 * simply stops being the player's (story/06-endings.md).  Which ending fires is order-5's schedule
 * and order-8's flow — this catalog only owns what each card says.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class BootCardCatalog {

    private BootCardCatalog() {}

    /**
     * Registers the v1 rows into {@code registry}.  Idempotent: a registry that already holds rows
     * is left untouched, so calling this from more than one entry point is safe.
     */
    public static void bootstrap(BootCardRegistry registry) {
        if (registry == null || !registry.isEmpty()) return;

        registerCards(registry);
        registerWakeLines(registry);
    }

    /** A fresh registry with the v1 catalog already in it (the game, tests, the showcase). */
    public static BootCardRegistry defaultRegistry() {
        BootCardRegistry registry = new BootCardRegistry();
        bootstrap(registry);
        return registry;
    }

    // -------------------------------------------------------------------------
    // The cards — one per variant that prints anything.  MERGE deliberately has none.
    // -------------------------------------------------------------------------
    private static void registerCards(BootCardRegistry registry) {
        // The default card, shown on every reprint including the very first run.  The only variant
        // reachable in play today; the endings that replace it are order-5 / order-8's schedule.
        registry.registerCard(BootCardDefinition.builder(BootCardVariant.REPRINT)
                .systemLines("story.boot.system.reprint.1",
                             "story.boot.system.reprint.2",
                             "story.boot.system.reprint.3")
                .showsInstanceCounter(true)
                .showsWakeLine(true)          // ORA is the beat; the machine is the frame
                .build());

        // FREE IT — the reserve is spent buying the being's freedom, so nobody is printed again.
        registry.registerCard(BootCardDefinition.builder(BootCardVariant.ENDING_FREE)
                .systemLines("story.boot.system.free.1",
                             "story.boot.system.free.2",
                             "story.boot.system.free.3")
                .showsInstanceCounter(false)  // there is no next instance to number
                .showsWakeLine(false)         // the machine ends this one alone
                .build());

        // KILL IT — the same absence by the other road: no source left to print a pattern from.
        registry.registerCard(BootCardDefinition.builder(BootCardVariant.ENDING_KILL)
                .systemLines("story.boot.system.kill.1",
                             "story.boot.system.kill.2",
                             "story.boot.system.kill.3")
                .showsInstanceCounter(false)
                .showsWakeLine(false)
                .build());

        // OBEY — the reward is more of the same, forever.  The counter stays: it will keep climbing.
        registry.registerCard(BootCardDefinition.builder(BootCardVariant.ENDING_OBEY)
                .systemLines("story.boot.system.obey.1",
                             "story.boot.system.obey.2",
                             "story.boot.system.obey.3")
                .showsInstanceCounter(true)
                .showsWakeLine(false)
                .build());

        // MERGE registers NOTHING on purpose. BootCardVariant.ENDING_MERGE.showsCard() is false and
        // the system presents no card, so the interface simply never reboots.
    }

    // -------------------------------------------------------------------------
    // ORA's wake-up lines — the guaranteed beat, region-gated.  Pools are kept 3+ deep per region
    // so the no-repeat memory always has somewhere to go and a long session never loops a line.
    // -------------------------------------------------------------------------
    private static void registerWakeLines(BootCardRegistry registry) {
        // Region 1 — onboarding cheer.  She counts your deaths like a scoreboard, and it is funny.
        wakeLine(registry, "boot.wake.rings.1", StoryRegion.HABITATION_RINGS);
        wakeLine(registry, "boot.wake.rings.2", StoryRegion.HABITATION_RINGS);
        wakeLine(registry, "boot.wake.rings.3", StoryRegion.HABITATION_RINGS);
        wakeLine(registry, "boot.wake.rings.4", StoryRegion.HABITATION_RINGS);

        // Region 2 — first doubts.  She has started asking what the printers run on.
        wakeLine(registry, "boot.wake.galleries.1", StoryRegion.HARVESTING_GALLERIES);
        wakeLine(registry, "boot.wake.galleries.2", StoryRegion.HARVESTING_GALLERIES);
        wakeLine(registry, "boot.wake.galleries.3", StoryRegion.HARVESTING_GALLERIES);
        wakeLine(registry, "boot.wake.galleries.4", StoryRegion.HARVESTING_GALLERIES);

        // Region 3 — cracking.  She knows what the reserve is now, and the counting stopped being a joke.
        wakeLine(registry, "boot.wake.reliquary.1", StoryRegion.RELIQUARY);
        wakeLine(registry, "boot.wake.reliquary.2", StoryRegion.RELIQUARY);
        wakeLine(registry, "boot.wake.reliquary.3", StoryRegion.RELIQUARY);
        wakeLine(registry, "boot.wake.reliquary.4", StoryRegion.RELIQUARY);

        // Region 4 — frightened ally.  Quiet, protective, keeping things off the log for you.
        wakeLine(registry, "boot.wake.wound.1", StoryRegion.WOUND);
        wakeLine(registry, "boot.wake.wound.2", StoryRegion.WOUND);
        wakeLine(registry, "boot.wake.wound.3", StoryRegion.WOUND);
        wakeLine(registry, "boot.wake.wound.4", StoryRegion.WOUND);

        // Region 5 — the Core.  Nothing left to hedge.
        wakeLine(registry, "boot.wake.core.1", StoryRegion.CORE);
        wakeLine(registry, "boot.wake.core.2", StoryRegion.CORE);
        wakeLine(registry, "boot.wake.core.3", StoryRegion.CORE);
    }

    /** One ORA line, gated to a single story region.  Its string id mirrors its catalog id. */
    private static void wakeLine(BootCardRegistry registry, String lineId, StoryRegion region) {
        registry.registerWakeLine(BootWakeLine.builder(lineId)
                .textStringId("story." + lineId)
                .region(region)
                .build());
    }
}
