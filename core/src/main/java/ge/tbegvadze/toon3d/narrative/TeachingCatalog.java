package ge.tbegvadze.toon3d.narrative;

/**
 * THE ENTIRE TUTORIAL (narrative-rework order-4) — one first-teach row per {@link TeachingTopic},
 * plus one RE-TEACH row for every topic that carries evidence
 * ({@link TeachingTopic#hasReteach()}). Registered from {@link BarkCatalog#bootstrap} exactly like
 * the order-5 control hints it replaces — the six original topics keep their original row ids
 * ({@code bark.control.<key>}), so no persisted seen-flag or telemetry id moves.
 *
 * <p>Both rows share the same {@link BarkTrigger#CONTROL_HINT} trigger, but never the same subject
 * key: a first-teach row answers {@link TeachingTopic#getSubjectKey()}, asked for on every frame the
 * topic might be newly useful ({@code World.requestControlHintBarks} and friends); a re-teach row
 * answers {@link TeachingTopic#getRetaughtSubjectKey()}, asked for ONLY by {@link TeachingSystem}
 * once its competence model has evidence and its rate limits clear. Two different subjects on one
 * row id would let the ordinary per-frame ask accidentally spend the re-teach for free — this is
 * the whole reason the subject keys differ.
 *
 * <p>Every row here is {@code oneShot(true)} and {@link BarkPriority#STORY_CRITICAL}, matching every
 * row already registered under this trigger ({@code StoryBarkTest.theColdOpenAndTheTutorialAreOneShotAndNeverDropped}):
 * a dropped teaching line is a stuck player, full stop. What a {@link TeachingTopic}'s
 * {@link TeachingTier} actually changes is {@link TeachingSystem}'s OWN gate on the re-teach ask —
 * a {@link TeachingTier#TACTICAL} topic simply never asks during a combat spike, so its
 * STORY_CRITICAL row never has the chance to bypass one.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class TeachingCatalog {

    private TeachingCatalog() {}

    /** Registers every topic's first-teach and (where it has one) re-teach row into {@code registry}. */
    public static void bootstrap(BarkRegistry registry) {
        for (TeachingTopic topic : TeachingTopic.values()) {
            String taughtId = "bark.control." + topic.getCatalogKey();
            registry.register(row(taughtId, storyIdFor(taughtId))
                    .trigger(BarkTrigger.CONTROL_HINT)
                    .subjectKey(topic.getSubjectKey())
                    .priority(BarkPriority.STORY_CRITICAL)
                    .oneShot(true)
                    .build());

            if (!topic.hasReteach()) continue;
            String retaughtId = "bark.control.retaught." + topic.getCatalogKey();
            registry.register(row(retaughtId, storyIdFor(retaughtId))
                    .trigger(BarkTrigger.CONTROL_HINT)
                    .subjectKey(topic.getRetaughtSubjectKey())
                    .priority(BarkPriority.STORY_CRITICAL)
                    .oneShot(true)
                    .build());
        }
    }

    /** Shared row prologue: ORA, lore tone (the priority is always overridden above). */
    private static BarkDefinition.Builder row(String id, String textStringId) {
        return BarkDefinition.builder(id)
                .speaker(Speaker.AI)
                .textStringId(textStringId)
                .tone(BarkTone.LORE);
    }

    /** Row ids and string ids are kept in lockstep by construction, exactly like {@link BarkCatalog}. */
    private static String storyIdFor(String barkId) {
        return "story." + barkId;
    }
}
