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
        // THE FIRST PRINT (narrative-rework order-2 B) — the card for a save that has never printed
        // anybody, and the first screen of this fiction a stranger ever reads.  Four plain lines,
        // no proper noun of the setting in any of them: a person died, that person is you, there is
        // a copy, the copy is being made.  The launch screen borrows all four (see
        // BootCardDefinition.getLaunchStatusLineCount), because on an empty save they are the only
        // explanation the player has of what they are about to be.
        registry.registerCard(BootCardDefinition.builder(BootCardVariant.FIRST_PRINT)
                .systemLines("story.boot.system.firstprint.1",
                             "story.boot.system.firstprint.2",
                             "story.boot.system.firstprint.3",
                             "story.boot.system.firstprint.4")
                .launchStatusLines(4)
                .showsInstanceCounter(true)   // never hidden: the number is the premise
                .showsWakeLine(true)
                .build());

        // The default card, shown on every reprint after that one.  The only other variant reachable
        // in play today; the endings that replace it are order-5 / order-8's schedule.
        //
        // TWO BANDS (narrative-rework order-2 C).  Up top it says only what the player can already
        // read: the last body died, a copy is on file, a new one is printing.  "SOUL RESERVE" — the
        // Region-3 reveal, and the whole horror of this card — waits for the Harvesting Galleries,
        // which is exactly where ORA starts asking what the printers run on.  Before that beat the
        // word was wallpaper; after it, it is on every card the player will ever read again.
        registry.registerCard(BootCardDefinition.builder(BootCardVariant.REPRINT)
                .systemLines("story.boot.system.reprint.1",
                             "story.boot.system.reprint.2",
                             "story.boot.system.reprint.3")
                .systemLinesFrom(StoryRegion.HARVESTING_GALLERIES,
                             "story.boot.system.reprint.deep.1",
                             "story.boot.system.reprint.deep.2",
                             "story.boot.system.reprint.deep.3")
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
        // ---- RESERVED LINES: the three cards a player only ever reads once -----------------------
        // (narrative-rework order-2 B/C).  Each is held for exactly one card, so the moments that
        // have to explain something are never spent on a random greeting from the pool.

        // The FIRST PRINT.  Thirty seconds after a stranger's death notice: no name, no job, no
        // explanation — she introduces herself the moment the player has control and can look
        // around, which is where a line like that can actually be answered.  Deliberately region
        // -unrestricted: it is pinned to the one card that can only ever happen once anyway.
        registry.registerWakeLine(BootWakeLine.builder("boot.wake.first.1")
                .textStringId("story.boot.wake.first.1")
                .onlyOnVariant(BootCardVariant.FIRST_PRINT)
                .build());

        // The player's OWN first deaths.  Instance 2 is the first body they actually lost, and this
        // is the screen where the loop gets explained rather than joked about: what came back, what
        // did not, and — a print later, one new thing per screen — that the REPORT plate is there
        // for whoever wants to know what killed them.  Reserved by instance number, so they land in
        // order, once each, whatever region the story has reached by then.
        registry.registerWakeLine(BootWakeLine.builder("boot.wake.firstdeath.1")
                .textStringId("story.boot.wake.firstdeath.1")
                .onlyAtReprintCount(2)
                .build());
        registry.registerWakeLine(BootWakeLine.builder("boot.wake.firstdeath.2")
                .textStringId("story.boot.wake.firstdeath.2")
                .onlyAtReprintCount(3)
                .build());
        registry.registerWakeLine(BootWakeLine.builder("boot.wake.firstdeath.report")
                .textStringId("story.boot.wake.firstdeath.report")
                .onlyAtReprintCount(4)
                .build());

        // THE COUNTER EARNS ITS KEEP (narrative-rework order-9 D).  By print forty the opening beats
        // are long spent and this card is the only story a player sees for whole sessions, so the
        // number on it — the game's oldest running joke and its slowest turn — is given a line of its
        // own every tenth print.  Reserved by instance number, which is what makes each one land
        // exactly once in the life of a save without a one-shot flag; banded by region from thirty
        // down, because "I've stopped rounding it off in my head" only means anything to somebody who
        // has read what the Cradles are fed.  A player still up top at print thirty simply gets an
        // ordinary greeting, which is the right answer: the joke has not turned for them yet.
        counterMilestone(registry, 10,  StoryRegion.HABITATION_RINGS);
        counterMilestone(registry, 20,  StoryRegion.HABITATION_RINGS);
        counterMilestone(registry, 30,  StoryRegion.RELIQUARY);
        counterMilestone(registry, 40,  StoryRegion.WOUND);
        counterMilestone(registry, 50,  StoryRegion.WOUND);
        counterMilestone(registry, 100, StoryRegion.CORE);

        // THE RE-ENTRY LINE (narrative-rework order-9 A).  One row per region, taking this card's
        // single ORA slot when the player has been away longer than STORY_REENTRY_GAP_HOURS.  It
        // says where they are and what they were last told — never what to do next, because the
        // moment a recap acquires an objective it stops being ORA talking and becomes a quest log.
        for (StoryRegion region : StoryRegion.values()) {
            String id = "boot.wake.reentry." + region.getCatalogKey();
            registry.registerWakeLine(BootWakeLine.builder(id)
                    .textStringId("story." + id)
                    .region(region)
                    .reEntry(true)
                    .build());
        }

        // ---- The general pool, gated by the deepest region ever reached --------------------------
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

    /**
     * One line about the instance NUMBER, held for the card that prints it (order-9 D).  Banded from
     * {@code firstRegion} down rather than pinned to one region, so a player who is deeper than the
     * milestone expects still gets it — the row is lost only to somebody who is SHALLOWER, where its
     * meaning has not arrived yet.
     */
    private static void counterMilestone(BootCardRegistry registry, int reprintCount,
                                         StoryRegion firstRegion) {
        String id = "boot.wake.milestone." + reprintCount;
        registry.registerWakeLine(BootWakeLine.builder(id)
                .textStringId("story." + id)
                .regionFrom(firstRegion)
                .onlyAtReprintCount(reprintCount)
                .build());
    }

    /** One ORA line, gated to a single story region.  Its string id mirrors its catalog id. */
    private static void wakeLine(BootCardRegistry registry, String lineId, StoryRegion region) {
        registry.registerWakeLine(BootWakeLine.builder(lineId)
                .textStringId("story." + lineId)
                .region(region)
                .build());
    }
}
