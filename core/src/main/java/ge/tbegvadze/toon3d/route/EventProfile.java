package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.Collections;

/**
 * The EVENT node's profile (route-map order-10): a small curated NON-COMBAT beat — a distress beacon,
 * an abandoned stash, a surviving tech, a corrupted shrine, a data terminal — that gives the descent
 * story and a decision without a fight. It is the map's "? room": routing here trades a combat reward
 * for a choice.
 *
 * <p>Recipe:
 * <ul>
 *   <li>Generator: the bespoke {@link GeneratorId#EVENT_ROOM} chamber — a tight room around one
 *       central interactable, so the "story beat, not a fight" read is instant.</li>
 *   <li>Budget: {@link RouteMapConstants#EVENT_BUDGET_SCALE} — near-zero; still depth-scaled first
 *       (order-3 invariant), so a deep EVENT is not depth-frozen, merely quiet.</li>
 *   <li>Event: one {@link FacilityEvent} rolled deterministically from the node seed and attached to
 *       the plan by id ({@link LevelPlan#withEventId}). {@code World} looks it up in the registry,
 *       arms it on the room's event station, and opens the choice overlay when the player arrives.</li>
 *   <li>Effects: a first-frame sting ({@link RouteMapConstants#EVENT_STING_TEXT}) and a telemetry
 *       token (the event id) so the post-run route story records which beat was met.</li>
 * </ul>
 *
 * <p>Boss depths still defer to the arena ({@link GameMath#isBossFloor(int)} is the single authority),
 * matching every other profile. If the event catalog is somehow empty, the plan carries no event id and
 * {@code World} simply shows a quiet room — graceful degradation, never a crash. Pure / headless.
 */
public final class EventProfile implements NodeLevelProfile {

    /** The stable id EVENT nodes reference (matches the node definition's {@code levelProfileId}). */
    public static final String PROFILE_ID = "event";

    private final FacilityEventRegistry events;

    public EventProfile(FacilityEventRegistry events) {
        this.events = events;
    }

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public LevelPlan resolve(RouteNode node, int depth, long seed) {
        if (GameMath.isBossFloor(depth)) {
            // An EVENT is never forced onto a boss depth, but stay consistent: the arena always wins.
            return new LevelPlan(GeneratorId.BOSS_ARENA, null, Collections.emptyList());
        }

        long nodeSeed = node != null ? node.nodeSeed : seed;
        FacilityEvent event = events != null ? events.rollFor(nodeSeed) : null;

        FloorEffects effects = FloorEffects.NONE.withSting(RouteMapConstants.EVENT_STING_TEXT);
        if (event != null) {
            effects = effects.withStatLog(event.id());
        }

        LevelPlan plan = new LevelPlan(GeneratorId.EVENT_ROOM, null, Collections.emptyList(),
                EnemyBudgetOverride.scaled(RouteMapConstants.EVENT_BUDGET_SCALE), null, effects);
        return event != null ? plan.withEventId(event.id()) : plan;
    }
}
