package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.BossArenaGenerator;
import ge.tbegvadze.toon3d.level.CavernGenerator;
import ge.tbegvadze.toon3d.level.ILevelGenerator;
import ge.tbegvadze.toon3d.level.LevelGenerator;
import ge.tbegvadze.toon3d.level.LinearCorridorGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry-integrity tests: get() never returns null for a registered type, the pool excludes
 * forced types, the standard generator pool is correct, and bootstrap is idempotent.
 */
class RouteRegistriesTest {

    private NodeTypeRegistry freshNodeTypes() {
        NodeTypeRegistry registry = new NodeTypeRegistry();
        RouteRegistries.registerNodeTypes(registry);
        return registry;
    }

    private GeneratorRegistry freshGenerators() {
        GeneratorRegistry registry = new GeneratorRegistry();
        RouteRegistries.registerGenerators(registry);
        return registry;
    }

    @Test
    void everyNodeTypeIsRegisteredAndGetNeverReturnsNull() {
        NodeTypeRegistry registry = freshNodeTypes();
        assertEquals(RouteNodeType.values().length, registry.size());
        for (RouteNodeType type : RouteNodeType.values()) {
            assertTrue(registry.isRegistered(type), "missing registration for " + type);
            assertNotNull(registry.get(type), "null definition for " + type);
            assertEquals(type, registry.get(type).type());
        }
    }

    @Test
    void poolExcludesForcedNodeTypes() {
        NodeTypeRegistry registry = freshNodeTypes();
        List<NodeTypeDefinition> pool = registry.allWeightedForPool();
        for (NodeTypeDefinition definition : pool) {
            assertFalse(definition.forced(), definition.type() + " is forced but appears in the pool");
        }
        // BOSS and REGION_GATE are the two forced types, so the pool is total minus two.
        assertEquals(RouteNodeType.values().length - 2, pool.size());
    }

    @Test
    void registeringSameTypeTwiceThrows() {
        NodeTypeRegistry registry = freshNodeTypes();
        assertThrows(IllegalStateException.class, () ->
                registry.register(NodeTypeDefinition.builder(RouteNodeType.COMBAT)
                        .id("combat").displayName("dup").hintLine("dup").baseWeight(1f)
                        .dangerTier(DangerTier.STANDARD).accentColorId("combat")
                        .forced(false).levelProfileId("combat_standard").iconPainterId("icon_combat")
                        .build()));
    }

    @Test
    void gettingUnregisteredTypeThrows() {
        NodeTypeRegistry empty = new NodeTypeRegistry();
        assertThrows(IllegalArgumentException.class, () -> empty.get(RouteNodeType.COMBAT));
    }

    @Test
    void standardPoolHoldsThreeGeneratorsAndExcludesBossArena() {
        GeneratorRegistry registry = freshGenerators();
        List<GeneratorId> pool = registry.standardPool();
        assertEquals(3, pool.size());
        assertTrue(pool.contains(GeneratorId.ROOMS_MST));
        assertTrue(pool.contains(GeneratorId.LINEAR_CORRIDOR));
        assertTrue(pool.contains(GeneratorId.CAVERN));
        assertFalse(pool.contains(GeneratorId.BOSS_ARENA), "boss arena must not be in the standard pool");
    }

    @Test
    void generatorFactoriesBuildTheExpectedGeneratorTypes() {
        GeneratorRegistry registry = freshGenerators();
        assertInstanceOf(LevelGenerator.class, registry.create(GeneratorId.ROOMS_MST, 1L, null));
        assertInstanceOf(LinearCorridorGenerator.class, registry.create(GeneratorId.LINEAR_CORRIDOR, 1L, null));
        assertInstanceOf(CavernGenerator.class, registry.create(GeneratorId.CAVERN, 1L, null));
        assertInstanceOf(BossArenaGenerator.class, registry.create(GeneratorId.BOSS_ARENA, 1L, null));
    }

    @Test
    void creatingUnregisteredGeneratorThrows() {
        GeneratorRegistry empty = new GeneratorRegistry();
        assertThrows(IllegalArgumentException.class, () -> empty.create(GeneratorId.ROOMS_MST, 1L, null));
    }

    @Test
    void bootstrapIsIdempotentAndPopulatesSharedRegistries() {
        RouteRegistries.bootstrap();
        RouteRegistries.bootstrap(); // second call must be a no-op, not a double-registration throw
        assertTrue(RouteRegistries.isBootstrapped());
        assertEquals(RouteNodeType.values().length, RouteRegistries.nodeTypes().size());
        ILevelGenerator generator = RouteRegistries.generators().create(GeneratorId.ROOMS_MST, 42L, null);
        assertNotNull(generator);
    }
}
