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
    void everyWallSymbolAndTheColumnResolveInEveryRegionWallSet() {
        // generated-wall-art order 2: walls and the column left the sprite catalog. Every Level.isWall char is
        // a slot of the WALL_SLOT_TABLE and resolves (with the R3 fallback) to a sprite id of every registered
        // region set — an alias through the set it borrows; 'P' is the COLUMN and every set ships columns.
        EnvironmentSpriteRegistry registry = freshRegistry();
        assertTrue(registry.allInCategory(TileCategory.WALL).isEmpty(), "walls are not catalog sprites any more");
        assertTrue(registry.allInCategory(TileCategory.COLUMN).isEmpty(), "columns are not catalog sprites any more");
        int wallSymbolCount = countLevelChars(candidate -> Level.isWall((char) candidate));
        assertEquals(WallSlotTable.symbols().length(), wallSymbolCount);
        RegionWallSetRegistry sets = RegionWallSets.registry();
        for (String setId : sets.ids()) {
            RegionWallSet set = sets.resolve(setId);
            for (char symbol : WallSlotTable.symbols().toCharArray()) {
                assertTrue(Level.isWall(symbol), symbol + " must stay a wall");
                List<String> ids = set.resolve(WallSlotTable.groupOf(symbol), WallSlotTable.slotOf(symbol));
                assertFalse(ids.isEmpty(), setId + " leaves " + symbol + " without a picture");
                assertTrue(set.allWallIds().containsAll(ids), setId + " resolves " + symbol + " outside its set");
            }
            assertTrue(Level.isColumn(ge.tbegvadze.toon3d.util.TilesetConstants.WALL_SET_COLUMN_SYMBOL));
            assertEquals(2, set.columnIds().size());
            assertEquals(4, set.doorIds().size());
        }
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
        // A crate keeps its legacy height and occludes (walls are region-set faces now, not catalog sprites).
        assertEquals(ge.tbegvadze.toon3d.util.RenderConstants.PROP_HEIGHT_CRATE,
                registry.get("prop_crate").heightMultiplier(), 0f);
        assertTrue(registry.get("prop_crate").occluder());
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
        assertTrue(registry.get("prop_gravity_well_core").hasThemeTag(TilesetRegistries.THEME_TAG_OBSERVATORY));
        assertTrue(registry.get("decal_starlight_seam").hasThemeTag(TilesetRegistries.THEME_TAG_OBSERVATORY));
        // Non-observatory art carries no theme tag.
        assertTrue(registry.get("prop_locker").themeTags().isEmpty());
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
                registry.register(EnvironmentSpriteDefinition.builder("prop_locker", TileCategory.SOLID_PROP, "dup").build()));
    }

    @Test
    void gettingUnregisteredIdThrows() {
        EnvironmentSpriteRegistry empty = new EnvironmentSpriteRegistry();
        assertThrows(IllegalArgumentException.class, () -> empty.get("prop_locker"));
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
        assertNotNull(TilesetRegistries.sprites().get("prop_locker"));
        // The shared singleton holds the same complete inventory as a freshly-populated registry.
        assertEquals(freshRegistry().size(), TilesetRegistries.sprites().size());
    }
}
