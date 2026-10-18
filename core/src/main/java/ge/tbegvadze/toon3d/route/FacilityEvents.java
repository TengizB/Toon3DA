package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.relic.RelicSource;
import ge.tbegvadze.toon3d.util.BalanceConfig;
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
 *   <li>DISTRESS BEACON — information (next-region scan + a small bounty) vs a credit haul.</li>
 *   <li>ABANDONED STASH — a risk gamble (a weapon level + a med pack vs a mimic bite) vs leave it.</li>
 *   <li>SURVIVING TECH — escort (armour to full + a perk rank, next floor +budget) vs sell their keycard.</li>
 *   <li>CORRUPTED SHRINE — a Faustian pull (full heal + a weapon level, next floor hotter) vs a perk rank.</li>
 *   <li>DATA TERMINAL — the pure-lore breather: a perk rank, no downside.</li>
 *   <li>QUARANTINE LOCKER — break the seal for one of two cursed relics, or sell it unopened.</li>
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
        registry.register(quarantineLocker());
    }

    // Balance-overhaul order 6 (F15): every choice pays — and costs — in LADDER CURRENCIES (EventEffect): weapon
    // levels, a perk rank of a carried tag, heal / armour fractions, credits in floors of income. R-EVENT-VALUE
    // requires every event to offer at least one choice worth a COMBAT floor's supply.

    /** QUARANTINE LOCKER — a cursed relic (each helps, each costs) or the containment bounty. */
    private static FacilityEvent quarantineLocker() {
        return FacilityEvent.builder("quarantine_locker")
                .title("QUARANTINE LOCKER")
                .narrative("A sealed locker stamped DO NOT OPEN.",
                           "Something inside is still warm.")
                .choice("BREAK THE SEAL", "Take one of two quarantined relics. Each helps. Each costs.",
                        EventEffect.builder()
                                .relicOffer(RelicSource.QUARANTINE_LOCKER)
                                .logToken("broken")
                                .build())
                .choice("SELL THE SEAL", "Report it unopened and claim the containment bounty.",
                        EventEffect.builder()
                                .credits(BalanceConfig.EVENT_CREDIT_FLOORS_LARGE)
                                .resultText("SEAL REPORTED — BOUNTY BANKED")
                                .logToken("sold")
                                .build())
                .build();
    }

    /** DISTRESS BEACON — trade information for resources. */
    private static FacilityEvent distressBeacon() {
        return FacilityEvent.builder("distress_beacon")
                .title("DISTRESS BEACON")
                .narrative("A looping signal pulses from the console.",
                           "Someone, or something, is still transmitting.")
                .choice("ANSWER", "Triangulate the source — map the next region, claim a small bounty.",
                        EventEffect.builder()
                                .revealNextRegion()
                                .credits(BalanceConfig.EVENT_CREDIT_FLOORS_SMALL)
                                .resultText("SIGNAL TRIANGULATED — SECTOR MAPPED")
                                .logToken("answered")
                                .build())
                .choice("IGNORE", "Strip the beacon for its power cells — sell them for a credit haul.",
                        EventEffect.builder()
                                .credits(BalanceConfig.EVENT_CREDIT_FLOORS_LARGE)
                                .resultText("POWER CELLS SALVAGED — CREDITS BANKED")
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
                .choice("FORCE THE LOCKER", "60%: a weapon upgrade kit, a med pack and a credit stash. 40%: something bites back.",
                        EventEffect.builder()
                                .gamble(RouteMapConstants.EVENT_STASH_LOOT_CHANCE,
                                        BalanceConfig.EVENT_GAMBLE_FAIL_HEALTH_FRACTION)
                                .weaponLevels(1)
                                .heal(BalanceConfig.EVENT_GAMBLE_HEAL_FRACTION)
                                .credits(BalanceConfig.EVENT_STASH_CREDIT_FLOORS)
                                .resultText("LOCKER FORCED — WEAPON UPGRADED, CREDITS FOUND")
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
                .choice("ESCORT THEM", "They patch your armour, teach you a trick and pay you — but the next floor is hotter.",
                        EventEffect.builder()
                                .armour(BalanceConfig.EVENT_TECH_ARMOUR_FRACTION)
                                .perkRank()
                                .credits(BalanceConfig.EVENT_CREDIT_FLOORS_SMALL)
                                .nextFloorBudgetBonus(RouteMapConstants.EVENT_NEXT_FLOOR_BUDGET_BONUS)
                                .resultText("ARMOUR REPAIRED — THEY FOLLOW YOU DOWN")
                                .logToken("escorted")
                                .build())
                .choice("TAKE THE KEYCARD", "Take their keycard and go — sell the access codes.",
                        EventEffect.builder()
                                .credits(BalanceConfig.EVENT_CREDIT_FLOORS_SMALL)
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
                .choice("TOUCH IT", "Full health, a surging weapon and its tithe of credits — but the next floor turns on you.",
                        EventEffect.builder()
                                .heal(BalanceConfig.EVENT_SHRINE_HEAL_FRACTION)
                                .weaponLevels(1)
                                .credits(BalanceConfig.EVENT_CREDIT_FLOORS_SMALL)
                                .nextFloorBudgetBonus(RouteMapConstants.EVENT_NEXT_FLOOR_BUDGET_BONUS)
                                .resultText("POWER FLOODS IN — THE FACILITY RECOILS")
                                .logToken("touched")
                                .build())
                .choice("DESTROY IT", "Smash the growth — what you learn from it sharpens you.",
                        EventEffect.builder()
                                .perkRank()
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
                .choice("DOWNLOAD", "Archive the logs — a field technique worth learning and access codes to sell, no risk.",
                        EventEffect.builder()
                                .perkRank()
                                .credits(BalanceConfig.EVENT_CREDIT_FLOORS_SMALL)
                                .resultText("LOGS ARCHIVED — TECHNIQUE LEARNED, CODES SOLD")
                                .logToken("downloaded")
                                .build())
                .build();
    }
}
