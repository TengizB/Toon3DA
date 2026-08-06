package ge.tbegvadze.toon3d.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.enemy.EnemyFamily;
import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.enemy.SpecialAbility;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * THE SKIMMER PATH (Story UI order-6 Part C) — the test that keeps the whole story readable by
 * someone who reads nothing optional.
 *
 * <p>The design promise is specific: <em>a player who dismisses every bark they can, never opens the
 * codex, and taps through every choice as fast as it appears must still understand who they are,
 * what the Organization does, and what choice they face at the Core.</em>  That only holds if the
 * spine of the story rides channels a skimmer cannot skip — the boot card, one-shot
 * {@code STORY_CRITICAL} barks, and the blocking exchanges — and if none of it hides behind the
 * archive.  This file asserts exactly that, so a later order cannot quietly move a spine beat onto
 * an optional channel.
 *
 * <p>The other half of Part C is the promise never to TRAP anyone: nothing in the story UI advances
 * itself, and nothing waits on a timer.  That is asserted here across all three channels at once,
 * because it is a property of the layer rather than of any one system.
 *
 * <p>Headless: no LibGDX context anywhere.
 */
class StorySkimmerPathTest {

    private static final float FRAME_SECONDS = 1f / 60f;

    // -------------------------------------------------------------------------
    // The spine rides unskippable channels
    // -------------------------------------------------------------------------

    /**
     * Every beat the story cannot afford to lose is a one-shot {@code STORY_CRITICAL} bark, which is
     * never dropped as chatter and never expires in the queue.  If a beat below stops being critical
     * a skimmer can miss it, and the plot develops a hole.
     */
    @Test
    void everyMandatoryBeatIsAOneShotCriticalBark() {
        BarkRegistry registry = BarkCatalog.defaultRegistry();
        List<String> spineIds = new ArrayList<>();
        for (StoryRegion region : StoryRegion.values()) {
            spineIds.add("bark.region." + region.getCatalogKey());   // this is where you are
            spineIds.add("bark.gate."   + region.getCatalogKey());   // this is what they want
        }
        spineIds.add("bark.log.galleries.yield");     // they are mining something that heals
        spineIds.add("bark.log.reliquary.cradle");    // the reserve is the planet, and you are made of it
        for (IntroBeat beat : IntroBeat.values()) {
            // who is talking to you, what happened to you, why your memory came back short, and
            // what she is for — the whole of ORA's introduction (narrative-rework order-2 D)
            spineIds.add("bark.intro." + beat.getCatalogKey());
        }
        for (ControlHint hint : ControlHint.values()) {
            spineIds.add("bark.control." + hint.getCatalogKey());    // how to play at all
        }
        // THE BESTIARY VOICE (narrative-rework order-5). "What am I fighting?" is a comprehension-bar
        // question, so the answer may not ride a channel a skimmer can lose. Looping the game's own
        // enums is also the COVERAGE gate the order asks for: a family, boss or special ability added
        // without a line fails here (missing row) or in the localisation sweep (unresolved id),
        // which is what keeps the shipped rows and EnemyType.tacticalVerb() from drifting apart.
        for (EnemyFamily family : EnemyFamily.values()) {
            spineIds.add(BestiaryCatalog.familyBarkId(family));      // what it is, and how it kills you
            spineIds.add(BestiaryCatalog.deepFamilyBarkId(family));  // and what it actually was
        }
        for (SpecialAbility ability : SpecialAbility.values()) {
            spineIds.add(BestiaryCatalog.abilityBarkId(ability));    // the rules you learn by dying
        }
        for (EnemyType type : EnemyType.values()) {
            // A boss fight is the whole floor and has no lull to wait for, so its line is mandatory;
            // ordinary archetypes are REACTIVE by design and are deliberately NOT on the spine.
            if (type.role() == EnemyRole.BOSS) spineIds.add(BestiaryCatalog.archetypeBarkId(type));
        }
        spineIds.add(BestiaryCatalog.BOSS_ARENA_BARK_ID);            // the room was built for it
        spineIds.add(BestiaryCatalog.BOSS_DEFEATED_BARK_ID);         // and they kept sending shifts past

        for (int idIndex = 0; idIndex < spineIds.size(); idIndex++) {
            BarkDefinition row = registry.getById(spineIds.get(idIndex));
            assertNotNull(row, "spine beat missing from the catalog: " + spineIds.get(idIndex));
            assertEquals(BarkPriority.STORY_CRITICAL, row.getPriority(),
                    row.getId() + " is on the spine and must never be dropped as chatter");
            assertTrue(row.isOneShot(), row.getId() + " must fire exactly once, ever");
        }
    }

    /**
     * A critical beat survives everything the pacing budget throws at it: a full queue of flavour, a
     * combat spike, and the rate limit.  That is what "unskippable" means in practice — not that the
     * player cannot dismiss it, but that the game never decides not to say it.
     */
    @Test
    void aCriticalBeatSurvivesAFullQueueAndAFight() {
        BarkSystem barks = new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(),
                                          new StoryProgress(), 5L);
        // Fill the queue with ordinary lines and start a fight on top of it.
        for (int fillIndex = 0; fillIndex < StoryUiConstants.STORY_BARK_QUEUE_CAPACITY + 2; fillIndex++) {
            barks.request(BarkTrigger.FLOOR_ARRIVAL);
            barks.request(BarkTrigger.KILL);
        }
        barks.setCombatSpike(true);
        assertTrue(barks.request(BarkTrigger.REGION_ENTERED),
                "a mandatory beat must be accepted even with the queue full and a fight on");

        // Play out the fight, then the lull. The beat must arrive.
        advance(barks, 30f);
        barks.setCombatSpike(false);
        String seen = drainUntilSeen(barks, "bark.region.rings", 120f);
        assertEquals("bark.region.rings", seen, "the region beat never reached the player");
    }

    /**
     * WHO THEY ARE: the reprint card opens every single run and states the fact plainly — the
     * previous instance is dead, this one was printed, and here is its number.  A skimmer cannot
     * miss it, because it is the screen the run starts on.
     */
    @Test
    void theSkimmerCannotMissTheReprint() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        for (StoryRegion region : StoryRegion.values()) {
            StoryProgress progress = new StoryProgress(store);
            progress.reachRegion(region);
            BootCardSystem card = new BootCardSystem(BootCardCatalog.defaultRegistry(),
                                                     StoryStrings.defaults(), progress, 3L);
            assertTrue(card.present(BootCardVariant.REPRINT),
                    "the reprint card must open at every depth of the story");
            assertTrue(card.getSystemLineCount() > 0, "the machine states what happened");
            assertTrue(card.getWakeLineCount() > 0,
                    "ORA speaks on every reprint, at " + region);
            assertTrue(card.isContinueAllowed(), "and the card is never a trap");
            card.clear();
        }
        assertTrue(new StoryProgress(store).getReprintCount() >= StoryRegion.values().length,
                "the counter climbs on every print — that number IS the premise");
    }

    /**
     * WHAT THE ORGANIZATION DOES: the two reveal beats and the escalating gate orders all ride the
     * critical channel, so the skimmer learns the harvest and the reserve without opening anything.
     */
    @Test
    void theSkimmerLearnsWhatTheOrganizationDoes() {
        StoryStrings strings  = StoryStrings.defaults();
        BarkRegistry registry = BarkCatalog.defaultRegistry();

        BarkDefinition yield  = registry.getById("bark.log.galleries.yield");
        BarkDefinition cradle = registry.getById("bark.log.reliquary.cradle");
        assertFalse(strings.get(yield.getTextStringId()).trim().isEmpty());
        assertFalse(strings.get(cradle.getTextStringId()).trim().isEmpty());

        // The gate orders escalate across all five regions, in the Organization's own voice.
        for (StoryRegion region : StoryRegion.values()) {
            BarkDefinition gate = registry.getById("bark.gate." + region.getCatalogKey());
            assertEquals(Speaker.ORGANIZATION, gate.getSpeaker(),
                    "the demand must come from them, not be reported by ORA");
        }
    }

    /**
     * THE CHOICE AT THE CORE: the ending is a blocking exchange, which is the one channel a skimmer
     * physically cannot tap past — there is no blank "next", so the run cannot end without them
     * choosing how.
     */
    @Test
    void theSkimmerStillHasToChooseAnEnding() {
        StoryProgress  progress = new StoryProgress();
        ExchangeSystem system   = new ExchangeSystem(ExchangeCatalog.defaultRegistry(),
                                                     StoryStrings.defaults(), progress, 23L);
        assertTrue(system.requestById(ExchangeCatalog.CORE_ENDING_EXCHANGE_ID));
        assertTrue(system.openPending());
        assertTrue(system.isAwaitingChoice());

        // Tap through as fast as the UI allows: there is no option that means "skip this".
        assertTrue(system.getOptionCount() >= StoryUiConstants.STORY_EXCHANGE_MIN_OPTIONS);
        advance(system, 60f);
        assertTrue(system.isAwaitingChoice(),
                "the ending must not resolve itself while the player does nothing");
    }

    // -------------------------------------------------------------------------
    // Nothing in the layer traps the player, and nothing advances on its own
    // -------------------------------------------------------------------------

    /**
     * One tap always advances, and no tap is ever REQUIRED by a clock.  Asserted across all three
     * channels at once, because "we never put the player on a timer" is a property of the layer.
     */
    @Test
    void noChannelAdvancesItself() {
        StoryProgress progress = new StoryProgress();

        BarkSystem barks = new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(),
                                          progress, 29L);
        advance(barks, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 1f);
        barks.request(BarkTrigger.FLOOR_ARRIVAL);
        advance(barks, 1f);
        assertTrue(barks.hasActiveBark());
        advance(barks, 120f);
        assertTrue(barks.hasActiveBark(), "a bark waits for the player, forever");
        assertTrue(barks.dismissActiveBark(), "and one tap always closes it");

        BootCardSystem card = new BootCardSystem(BootCardCatalog.defaultRegistry(),
                                                 StoryStrings.defaults(), progress, 31L);
        card.present(BootCardVariant.REPRINT);
        for (int frame = 0; frame < 60 * 120; frame++) card.update(FRAME_SECONDS);
        assertFalse(card.isContinueRequested(), "the boot card never continues by itself");
        card.requestContinue();
        assertTrue(card.isContinueRequested(), "and one tap always continues it");

        ExchangeSystem exchanges = new ExchangeSystem(ExchangeCatalog.defaultRegistry(),
                                                      StoryStrings.defaults(), progress, 37L);
        assertTrue(exchanges.requestById(ExchangeCatalog.CORE_ENDING_EXCHANGE_ID));
        exchanges.openPending();
        advance(exchanges, 120f);
        assertTrue(exchanges.isAwaitingChoice(), "an exchange waits for an answer, forever");
    }

    // -------------------------------------------------------------------------
    // The archive is never load-bearing
    // -------------------------------------------------------------------------

    /**
     * A run in which the codex is never opened must lose no beat.  The archive holds only the LONG
     * version of lines that already landed elsewhere, so an empty codex changes nothing about what
     * the story channels say.
     */
    @Test
    void theCodexIsNeverOnTheSpine() {
        StoryProgress progress = new StoryProgress();
        CodexSystem   codex    = new CodexSystem(CodexCatalog.defaultRegistry(),
                                                 StoryStrings.defaults(), progress,
                                                 new StorySettings());
        BarkSystem barks = new BarkSystem(BarkCatalog.defaultRegistry(), StoryStrings.defaults(),
                                          progress, 41L);

        // Every critical beat delivers with the archive completely empty and never opened.  The
        // engine's archiving hook runs either way (World.archiveDeliveredStoryLine), which is what
        // makes the codex a RECORD of the run rather than a prerequisite for it.
        for (StoryRegion region : StoryRegion.values()) {
            progress.reachRegion(region);
            assertTrue(barks.request(BarkTrigger.REGION_ENTERED)
                            || progress.hasSeen("bark.region." + region.getCatalogKey()),
                    "the region beat must not depend on the codex");
            advance(barks, StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS + 1f);
            codex.noteLineSpoken(barks.consumeJustDeliveredBarkId());
            barks.dismissActiveBark();
            advance(barks, 1f);
        }
        assertFalse(codex.isOpen(), "a skimmer never opened it");

        // And conversely: the archive only ever fills from lines that were actually said, so a
        // skimmer's codex is not empty either — it is a record of what they were already told.
        assertTrue(codex.getUnlockedCount(CodexCategory.MEMORIES) > 0,
                "the beats the skimmer DID hear still archived themselves");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void advance(BarkSystem barks, float seconds) {
        int frames = Math.max(1, Math.round(seconds / FRAME_SECONDS));
        for (int frame = 0; frame < frames; frame++) barks.update(FRAME_SECONDS);
    }

    private static void advance(ExchangeSystem exchanges, float seconds) {
        int frames = Math.max(1, Math.round(seconds / FRAME_SECONDS));
        for (int frame = 0; frame < frames; frame++) exchanges.update(FRAME_SECONDS);
    }

    /**
     * Plays frames, dismissing each bark as a skimmer would, until {@code wantedId} appears or the
     * time budget runs out.  Returns the id that was found, or null.
     */
    private static String drainUntilSeen(BarkSystem barks, String wantedId, float budgetSeconds) {
        int frames = Math.round(budgetSeconds / FRAME_SECONDS);
        for (int frame = 0; frame < frames; frame++) {
            barks.update(FRAME_SECONDS);
            if (!barks.hasActiveBark()) continue;
            if (wantedId.equals(barks.getActiveBarkId())) return wantedId;
            barks.dismissActiveBark();   // the skimmer closes everything on sight
        }
        return null;
    }
}
