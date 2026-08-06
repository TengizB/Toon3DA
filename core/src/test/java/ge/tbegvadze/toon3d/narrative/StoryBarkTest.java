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

    /** Dismisses the bark on screen and runs its fade-out to completion. */
    private static void dismissAndSettle(BarkSystem system) {
        system.dismissActiveBark();
        advance(system, StoryUiConstants.STORY_FADE_OUT_SECONDS + 0.05f);
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
    // The order-5 moment schedule: the cold open and the tutorial
    // -------------------------------------------------------------------------

    /**
     * Every control ORA teaches must actually have a line, or a player is left holding a button
     * nobody told them about — the failure mode the tutorial-through-ORA design exists to prevent.
     */
    @Test
    void everyControlHintHasALine() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        StoryStrings strings  = StoryStrings.defaults();
        for (ControlHint hint : ControlHint.values()) {
            BarkDefinition found = null;
            for (BarkDefinition row : registry.getForTrigger(BarkTrigger.CONTROL_HINT)) {
                if (row.matchesSubject(hint.getSubjectKey())) found = row;
            }
            assertNotNull(found, "no line teaches " + hint);
            assertTrue(strings.has(found.getTextStringId()),
                    hint + " has no text for " + found.getTextStringId());
        }
    }

    /**
     * The tutorial and the cold open fire exactly once for the life of the save, and nothing may
     * drop them: a hint that loses a race with a flavour quip is a control the player never learns.
     */
    @Test
    void theColdOpenAndTheTutorialAreOneShotAndNeverDropped() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        BarkTrigger[] mandatory = { BarkTrigger.RUN_START, BarkTrigger.CONTROL_HINT };
        for (BarkTrigger trigger : mandatory) {
            for (BarkDefinition row : registry.getForTrigger(trigger)) {
                assertTrue(row.isOneShot(), row.getId() + " would repeat on a later run");
                assertEquals(BarkPriority.STORY_CRITICAL, row.getPriority(),
                        row.getId() + " can be dropped as chatter");
            }
        }
    }

    /**
     * ORA introduces herself PROPERLY to a new player — who is talking, what happened to them, and
     * why their memory came back short — and then never again once they know her (narrative-rework
     * order-2 D).  A name alone is not an introduction, which is what this used to assert.
     */
    @Test
    void theColdOpenIntroducesOraAndThenStops() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        BarkSystem firstRun = newSystem(new StoryProgress(store));

        // Run 1: the three beats a stranger needs, each asked for by name so they cannot arrive out
        // of order (the pool must not hand back "you died an hour ago" before "I'm ORA").
        for (IntroBeat beat : IntroBeat.values()) {
            if (!beat.isColdOpen() || !beat.isAvailableAt(1)) continue;
            assertTrue(firstRun.request(beat.getTrigger(), beat.getSubjectKey()),
                    "the cold open never asked for " + beat);
            // A one-shot is spent on DELIVERY, not on being asked for, so each must be read.
            advance(firstRun, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 0.05f);
            assertEquals("bark.intro." + beat.getCatalogKey(), firstRun.getActiveBarkId(),
                    "the cold open delivered the wrong beat for " + beat);
            dismissAndSettle(firstRun);
        }

        // A later run gets none of them back — she is not a stranger any more.
        BarkSystem laterRun = newSystem(new StoryProgress(store));
        for (IntroBeat beat : IntroBeat.values()) {
            if (!beat.isColdOpen() || !beat.isAvailableAt(1)) continue;
            assertFalse(laterRun.request(beat.getTrigger(), beat.getSubjectKey()),
                    "ORA re-introduced herself to someone who already knows her: " + beat);
        }
    }

    /**
     * The two beats that make her matter — she is not printed, so she is the only one who remembers
     * the last body — wait for the SECOND run, because they only mean anything to a player who has
     * now died and come back themselves.
     */
    @Test
    void theRunTwoBeatsWaitForARunTwo() {
        for (IntroBeat beat : IntroBeat.values()) {
            if (!beat.isColdOpen()) continue;
            boolean isRunTwoBeat = beat == IntroBeat.CONTINUITY || beat == IntroBeat.MEMORY;
            assertEquals(!isRunTwoBeat, beat.isAvailableAt(1),
                    beat + " is offered on the wrong run");
            assertTrue(beat.isAvailableAt(2), beat + " never becomes available at all");
        }
    }

    /**
     * The rest of her introduction is DISTRIBUTED: every non-cold-open beat hangs off a moment that
     * already happens on floor one, and none of them invents a new trigger to do it.
     */
    @Test
    void theDistributedIntroBeatsRideExistingMoments() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        for (IntroBeat beat : IntroBeat.values()) {
            BarkDefinition row = registry.getById("bark.intro." + beat.getCatalogKey());
            assertNotNull(row, "no line registered for intro beat " + beat);
            assertEquals(beat.getTrigger(), row.getTrigger(), beat + " hangs off the wrong moment");
            assertTrue(row.isOneShot(), beat + " would introduce her twice");
            assertEquals(BarkPriority.STORY_CRITICAL, row.getPriority(),
                    beat + " can be dropped as chatter — an introduction that loses a race");
        }
    }

    /** A control hint waits for its own control: asking about one never spends another's line. */
    @Test
    void aControlHintOnlyAnswersItsOwnControl() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        assertTrue(system.request(BarkTrigger.CONTROL_HINT, ControlHint.RELOAD.getSubjectKey()));
        assertFalse(system.request(BarkTrigger.CONTROL_HINT, ControlHint.RELOAD.getSubjectKey()),
                "the same control was taught twice");
        assertTrue(system.request(BarkTrigger.CONTROL_HINT, ControlHint.HEAL.getSubjectKey()),
                "teaching one control consumed another's line");
    }

    /** The mandatory log beats are the ones a player must not miss, so nothing may drop them. */
    @Test
    void theMandatoryLogBeatsAreOneShotAndCritical() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        String[] mandatoryIds = { "bark.log.galleries.yield", "bark.log.reliquary.cradle" };
        for (String id : mandatoryIds) {
            BarkDefinition row = null;
            for (BarkDefinition candidate : registry.getForTrigger(BarkTrigger.LOG_FOUND)) {
                if (candidate.getId().equals(id)) row = candidate;
            }
            assertNotNull(row, "the mandatory log beat " + id + " is missing");
            assertTrue(row.isOneShot(), id + " would replay on a later run");
            assertEquals(BarkPriority.STORY_CRITICAL, row.getPriority(),
                    id + " can be dropped as chatter");
        }
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

    /**
     * Every region gets its cold order, in the Organization's own voice, once.  The only other thing
     * allowed on this moment is ORA's aside naming the word they just used (the narrative-rework
     * order-3 vocabulary ladder) — which is the entire point of hanging a naming line here: the
     * player hears "Overseer" from them and is told what an Overseer is one panel later.
     */
    @Test
    void theOrganizationSpeaksAtEveryGateAndEscalates() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        for (StoryRegion region : StoryRegion.values()) {
            boolean found = false;
            for (BarkDefinition row : registry.getForTrigger(BarkTrigger.REGION_GATE_ORDER)) {
                if (!row.matchesRegion(region)) continue;
                assertTrue(row.isOneShot(), "gate order " + row.getId() + " must be one-shot");
                if (row.getSpeaker() != Speaker.ORGANIZATION) {
                    assertTrue(StoryTermCatalog.isIntroLine(row.getTextStringId()),
                            "only the Organization and the vocabulary ladder may speak at a gate: "
                                    + row.getId());
                    continue;
                }
                found = true;
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
    void onlyOneBarkIsEverOnScreenAndItNeverStacks() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.request(BarkTrigger.FLOOR_ARRIVAL);
        system.request(BarkTrigger.LOW_HEALTH);
        advance(system, FRAME_SECONDS);
        assertTrue(system.hasActiveBark());
        assertEquals(1, system.getQueuedCount(), "the second line must wait, not stack");
    }

    /** The headline rule of the dismissal model: a line NEVER vanishes on its own. */
    @Test
    void aBarkNeverDisappearsByItself() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.request(BarkTrigger.FLOOR_ARRIVAL);
        advance(system, FRAME_SECONDS);
        String barkId = system.getActiveBarkId();
        assertNotNull(barkId);

        // Five minutes of play later, a slow reader still has their line.
        advance(system, 300f);
        assertTrue(system.hasActiveBark(), "a bark timed out instead of waiting for the player");
        assertEquals(barkId, system.getActiveBarkId());
        assertEquals(1f, system.getVisibleFraction(), 0.001f, "a held bark must stay fully opaque");
    }

    @Test
    void dismissingFadesTheBarkOutAndFreesTheScreen() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.request(BarkTrigger.FLOOR_ARRIVAL);
        advance(system, FRAME_SECONDS);
        assertTrue(system.hasActiveBark());

        assertTrue(system.dismissActiveBark(), "the first dismiss should take effect");
        assertFalse(system.dismissActiveBark(), "dismissing twice must be a no-op");
        assertTrue(system.isActiveBarkDismissed());
        // Mid fade-out it is still on screen, just dimmer.
        advance(system, StoryUiConstants.STORY_FADE_OUT_SECONDS / 2f);
        assertTrue(system.hasActiveBark());
        assertTrue(system.getVisibleFraction() < 1f, "a dismissed bark should be fading");

        advance(system, StoryUiConstants.STORY_FADE_OUT_SECONDS);
        assertFalse(system.hasActiveBark(), "the fade-out never finished");
    }

    /** Even a mandatory beat waits: nothing may snatch a line the player is still reading. */
    @Test
    void aCriticalArrivalWaitsInsteadOfCuttingTheCurrentLineShort() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.request(BarkTrigger.KILL);
        advance(system, FRAME_SECONDS);
        String readingBarkId = system.getActiveBarkId();

        system.request(BarkTrigger.REGION_ENTERED);
        advance(system, 60f);
        assertEquals(readingBarkId, system.getActiveBarkId(),
                "a critical beat cut short the line the player was reading");
        assertEquals(1, system.getQueuedCount());
    }

    @Test
    void theRateLimitSpacesDeliveriesFromTheMomentTheScreenFrees() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        assertTrue(system.request(BarkTrigger.FLOOR_ARRIVAL));
        assertTrue(system.request(BarkTrigger.LOW_HEALTH));

        advance(system, FRAME_SECONDS);
        String firstBarkId = system.getActiveBarkId();
        assertNotNull(firstBarkId);

        // The player reads at their own pace, then closes it. The interval starts HERE, not at
        // delivery — so a slow reader is never punished with a burst of queued lines.
        advance(system, 5f);
        dismissAndSettle(system);
        assertFalse(system.hasActiveBark());
        assertEquals(1, system.getQueuedCount());

        advance(system, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 0.2f);
        assertTrue(system.hasActiveBark(), "the queued line never arrived after the interval");
        assertFalse(firstBarkId.equals(system.getActiveBarkId()));
    }

    @Test
    void flavorIsDroppedUnderPressureButCriticalNeverIs() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        // Nothing on screen, nothing queued, no cooldown: flavour is allowed.
        assertTrue(system.request(BarkTrigger.IDLE));
        advance(system, FRAME_SECONDS);
        assertTrue(system.getActiveBarkId().startsWith("bark.idle.rings"),
                "expected an idle line, got " + system.getActiveBarkId());

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

    /**
     * A player who leaves a bark up for a long time must not be buried in a burst of stale lines
     * when they finally close it — but a MANDATORY beat still survives the wait.
     */
    @Test
    void aLongReadDropsStaleReactiveLinesButKeepsCriticalOnes() {
        BarkSystem system = newSystemInRegion(StoryRegion.HABITATION_RINGS);
        system.request(BarkTrigger.FLOOR_ARRIVAL);
        advance(system, FRAME_SECONDS);
        system.request(BarkTrigger.KILL);              // reactive, queued behind it
        system.request(BarkTrigger.REGION_ENTERED);    // mandatory, queued behind it
        assertEquals(2, system.getQueuedCount());

        advance(system, StoryUiConstants.STORY_BARK_QUEUE_STALE_SECONDS + 1f);
        assertEquals(1, system.getQueuedCount(), "the reactive line should have gone stale");

        dismissAndSettle(system);
        advance(system, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 0.2f);
        assertEquals("bark.region.rings", system.getActiveBarkId(),
                "the mandatory beat must survive however long the player reads");
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

    // -------------------------------------------------------------------------
    // Anti-spam: no repeats, deep pools, and a lore/levity balance
    // -------------------------------------------------------------------------

    /** The fix for "ORA keeps saying the same thing": a line cannot come back for a while. */
    @Test
    void aLineIsNeverRepeatedWhileItIsStillInRecentMemory() {
        BarkSystem system = newSystemInRegion(StoryRegion.RELIQUARY);
        java.util.Set<String> spoken = new java.util.HashSet<>();

        // Drain one whole pool: every delivery must be a line we have not heard yet.
        for (int barkIndex = 0; barkIndex < 4; barkIndex++) {
            if (!system.request(BarkTrigger.FLOOR_ARRIVAL)) break;
            advance(system, FRAME_SECONDS);
            String barkId = system.getActiveBarkId();
            assertNotNull(barkId, "nothing was delivered on round " + barkIndex);
            assertTrue(spoken.add(barkId), "ORA repeated " + barkId + " while it was still recent");
            dismissAndSettle(system);
            advance(system, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 0.2f);
            // The per-trigger cooldown is the other half of the anti-spam guard; skip past it.
            advance(system, StoryUiConstants
                    .STORY_BARK_TRIGGER_COOLDOWN_SECONDS[BarkTrigger.FLOOR_ARRIVAL.ordinal()] + 1f);
        }
        assertTrue(spoken.size() >= 3, "the floor-arrival pool is too shallow to avoid repeats");
    }

    /** Every repeatable moment needs a pool deep enough that the no-repeat filter has room. */
    @Test
    void repeatableMomentsHaveDeepPoolsInEveryRegion() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        BarkTrigger[] repeatable = { BarkTrigger.FLOOR_ARRIVAL, BarkTrigger.KILL,
                                     BarkTrigger.LOG_FOUND };
        for (BarkTrigger trigger : repeatable) {
            for (StoryRegion region : StoryRegion.values()) {
                int poolSize = 0;
                for (BarkDefinition row : registry.getForTrigger(trigger)) {
                    if (row.matchesRegion(region)) poolSize++;
                }
                assertTrue(poolSize >= 4,
                        trigger + " in " + region + " has only " + poolSize + " lines");
            }
        }
    }

    /** ORA jokes sometimes, not constantly: lore must outweigh levity, everywhere she speaks. */
    @Test
    void loreOutweighsLevityOverall() {
        float loreWeight = 0f;
        float levityWeight = 0f;
        for (BarkDefinition row : BarkCatalog.defaultRegistry().getAll()) {
            if (row.getTone() == BarkTone.LEVITY) levityWeight += row.getWeight();
            else                                   loreWeight   += row.getWeight();
        }
        assertTrue(loreWeight > levityWeight * 2f,
                "the catalog is too jokey: lore=" + loreWeight + " levity=" + levityWeight);
    }

    /** ...but she is not humourless early on, and never flippant at the bottom. */
    @Test
    void humourLivesNearTheSurfaceAndDiesNearTheCore() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        boolean surfaceHasLevity = false;
        for (BarkDefinition row : registry.getForTrigger(BarkTrigger.FLOOR_ARRIVAL)) {
            if (row.matchesRegion(StoryRegion.HABITATION_RINGS) && row.getTone() == BarkTone.LEVITY) {
                surfaceHasLevity = true;
            }
        }
        assertTrue(surfaceHasLevity, "Region 1 ORA should still be funny");

        for (BarkDefinition row : registry.getAll()) {
            boolean deepOnly = !row.matchesRegion(StoryRegion.HABITATION_RINGS)
                            && !row.matchesRegion(StoryRegion.HARVESTING_GALLERIES)
                            && row.matchesRegion(StoryRegion.CORE);
            if (deepOnly) {
                assertEquals(BarkTone.LORE, row.getTone(),
                        "a joke survived into the deepest strata: " + row.getId());
            }
        }
    }

    /** The planet never jokes, in any region it speaks. */
    @Test
    void thePlanetOnlyEverSpeaksLore() {
        for (BarkDefinition row : BarkCatalog.defaultRegistry().getAll()) {
            if (row.getSpeaker() != Speaker.PLANET) continue;
            assertEquals(BarkTone.LORE, row.getTone(), "the planet made a joke: " + row.getId());
        }
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
