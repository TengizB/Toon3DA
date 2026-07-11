package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * The v1 narrative EVENT catalog (route-map order-10). One {@code register(...)} block per event; the
 * list is MEANT to grow (creative-game-designer expands it) — a new event is one block here and
 * nothing else, because every choice's behaviour is a typed {@link EventEffect} routed through an
 * existing system. Kept as a small factory (rather than inline in {@link RouteRegistries}) so the
 * events read as content, not registry plumbing.
 *
 * <p>v1 events, each a small trade the player makes at the interactable:
 * <ul>
 *   <li>DISTRESS BEACON — trade information for resources (free next-region scan vs an ammo cache).</li>
 *   <li>ABANDONED STASH — a risk gamble (force the locker: loot vs a mimic bite) vs leave it.</li>
 *   <li>SURVIVING TECH — escort (armour to full now, next floor +budget) vs take their keycard and go.</li>
 *   <li>CORRUPTED SHRINE — a Faustian pull (a strong heal + a next-floor risk) vs destroy it for XP.</li>
 *   <li>DATA TERMINAL — the pure-lore breather: guaranteed XP, no downside.</li>
 * </ul>
 *
 * <p>Pure / headless — no LibGDX imports.
 */
public final class FacilityEvents {

    private FacilityEvents() {}

    /** Registers every v1 event into the given registry (called from {@link RouteRegistries}). */
    public static void registerAll(FacilityEventRegistry registry) {
        registry.register(distressBeacon());
        registry.register(abandonedStash());
        registry.register(survivingTech());
        registry.register(corruptedShrine());
        registry.register(dataTerminal());
    }

    /** DISTRESS BEACON — trade information for resources. */
    private static FacilityEvent distressBeacon() {
        return FacilityEvent.builder("distress_beacon")
                .title("DISTRESS BEACON")
                .narrative("A looping signal pulses from the console.",
                           "Someone, or something, is still transmitting.")
                .choice("ANSWER", "Triangulate the source — reveal the next region.",
                        EventEffect.builder()
                                .revealNextRegion()
                                .resultText("SIGNAL TRIANGULATED — SECTOR MAPPED")
                                .logToken("answered")
                                .build())
                .choice("IGNORE", "Strip the beacon for its power cells — grab an ammo cache.",
                        EventEffect.builder()
                                .ammoBoxes(RouteMapConstants.EVENT_AMMO_BOXES)
                                .resultText("POWER CELLS SALVAGED — AMMO RECOVERED")
                                .logToken("ignored")
                                .build())
                .build();
    }

    /** ABANDONED STASH — a press-your-luck locker. */
    private static FacilityEvent abandonedStash() {
        return FacilityEvent.builder("abandoned_stash")
                .title("ABANDONED STASH")
                .narrative("A sealed supply locker, half-buried in debris.",
                           "The lock is corroded. It could hold anything.")
                .choice("FORCE THE LOCKER", "60% a loot haul, 40% something bites back.",
                        EventEffect.builder()
                                .gamble(RouteMapConstants.EVENT_STASH_LOOT_CHANCE, 0.20f)
                                .ammoBoxes(RouteMapConstants.EVENT_AMMO_BOXES)
                                .heal(0.20f)
                                .resultText("LOCKER FORCED — SUPPLIES RECOVERED")
                                .failText("MIMIC! — IT WAS WAITING")
                                .logToken("forced")
                                .build())
                .choice("LEAVE IT", "Not worth the risk. Walk on.",
                        EventEffect.builder()
                                .logToken("left")
                                .build())
                .build();
    }

    /** SURVIVING TECH — a rescue with a cost. */
    private static FacilityEvent survivingTech() {
        return FacilityEvent.builder("surviving_tech")
                .title("SURVIVING TECH")
                .narrative("A UAC technician cowers behind an overturned rack.",
                           "\"You're not one of them. Please — get me out.\"")
                .choice("ESCORT THEM", "They patch your armour to full — but the next floor is hotter.",
                        EventEffect.builder()
                                .armourToFull()
                                .nextFloorBudgetBonus(RouteMapConstants.EVENT_NEXT_FLOOR_BUDGET_BONUS)
                                .resultText("ARMOUR REPAIRED — THEY FOLLOW YOU DOWN")
                                .logToken("escorted")
                                .build())
                .choice("TAKE THE KEYCARD", "Take their keycard and go — a small XP for the intel.",
                        EventEffect.builder()
                                .grantXp(RouteMapConstants.EVENT_XP_SMALL)
                                .resultText("KEYCARD TAKEN — YOU LEAVE THEM BEHIND")
                                .logToken("abandoned_tech")
                                .build())
                .build();
    }

    /** CORRUPTED SHRINE — a Faustian pull. */
    private static FacilityEvent corruptedShrine() {
        return FacilityEvent.builder("corrupted_shrine")
                .title("CORRUPTED SHRINE")
                .narrative("A pulsing growth has consumed an old relay altar.",
                           "It offers power. It always wants something back.")
                .choice("TOUCH IT", "Surge to full health now — but the next floor turns on you.",
                        EventEffect.builder()
                                .heal(1.0f)
                                .nextFloorBudgetBonus(RouteMapConstants.EVENT_NEXT_FLOOR_BUDGET_BONUS)
                                .resultText("POWER FLOODS IN — THE FACILITY RECOILS")
                                .logToken("touched")
                                .build())
                .choice("DESTROY IT", "Smash the growth — a clean conscience and some XP.",
                        EventEffect.builder()
                                .grantXp(RouteMapConstants.EVENT_XP_LARGE)
                                .resultText("SHRINE DESTROYED — THE HUM DIES")
                                .logToken("destroyed")
                                .build())
                .build();
    }

    /** DATA TERMINAL — the pure-lore breather. */
    private static FacilityEvent dataTerminal() {
        return FacilityEvent.builder("data_terminal")
                .title("DATA TERMINAL")
                .narrative("A terminal still cycles through incident logs.",
                           "\"...containment failure on sublevel four. God help us.\"",
                           "You download what you can.")
                .choice("DOWNLOAD", "Archive the logs — guaranteed intel, no risk.",
                        EventEffect.builder()
                                .grantXp(RouteMapConstants.EVENT_XP_SMALL)
                                .resultText("LOGS ARCHIVED — +INTEL")
                                .logToken("downloaded")
                                .build())
                .build();
    }
}
