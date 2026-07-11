package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.EventRoomGenerator;
import ge.tbegvadze.toon3d.level.GateAirlockGenerator;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.RouteMapConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Order-10 profile + generator tests: the EVENT and REGION_GATE profiles resolve to their bespoke
 * calm floors, thread their content through the existing {@link LevelPlan} seams, and defer to the
 * boss arena on boss depths; their generators emit legible, enemy-free rooms with the expected anchors.
 */
class EventGateProfileTest {

    private FacilityEventRegistry freshEvents() {
        FacilityEventRegistry registry = new FacilityEventRegistry();
        RouteRegistries.registerEvents(registry);
        return registry;
    }

    private RouteNode eventNode(int depth, long nodeSeed) {
        RouteNode node = new RouteNode(RouteNodeType.EVENT, depth, 0);
        node.nodeSeed = nodeSeed;
        return node;
    }

    // ---- EventProfile ------------------------------------------------------

    @Test
    void eventProfileBuildsACalmEventRoomWithAnEvent() {
        EventProfile profile = new EventProfile(freshEvents());
        LevelPlan plan = profile.resolve(eventNode(3, 42L), 3, 1234L);
        assertEquals(GeneratorId.EVENT_ROOM, plan.generatorId());
        assertNotNull(plan.eventId(), "an EVENT floor should name its rolled event");
        assertNotNull(plan.enemyBudget(), "an EVENT floor should carry a calm budget override");
        assertTrue(plan.floorEffects().stingText() != null, "an EVENT floor shows an entry sting");
        // The rolled event id logs to telemetry so the route story records the beat.
        assertEquals(plan.eventId(), plan.floorEffects().statLogToken());
    }

    @Test
    void eventProfileIsDeterministicFromNodeSeed() {
        EventProfile profile = new EventProfile(freshEvents());
        String first  = profile.resolve(eventNode(3, 99L), 3, 5L).eventId();
        String second = profile.resolve(eventNode(3, 99L), 3, 5L).eventId();
        assertEquals(first, second);
    }

    @Test
    void eventProfileDefersToBossArenaOnBossDepth() {
        EventProfile profile = new EventProfile(freshEvents());
        LevelPlan plan = profile.resolve(eventNode(5, 1L), 5, 1L); // depth 5 is a boss floor
        assertEquals(GeneratorId.BOSS_ARENA, plan.generatorId());
        assertNull(plan.eventId());
    }

    @Test
    void eventProfileDegradesGracefullyWithNoEventCatalog() {
        EventProfile profile = new EventProfile(new FacilityEventRegistry()); // empty catalog
        LevelPlan plan = profile.resolve(eventNode(3, 7L), 3, 7L);
        assertEquals(GeneratorId.EVENT_ROOM, plan.generatorId());
        assertNull(plan.eventId(), "no event should be armed when the catalog is empty");
    }

    // ---- GateProfile -------------------------------------------------------

    @Test
    void gateProfileBuildsACalmAirlockFloor() {
        GateProfile profile = new GateProfile();
        RouteNode gate = new RouteNode(RouteNodeType.REGION_GATE, 4, 0);
        LevelPlan plan = profile.resolve(gate, 4, 1L);
        assertEquals(GeneratorId.GATE_AIRLOCK, plan.generatorId());
        assertNotNull(plan.enemyBudget());
        assertEquals("gate", plan.floorEffects().statLogToken());
    }

    @Test
    void gateProfileDefersToBossArenaOnBossDepth() {
        GateProfile profile = new GateProfile();
        RouteNode gate = new RouteNode(RouteNodeType.REGION_GATE, 5, 0);
        LevelPlan plan = profile.resolve(gate, 5, 1L);
        assertEquals(GeneratorId.BOSS_ARENA, plan.generatorId());
    }

    // ---- Generators --------------------------------------------------------

    @Test
    void eventRoomGeneratorEmitsAnEnemyFreeRoomWithAStation() {
        Level level = new EventRoomGenerator(2024L).generate(3);
        assertTrue(level.getEnemySpawnPoints().isEmpty(), "an EVENT room hosts no fight");
        assertEquals(1, level.getEventStationTiles().size(), "exactly one interactable station");
        assertTrue(containsChar(level, 'p'), "player start present");
        assertTrue(containsChar(level, '>'), "exit present");
        assertTrue(containsChar(level, RouteMapConstants.EVENT_STATION_SYMBOL), "interactable present");
    }

    @Test
    void gateAirlockGeneratorEmitsACeremonialBulkhead() {
        Level level = new GateAirlockGenerator(2024L).generate(4);
        assertTrue(level.getEnemySpawnPoints().isEmpty(), "the gate is a pacing breath, not a fight");
        assertTrue(containsChar(level, 'p'), "player start present");
        assertTrue(containsChar(level, '>'), "exit present");
        assertTrue(containsChar(level, 'B'), "blue keycard door present");
        assertTrue(containsChar(level, 'b'), "blue keycard present so the bulkhead always opens");
    }

    private boolean containsChar(Level level, char target) {
        for (int row = 0; row < level.getHeight(); row++) {
            for (int column = 0; column < level.getWidth(); column++) {
                if (level.getCell(column, row) == target) return true;
            }
        }
        return false;
    }
}
