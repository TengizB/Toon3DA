package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry-integrity tests for the tileset art catalog (order-1): {@code get()} never returns null
 * for a registered id, per-category counts match today's {@code Level} predicates (a cheap guard that
 * the inventory is complete and nothing was dropped), duplicate ids throw, and {@code bootstrap()} is
 * idempotent. Headless — no LibGDX render calls.
 */
class TilesetRegistriesTest {

    // The printable ASCII range that a level char can occupy. Iterating it and counting Level's
    // predicate hits gives the ground-truth category counts to check the registry against, without
    // duplicating the char lists here.
    private static final char FIRST_PRINTABLE = 32;
    private static final char LAST_PRINTABLE  = 126;

    private EnvironmentSpriteRegistry freshRegistry() {
        EnvironmentSpriteRegistry registry = new EnvironmentSpriteRegistry();
        TilesetRegistries.registerAll(registry);
        return registry;
    }

    private int countLevelChars(java.util.function.IntPredicate predicate) {
        int count = 0;
        for (char candidate = FIRST_PRINTABLE; candidate <= LAST_PRINTABLE; candidate++) {
            if (predicate.test(candidate)) {
                count++;
            }
        }
        return count;
    }

    @Test
    void everyRegisteredSpriteResolvesAndGetNeverReturnsNull() {
        EnvironmentSpriteRegistry registry = freshRegistry();
        for (TileCategory category : TileCategory.values()) {
            for (EnvironmentSpriteDefinition definition : registry.allInCategory(category)) {
                assertTrue(registry.contains(definition.id()), "contains() false for " + definition.id());
                assertNotNull(registry.get(definition.id()), "null definition for " + definition.id());
                assertEquals(definition.id(), registry.get(definition.id()).id());
                assertEquals(category, definition.category());
            }
        }
    }

    @Test
    void wallCountMatchesLevelIsWall() {
        EnvironmentSpriteRegistry registry = freshRegistry();
        assertEquals(countLevelChars(candidate -> Level.isWall((char) candidate)),
                registry.allInCategory(TileCategory.WALL).size());
    }

    @Test
    void columnCategoryHoldsRoundAndSquareColumnVariety() {
        // order-8 REQUIREMENT PROOF 2: the COLUMN category is the first flexible-sprite slot to carry
        // MORE art than it has symbols — the single symbol 'P' (Level.isColumn) resolves per level to
        // "column_round" OR "column_square". So the 1:1 symbol==sprite count invariant no longer holds
        // for COLUMN by design; it must hold that the pool is at least the symbol count and contains
        // both variety sprites.
        EnvironmentSpriteRegistry registry = freshRegistry();
        int columnSymbolCount = countLevelChars(candidate -> Level.isColumn((char) candidate));
        assertTrue(registry.allInCategory(TileCategory.COLUMN).size() >= columnSymbolCount);
        assertTrue(registry.contains("column_round"));
        assertTrue(registry.contains("column_square"));
    }

    @Test
    void solidPropCountMatchesLevelIsPropSolid() {
        EnvironmentSpriteRegistry registry = freshRegistry();
        assertEquals(countLevelChars(candidate -> Level.isPropSolid((char) candidate)),
                registry.allInCategory(TileCategory.SOLID_PROP).size());
    }

    @Test
    void floorDecalCountMatchesPureDecalsInLevelIsPropDecal() {
        EnvironmentSpriteRegistry registry = freshRegistry();
        // FLOOR_DECAL covers the "pure" decals: isPropDecal minus the FIXED pickup / hazard / stairs
        // symbols, which keep their dedicated handling and are NOT in the sprite registry.
        int pureDecalCount = countLevelChars(candidate -> {
            char cell = (char) candidate;
            return Level.isPropDecal(cell)
                    && !Level.isKeycardPickup(cell)
                    && !Level.isMedicalPickup(cell)
                    && !Level.isArmourPickup(cell)
                    && !Level.isAmmoPickup(cell)
                    && !Level.isHazardDecal(cell)
                    && !Level.isStairsDown(cell);
        });
        assertEquals(pureDecalCount, registry.allInCategory(TileCategory.FLOOR_DECAL).size());
    }

    @Test
    void heightAndOccluderMetadataMatchesLegacyValues() {
        EnvironmentSpriteRegistry registry = freshRegistry();
        // Walls are full-height stripes that occlude.
        EnvironmentSpriteDefinition plainWall = registry.get("wall_plain");
        assertEquals(1f, plainWall.heightMultiplier(), 0f);
        assertTrue(plainWall.occluder());
        // A solid prop keeps its legacy height and occludes.
        assertEquals(0.90f, registry.get("prop_locker").heightMultiplier(), 0f);
        assertTrue(registry.get("prop_locker").occluder());
        // A flat floor stain keeps its legacy height and does NOT occlude.
        assertEquals(0.18f, registry.get("decal_blood").heightMultiplier(), 0f);
        assertFalse(registry.get("decal_blood").occluder());
    }

    @Test
    void observatoryArtIsTaggedForContainment() {
        EnvironmentSpriteRegistry registry = freshRegistry();
        assertTrue(registry.get("wall_stellar_viewport").hasThemeTag(TilesetRegistries.THEME_TAG_OBSERVATORY));
        assertTrue(registry.get("prop_gravity_well_core").hasThemeTag(TilesetRegistries.THEME_TAG_OBSERVATORY));
        assertTrue(registry.get("decal_starlight_seam").hasThemeTag(TilesetRegistries.THEME_TAG_OBSERVATORY));
        // Non-observatory art carries no theme tag.
        assertTrue(registry.get("wall_plain").themeTags().isEmpty());
    }

    @Test
    void textureGeneratorIdDefaultsToSpriteId() {
        EnvironmentSpriteRegistry registry = freshRegistry();
        for (TileCategory category : TileCategory.values()) {
            for (EnvironmentSpriteDefinition definition : registry.allInCategory(category)) {
                assertEquals(definition.id(), definition.textureGeneratorId());
            }
        }
    }

    @Test
    void registeringSameIdTwiceThrows() {
        EnvironmentSpriteRegistry registry = freshRegistry();
        assertThrows(IllegalStateException.class, () ->
                registry.register(EnvironmentSpriteDefinition.builder("wall_plain", TileCategory.WALL, "dup").build()));
    }

    @Test
    void gettingUnregisteredIdThrows() {
        EnvironmentSpriteRegistry empty = new EnvironmentSpriteRegistry();
        assertThrows(IllegalArgumentException.class, () -> empty.get("wall_plain"));
    }

    @Test
    void allInCategoryForEmptyRegistryIsEmptyNotNull() {
        EnvironmentSpriteRegistry empty = new EnvironmentSpriteRegistry();
        List<EnvironmentSpriteDefinition> walls = empty.allInCategory(TileCategory.WALL);
        assertNotNull(walls);
        assertTrue(walls.isEmpty());
    }

    @Test
    void bootstrapIsIdempotentAndPopulatesSharedRegistry() {
        TilesetRegistries.bootstrap();
        TilesetRegistries.bootstrap(); // second call must be a no-op, not a double-registration throw
        assertTrue(TilesetRegistries.isBootstrapped());
        assertNotNull(TilesetRegistries.sprites().get("wall_plain"));
        // The shared singleton holds the same complete inventory as a freshly-populated registry.
        assertEquals(freshRegistry().size(), TilesetRegistries.sprites().size());
    }
}
