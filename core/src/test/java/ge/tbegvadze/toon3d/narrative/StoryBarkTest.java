package ge.tbegvadze.toon3d.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.StoryUiConstants;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Headless guards for the Story UI order-2 BARK LAYER: the catalog is complete and readable, the
 * pools shift tone by story region, the planet keeps its Region-1 silence, one-shot beats fire once
 * ever, death does not rewind the story, and the queue/rate-limit/priority rules hold.
 *
 * <p>Everything here runs without a LibGDX context — which is the point of keeping the bark brain in
 * {@code …toon3d.narrative}.
 */
class StoryBarkTest {

    /** A frame step small enough that timing assertions are exact to a few hundredths. */
    private static final float FRAME_SECONDS = 1f / 60f;

    private static BarkSystem newSystem(StoryProgress progress) {
        return new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(), progress, 1234L);
    }

    private static BarkSystem newSystemInRegion(StoryRegion region) {
        StoryProgress progress = new StoryProgress();
        progress.reachRegion(region);
        return newSystem(progress);
    }

    /** Runs the system forward, one frame at a time, for the given number of seconds. */
    private static void advance(BarkSystem system, float seconds) {
        int frames = Math.max(1, Math.round(seconds / FRAME_SECONDS));
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            system.update(FRAME_SECONDS);
        }
    }

    /** Total on-screen life of one delivered bark. */
    private static float barkLifetimeSeconds() {
        return StoryUiConstants.STORY_FADE_IN_SECONDS
                + StoryUiConstants.STORY_BARK_HOLD_SECONDS
                + StoryUiConstants.STORY_FADE_OUT_SECONDS;
    }

    // -------------------------------------------------------------------------
    // Catalog integrity — every line exists, and every line is readable
    // -------------------------------------------------------------------------

    @Test
    void everyCatalogRowResolvesToRealText() {
        StoryStrings strings = StoryStrings.defaults();
        for (BarkDefinition row : BarkCatalog.defaultRegistry().getAll()) {
            assertTrue(strings.has(row.getTextStringId()),
                    "bark " + row.getId() + " has no text for id " + row.getTextStringId());
            assertFalse(strings.get(row.getTextStringId()).trim().isEmpty(),
                    "bark " + row.getId() + " resolves to blank text");
        }
    }

    @Test
    void everyBarkFitsTheTwoLineCap() {
        StoryStrings strings = StoryStrings.defaults();
        for (BarkDefinition row : BarkCatalog.defaultRegistry().getAll()) {
            String text = strings.get(row.getTextStringId());
            if (row.getSpeaker().getTypeStyle().isUpperCase()) {
                text = text.toUpperCase(Locale.ROOT);   // as the renderer will draw it
            }
            List<String> wrapped = StoryText.wrapToMaxChars(text, StoryUiConstants.STORY_LINE_MAX_CHARS);
            assertTrue(wrapped.size() <= StoryUiConstants.STORY_BARK_MAX_LINES,
                    "bark " + row.getId() + " wraps to " + wrapped.size() + " lines: " + text);
        }
    }

    /** The shipped asset is the real table; the Java defaults are only its fallback. */
    @Test
    void shippedStringAssetCoversEveryCatalogRow() throws IOException {
        File assetFile = new File("../assets/" + StoryUiConstants.STORY_STRINGS_ASSET_PATH);
        assumeTrue(assetFile.exists(), "string asset not reachable from the test working directory");
        StoryStrings shipped = StoryStrings.fromProperties(
                new String(Files.readAllBytes(assetFile.toPath()), StandardCharsets.UTF_8));
        for (BarkDefinition row : BarkCatalog.defaultRegistry().getAll()) {
            assertTrue(shipped.has(row.getTextStringId()),
                    "story-strings.properties is missing " + row.getTextStringId());
            assertEquals(StoryStrings.defaults().get(row.getTextStringId()),
                    shipped.get(row.getTextStringId()),
                    "asset and BarkStrings disagree on " + row.getTextStringId());
        }
    }

    @Test
    void everyTriggerHasAtLeastOneLineSomewhere() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        for (BarkTrigger trigger : BarkTrigger.values()) {
            assertFalse(registry.getForTrigger(trigger).isEmpty(),
                    "no bark registered for trigger " + trigger);
        }
    }

    @Test
    void triggerCooldownTableMatchesTheTriggerEnum() {
        assertEquals(BarkTrigger.values().length,
                StoryUiConstants.STORY_BARK_TRIGGER_COOLDOWN_SECONDS.length,
                "cooldown table drifted from BarkTrigger");
    }

    // -------------------------------------------------------------------------
    // Placement — the bark must never sit on any other UI element
    // -------------------------------------------------------------------------

    @Test
    void barkAnchorClearsTheMiniMapAndTheWeaponSprite() {
        float miniMapRightX = Constants.MINI_MAP_ORIGIN_X + Constants.MINI_MAP_WORLD_SIZE;
        assertTrue(StoryUiConstants.STORY_BARK_X >= miniMapRightX,
                "bark overlaps the mini-map");
        float weaponSpriteTopY = WeaponConstants.WEAPON_HUD_BASE_Y + WeaponConstants.WEAPON_HUD_HEIGHT;
        assertTrue(StoryUiConstants.STORY_BARK_Y >= weaponSpriteTopY,
                "bark overlaps the weapon sprite");
        assertTrue(StoryUiConstants.STORY_BARK_TOP_Y <= Constants.WORLD_HEIGHT,
                "bark runs off the top of the screen");
    }

    // -------------------------------------------------------------------------
    // Region gating — the tone engine
    // -------------------------------------------------------------------------

    @Test
    void killPoolShiftsToneWithTheDescent() {
        BarkSystem surface = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        assertTrue(surface.request(BarkTrigger.KILL));
        advance(surface, FRAME_SECONDS);
        assertTrue(surface.getActiveBarkId().startsWith("bark.kill.rings"),
                "surface kills should use the corporate-cheerful pool, got " + surface.getActiveBarkId());

        BarkSystem deep = newSystemInRegion(StoryRegion.WOUND);
        assertTrue(deep.request(BarkTrigger.KILL));
        advance(deep, FRAME_SECONDS);
        assertTrue(deep.getActiveBarkId().startsWith("bark.kill.wound"),
                "deep kills should use the doubting pool, got " + deep.getActiveBarkId());
    }

    @Test
    void theOrganizationSpeaksAtEveryGateAndEscalates() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        for (StoryRegion region : StoryRegion.values()) {
            boolean found = false;
            for (BarkDefinition row : registry.getForTrigger(BarkTrigger.REGION_GATE_ORDER)) {
                if (row.matchesRegion(region)) {
                    assertEquals(Speaker.ORGANIZATION, row.getSpeaker());
                    assertTrue(row.isOneShot(), "gate order " + row.getId() + " must be one-shot");
                    found = true;
                }
            }
            assertTrue(found, "no Organization gate order for " + region);
        }
    }

    @Test
    void thePlanetIsSilentInRegionOneAndSpeaksBelowIt() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        for (BarkDefinition row : registry.getAll()) {
            if (row.getSpeaker() != Speaker.PLANET) continue;
            assertFalse(row.matchesRegion(StoryRegion.HABITATION_RINGS),
                    "the planet must have no Region 1 lines: " + row.getId());
        }
        // ...and a deep-strata milestone up there therefore says nothing at all.
        BarkSystem surface = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        assertFalse(surface.request(BarkTrigger.DEEP_STRATA));
        advance(surface, FRAME_SECONDS);
        assertFalse(surface.hasActiveBark());

        BarkSystem galleries = newSystemInRegion(StoryRegion.HARVESTING_GALLERIES);
        assertTrue(galleries.request(BarkTrigger.DEEP_STRATA));
        advance(galleries, FRAME_SECONDS);
        assertEquals(Speaker.PLANET, galleries.getActiveSpeaker());
    }

    // -------------------------------------------------------------------------
    // Persistence — one-shots fire once, death never rewinds
    // -------------------------------------------------------------------------

    @Test
    void oneShotBeatsFireOnceEverAcrossRuns() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();

        BarkSystem firstRun = newSystem(new StoryProgress(store));
        firstRun.getProgress().reachRegion(StoryRegion.HABITATION_RINGS);
        assertTrue(firstRun.request(BarkTrigger.REGION_ENTERED));
        advance(firstRun, FRAME_SECONDS);
        assertEquals("bark.region.rings", firstRun.getActiveBarkId());

        // A later run re-enters the same region: the mandatory beat must NOT replay.
        BarkSystem laterRun = newSystem(new StoryProgress(store));
        assertFalse(laterRun.request(BarkTrigger.REGION_ENTERED),
                "a cleared region replayed its one-shot beat");
        advance(laterRun, FRAME_SECONDS);
        assertFalse(laterRun.hasActiveBark());
    }

    @Test
    void deathDoesNotRewindTheStory() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress deepRun = new StoryProgress(store);
        deepRun.reachRegion(StoryRegion.RELIQUARY);

        // The player dies and a brand-new run starts back at the surface.
        StoryProgress freshRun = new StoryProgress(store);
        assertEquals(StoryRegion.RELIQUARY, freshRun.getDeepestRegion());
        assertFalse(freshRun.reachRegion(StoryRegion.HABITATION_RINGS),
                "re-entering Region 1 must not deepen (or shallow) the story");
        assertEquals(StoryRegion.RELIQUARY, freshRun.getDeepestRegion());

        // ...so ORA's tone is still the Reliquary's, not the surface's.
        BarkSystem system = newSystem(freshRun);
        assertTrue(system.request(BarkTrigger.KILL));
        advance(system, FRAME_SECONDS);
        assertTrue(system.getActiveBarkId().startsWith("bark.kill.reliquary"),
                "tone rewound on death, got " + system.getActiveBarkId());
    }

    // -------------------------------------------------------------------------
    // Queue, rate limit, priority, suppression
    // -------------------------------------------------------------------------

    @Test
    void onlyOneBarkIsEverOnScreenAndItAutoDismisses() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.request(BarkTrigger.FLOOR_ARRIVAL);
        system.request(BarkTrigger.LOW_HEALTH);
        advance(system, FRAME_SECONDS);
        assertTrue(system.hasActiveBark());
        assertEquals(1, system.getQueuedCount(), "the second line must wait, not stack");

        advance(system, barkLifetimeSeconds());
        assertFalse(system.hasActiveBark(), "a bark must auto-dismiss after its hold");
    }

    @Test
    void theRateLimitSpacesDeliveries() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        assertTrue(system.request(BarkTrigger.FLOOR_ARRIVAL));
        assertTrue(system.request(BarkTrigger.LOW_HEALTH));

        advance(system, FRAME_SECONDS);
        String firstBarkId = system.getActiveBarkId();
        assertNotNull(firstBarkId);

        // The first line ends, but the interval has not elapsed: the screen stays empty.
        advance(system, barkLifetimeSeconds());
        assertFalse(system.hasActiveBark());
        assertEquals(1, system.getQueuedCount());

        advance(system, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS - barkLifetimeSeconds() + 0.2f);
        assertTrue(system.hasActiveBark(), "the queued line never arrived after the interval");
        assertFalse(firstBarkId.equals(system.getActiveBarkId()));
    }

    @Test
    void flavorIsDroppedUnderPressureButCriticalNeverIs() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        // Nothing on screen, nothing queued, no cooldown: flavour is allowed.
        assertTrue(system.request(BarkTrigger.IDLE));
        advance(system, FRAME_SECONDS);
        assertEquals("bark.idle.rings", system.getActiveBarkId());

        // Now a line IS on screen — flavour is dropped rather than queued.
        assertFalse(system.request(BarkTrigger.BACKTRACK), "flavour queued under pressure");
        assertEquals(0, system.getQueuedCount());

        // A mandatory beat is never dropped, even with the queue at capacity.
        for (int fillIndex = 0; fillIndex < StoryUiConstants.STORY_BARK_QUEUE_CAPACITY; fillIndex++) {
            system.request(BarkTrigger.KILL);
            system.request(BarkTrigger.LOW_HEALTH);
            system.request(BarkTrigger.FLOOR_ARRIVAL);
            system.request(BarkTrigger.ENEMY_FAMILY_FIRST_SEEN, "UNDEAD");
        }
        assertTrue(system.request(BarkTrigger.REGION_ENTERED), "a mandatory beat was dropped");
    }

    @Test
    void aCriticalArrivalCutsTheCurrentBarkShort() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.request(BarkTrigger.KILL);
        advance(system, 1f);
        float fractionBefore = system.getVisibleFraction();
        assertEquals(1f, fractionBefore, 0.001f, "the bark should be fully held before the cut");

        system.request(BarkTrigger.REGION_ENTERED);
        advance(system, StoryUiConstants.STORY_FADE_OUT_SECONDS + 0.05f);
        assertFalse(system.hasActiveBark(), "the reactive line should have faded out early");
    }

    @Test
    void suppressionQueuesAndResumesIntact() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.setSuppressed(true);
        assertTrue(system.request(BarkTrigger.FLOOR_ARRIVAL), "requests must still queue when suppressed");
        advance(system, 30f);   // an overlay stays open for a long time
        assertFalse(system.hasActiveBark(), "a bark was delivered over an overlay");
        assertEquals(1, system.getQueuedCount(), "the queued line went stale behind the overlay");

        system.setSuppressed(false);
        advance(system, FRAME_SECONDS);
        assertTrue(system.hasActiveBark(), "the queue did not resume after the overlay closed");
    }

    @Test
    void aStaleReactiveLineIsDroppedRatherThanSaidLate() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.request(BarkTrigger.FLOOR_ARRIVAL);   // delivered immediately
        system.request(BarkTrigger.KILL);            // queued behind it
        advance(system, FRAME_SECONDS);
        assertEquals(1, system.getQueuedCount());

        advance(system, StoryUiConstants.STORY_BARK_QUEUE_STALE_SECONDS + 0.5f);
        assertEquals(0, system.getQueuedCount(), "a stale reactive line must be dropped");
    }

    @Test
    void aDeliveredBarkExposesAWrappedReadModel() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.request(BarkTrigger.REGION_GATE_ORDER);
        advance(system, FRAME_SECONDS);

        assertEquals(Speaker.ORGANIZATION, system.getActiveSpeaker());
        assertNotNull(system.getActiveSpeakerName());
        assertTrue(system.getActiveLineCount() > 0);
        for (int lineIndex = 0; lineIndex < system.getActiveLineCount(); lineIndex++) {
            String line = system.getActiveLines()[lineIndex];
            assertTrue(line.length() <= StoryUiConstants.STORY_LINE_MAX_CHARS);
            // The Organization's TypeStyle is ALL-CAPS, applied once at delivery.
            assertEquals(line.toUpperCase(Locale.ROOT), line);
        }
        assertEquals(Speaker.ORGANIZATION, system.consumeJustAppearedSpeaker(),
                "the sting hook must report the speaker exactly once");
        assertNull(system.consumeJustAppearedSpeaker());
    }
}
