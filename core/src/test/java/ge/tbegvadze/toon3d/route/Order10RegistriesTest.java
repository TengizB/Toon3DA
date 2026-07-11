package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.RouteMapConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Order-10 registry tests: the narrative EVENT catalog and the region-ambience / theming catalog are
 * populated, deterministic, and complete. Uses fresh registries (never the shared singletons) so the
 * tests are independent of bootstrap ordering.
 */
class Order10RegistriesTest {

    private FacilityEventRegistry freshEvents() {
        FacilityEventRegistry registry = new FacilityEventRegistry();
        RouteRegistries.registerEvents(registry);
        return registry;
    }

    private RegionAmbienceRegistry freshAmbience() {
        RegionAmbienceRegistry registry = new RegionAmbienceRegistry();
        RouteRegistries.registerRegionAmbience(registry);
        return registry;
    }

    // ---- EVENT registry ----------------------------------------------------

    @Test
    void allFiveV1EventsAreRegistered() {
        FacilityEventRegistry registry = freshEvents();
        assertEquals(5, registry.size());
        for (String id : new String[]{"distress_beacon", "abandoned_stash", "surviving_tech",
                "corrupted_shrine", "data_terminal"}) {
            assertNotNull(registry.get(id), "missing event " + id);
        }
    }

    @Test
    void everyEventHasBetweenOneAndThreeChoices() {
        FacilityEventRegistry registry = freshEvents();
        for (String id : new String[]{"distress_beacon", "abandoned_stash", "surviving_tech",
                "corrupted_shrine", "data_terminal"}) {
            FacilityEvent event = registry.get(id);
            int choiceCount = event.choices().size();
            assertTrue(choiceCount >= 1 && choiceCount <= FacilityEvent.MAX_CHOICES,
                    id + " has an illegal choice count: " + choiceCount);
            assertNotNull(event.title());
        }
    }

    @Test
    void rollForIsDeterministicAndNonNull() {
        FacilityEventRegistry registry = freshEvents();
        FacilityEvent first  = registry.rollFor(123456789L);
        FacilityEvent second = registry.rollFor(123456789L);
        assertNotNull(first);
        assertSame(first, second, "same seed must roll the same event");
    }

    @Test
    void emptyEventRegistryRollsNullRatherThanThrowing() {
        FacilityEventRegistry empty = new FacilityEventRegistry();
        assertTrue(empty.isEmpty());
        assertNull(empty.rollFor(1L));
    }

    @Test
    void duplicateEventRegistrationThrows() {
        FacilityEventRegistry registry = new FacilityEventRegistry();
        FacilityEvent event = FacilityEvent.builder("dup").title("T")
                .choice("A", "d", EventEffect.builder().build()).build();
        registry.register(event);
        assertThrows(IllegalStateException.class, () -> registry.register(event));
    }

    @Test
    void distressBeaconTradesRevealAgainstAmmo() {
        FacilityEvent beacon = freshEvents().get("distress_beacon");
        assertEquals(2, beacon.choices().size());
        assertTrue(beacon.choices().get(0).effect().revealNextRegion());
        assertTrue(beacon.choices().get(1).effect().ammoBoxes() > 0);
    }

    @Test
    void abandonedStashOffersAGamble() {
        FacilityEvent stash = freshEvents().get("abandoned_stash");
        assertTrue(stash.choices().get(0).effect().isGamble());
        // The "leave it" option is a clean walk-away (no gamble).
        assertFalse(stash.choices().get(1).effect().isGamble());
    }

    // ---- Region ambience registry ------------------------------------------

    @Test
    void allFourV1RegionsAreRegistered() {
        RegionAmbienceRegistry registry = freshAmbience();
        assertEquals(4, registry.size());
        for (String theme : new String[]{RouteMapConstants.REGION_A_THEME, RouteMapConstants.REGION_B_THEME,
                RouteMapConstants.REGION_C_THEME, RouteMapConstants.REGION_D_THEME}) {
            assertTrue(registry.has(theme), "missing region ambience for " + theme);
        }
    }

    @Test
    void everyRegionCarriesCompleteThemingData() {
        RegionAmbienceRegistry registry = freshAmbience();
        for (String theme : new String[]{RouteMapConstants.REGION_A_THEME, RouteMapConstants.REGION_B_THEME,
                RouteMapConstants.REGION_C_THEME, RouteMapConstants.REGION_D_THEME}) {
            RegionAmbience ambience = registry.get(theme);
            assertNotNull(ambience.displayName());
            assertNotNull(ambience.crestPainterId());
            assertNotNull(ambience.overlayThemeId());
            assertFalse(ambience.wallPalette().isEmpty(), theme + " has an empty wall palette");
            assertNotNull(ambience.enemyFactionId());
            assertNotNull(ambience.lightingMoodId());
            assertNotNull(ambience.musicId());
            assertNotNull(ambience.bossId());
            assertNotNull(ambience.gateFlavour());
        }
    }

    @Test
    void redAlertFrequencyEscalatesWithDepth() {
        RegionAmbienceRegistry registry = freshAmbience();
        float outer   = registry.get(RouteMapConstants.REGION_A_THEME).redAlertFrequency();
        float breach  = registry.get(RouteMapConstants.REGION_D_THEME).redAlertFrequency();
        assertTrue(outer < breach, "the breach should feel more hostile than the outer facility");
    }

    @Test
    void unknownThemeFallsBackRatherThanReturningNull() {
        RegionAmbienceRegistry registry = freshAmbience();
        assertNull(registry.get("no_such_region"));
        assertNotNull(registry.getOrFallback("no_such_region"), "an unknown theme should fall back");
    }
}
