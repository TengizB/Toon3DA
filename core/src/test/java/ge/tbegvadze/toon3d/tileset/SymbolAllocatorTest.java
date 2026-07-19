package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.GameMath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Order-4 tests: the deterministic {@link SymbolAllocator}. Covers the determinism guarantee, fixed
 * integrity, room reservation + the freed-symbol mechanic, containment (R2), anti-repetition (R3),
 * coherence (R1, statistical), and clash capacity. Headless — no LibGDX render calls.
 *
 * <p>Tests that only need real art (determinism, fixed, reservation, containment, capacity) run against
 * the production registry + {@link SymbolBudget#standard()}. Coherence and anti-repetition need a pool
 * LARGER than the slot count and sprites carrying coherence tags, so they build a purpose-made themed
 * registry (a fresh {@link EnvironmentSpriteRegistry}) while still using the standard budget's symbols.
 */
class SymbolAllocatorTest {

    // Coherence-tag vocabulary for the themed registry. WALL sprites carry exactly one of these; every
    // other category's sprites stay untagged so the level theme is drawn only from these three.
    private static final String TAG_CLINICAL = "clinical";
    private static final String TAG_INDUSTRIAL = "industrial";
    private static final String TAG_DERELICT = "derelict";
    // 20 walls per theme (60 accent walls total). Each theme group is >= the 20 accent slots, so a
    // coherent level can fill the MAJORITY of its accent walls from a single theme — what R1 promises.
    private static final int WALLS_PER_THEME = 20;

    // ── Determinism ──────────────────────────────────────────────────────────────────────────────

    @Test
    void sameRequestYieldsIdenticalPalette() {
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        long seed = 0xA11CE5EEDL;

        SymbolAllocationRequest request = SymbolAllocationRequest.builder(seed, budget, registry)
                .depth(4)
                .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('Z', "wall_cryo")))
                .build();

        LevelPalette first = SymbolAllocator.allocate(request);
        LevelPalette second = SymbolAllocator.allocate(request);
        assertPalettesEqual(first, second);
    }

    @Test
    void twoRequestsBuiltIdenticallyProduceEqualPalettes() {
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        long seed = 987654321L;

        LevelPalette a = SymbolAllocator.allocate(
                SymbolAllocationRequest.builder(seed, budget, registry).depth(7).build());
        LevelPalette b = SymbolAllocator.allocate(
                SymbolAllocationRequest.builder(seed, budget, registry).depth(7).build());
        assertPalettesEqual(a, b);
    }

    // ── Fixed integrity ──────────────────────────────────────────────────────────────────────────

    @Test
    void fixedSymbolsAreNeverRolled() {
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        Set<String> baseWalls = new HashSet<>(budget.baseWallSpriteIds());
        // '@' -> prop_vendor is a true fixed sprite for EVERY seed. The bulk wall symbol 'x' is a BASE
        // WALL that varies per level (Step 1b), so it must always resolve to one of the registered base
        // walls in the WALL category — never an accent sprite — but not necessarily the same one.
        for (long seed = 0; seed < 200; seed++) {
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());
            assertEquals("prop_vendor", palette.spriteIdOf('@'), "@ rolled at seed " + seed);
            assertEquals(TileCategory.SOLID_PROP, palette.categoryOf('@'));
            assertTrue(baseWalls.contains(palette.spriteIdOf('x')),
                    "x resolved to a non-base wall at seed " + seed + ": " + palette.spriteIdOf('x'));
            assertEquals(TileCategory.WALL, palette.categoryOf('x'));
        }
    }

    @Test
    void baseWallVariesAcrossSeedsButIsDeterministic() {
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        // Deterministic: the same seed always yields the same base wall.
        assertEquals(
                SymbolAllocator.allocate(SymbolAllocationRequest.builder(7L, budget, registry).build())
                        .spriteIdOf('x'),
                SymbolAllocator.allocate(SymbolAllocationRequest.builder(7L, budget, registry).build())
                        .spriteIdOf('x'));
        // Varied: across many seeds the base wall is not always the default — the alternates DO get used.
        Set<String> observed = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            observed.add(SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build()).spriteIdOf('x'));
        }
        assertTrue(observed.size() > 1,
                "base wall never varied across 200 seeds; only saw " + observed);
        assertTrue(new HashSet<>(budget.baseWallSpriteIds()).containsAll(observed),
                "an unexpected base wall was chosen: " + observed);
    }

    @Test
    void minimalRegistryWithoutAlternatesKeepsDefaultBaseWall() {
        // A budget whose alternate base walls are NOT registered (e.g. the themed test registry) must
        // safely fall back to the single available base wall — the allocator filters candidates to the
        // request's registry, so no registry.get() on a missing id and no crash.
        EnvironmentSpriteRegistry registry = themedRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        for (long seed = 0; seed < 50; seed++) {
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());
            assertEquals("wall_plain", palette.spriteIdOf('x'),
                    "base wall should stay the only registered candidate at seed " + seed);
        }
    }

    // ── Reservation + freed symbols ───────────────────────────────────────────────────────────────

    @Test
    void placedRoomReservesItsRequiredSprite() {
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        SymbolAllocationRequest request = SymbolAllocationRequest.builder(42L, budget, registry)
                .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('Z', "wall_cryo")))
                .build();
        LevelPalette palette = SymbolAllocator.allocate(request);
        assertEquals("wall_cryo", palette.spriteIdOf('Z'));
        assertEquals(TileCategory.WALL, palette.categoryOf('Z'));
    }

    @Test
    void absentRoomFreesSymbolForVariety() {
        // The core freed-symbol test: with a room reserving 'Z' it renders that room's sprite; WITHOUT
        // the room, 'Z' is free and gets a generic variety WALL sprite instead. Reservation must also be
        // able to override what variety would otherwise pick — proven by demanding a DIFFERENT sprite.
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        long seed = 12345L;

        LevelPalette free = SymbolAllocator.allocate(
                SymbolAllocationRequest.builder(seed, budget, registry).build());
        String freeSprite = free.spriteIdOf('Z');
        assertNotNull(freeSprite, "freed 'Z' should still get a variety sprite");
        assertEquals(TileCategory.WALL, free.categoryOf('Z'));
        assertEquals(TileCategory.WALL, registry.get(freeSprite).category());

        LevelPalette reserved = SymbolAllocator.allocate(
                SymbolAllocationRequest.builder(seed, budget, registry)
                        .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('Z', "wall_medical")))
                        .build());
        assertEquals("wall_medical", reserved.spriteIdOf('Z'));

        // A reserved sprite is consumed: it must not ALSO appear as a variety fill on another WALL symbol.
        int medicalCount = 0;
        for (char wallSymbol : budget.flexibleSlots().get(TileCategory.WALL)) {
            if ("wall_medical".equals(reserved.spriteIdOf(wallSymbol))) {
                medicalCount++;
            }
        }
        assertEquals(1, medicalCount, "reserved wall_medical leaked to a second symbol");
    }

    // ── Containment (R2) ─────────────────────────────────────────────────────────────────────────

    @Test
    void observatoryArtNeverAppearsInAGenericSlot() {
        // With no observatory room placed, no generic WALL/SOLID_PROP/FLOOR_DECAL slot may bind an
        // observatory-tagged sprite. Checked across many seeds against the production catalog.
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        for (long seed = 0; seed < 300; seed++) {
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());
            for (char symbol = 32; symbol <= 126; symbol++) {
                String spriteId = palette.spriteIdOf(symbol);
                if (spriteId == null) {
                    continue;
                }
                assertFalse(
                        registry.get(spriteId).hasThemeTag(TilesetRegistries.THEME_TAG_OBSERVATORY),
                        "observatory sprite " + spriteId + " leaked to '" + symbol + "' at seed " + seed);
            }
        }
    }

    // ── Anti-repetition (R3) ─────────────────────────────────────────────────────────────────────

    @Test
    void accentWallsAreDistinctWhenPoolExceedsSlots() {
        EnvironmentSpriteRegistry registry = themedRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        List<Character> wallSymbols = budget.flexibleSlots().get(TileCategory.WALL);
        // themedRegistry has 61 eligible walls for 20 accent slots, so every accent wall must be distinct.
        assertTrue(registry.allInCategory(TileCategory.WALL).size() > wallSymbols.size(),
                "test precondition: WALL pool must exceed slot count");

        for (long seed = 0; seed < 50; seed++) {
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());
            Set<String> seen = new HashSet<>();
            for (char wallSymbol : wallSymbols) {
                String spriteId = palette.spriteIdOf(wallSymbol);
                assertNotNull(spriteId);
                assertTrue(seen.add(spriteId),
                        "duplicate accent wall " + spriteId + " at seed " + seed);
            }
        }
    }

    // ── Coherence (R1) ───────────────────────────────────────────────────────────────────────────

    @Test
    void majorityOfVarietyFillsShareTheLevelTheme() {
        EnvironmentSpriteRegistry registry = themedRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        List<Character> wallSymbols = budget.flexibleSlots().get(TileCategory.WALL);
        List<String> candidateThemeTags = sortedThemeTags(registry, budget);

        double totalThemeShare = 0;
        int seedCount = 400;
        for (long seed = 0; seed < seedCount; seed++) {
            String levelTheme = expectedLevelTheme(seed, candidateThemeTags);
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());

            int matches = 0;
            for (char wallSymbol : wallSymbols) {
                String spriteId = palette.spriteIdOf(wallSymbol);
                if (registry.get(spriteId).hasThemeTag(levelTheme)) {
                    matches++;
                }
            }
            totalThemeShare += matches / (double) wallSymbols.size();
        }
        double averageThemeShare = totalThemeShare / seedCount;
        // Base rate is 20/60 ≈ 0.33 (one theme's walls as a fraction of the candidate pool). Coherence
        // (×4) must pull the realised share into a clear majority; 0.50 is a safe margin below the ~0.6+
        // coherence produces and comfortably above the 0.33 base, so it proves the rule bites.
        assertTrue(averageThemeShare > 0.50,
                "coherence too weak: average theme share was " + averageThemeShare);
    }

    // ── Capacity / clash resolution ──────────────────────────────────────────────────────────────

    @Test
    void everySignatureRoomPlacedAtOnceAllocatesWithoutClash() {
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        // Many rooms placed together, each reserving a distinct real WALL sprite on a distinct symbol.
        SymbolAllocationRequest.Builder builder = SymbolAllocationRequest.builder(7L, budget, registry);
        String[] wallSprites = {"wall_cryo", "wall_medical", "wall_radiation", "wall_bio", "wall_hazard",
                "wall_rust", "wall_gore", "wall_bulkhead", "wall_glass"};
        char[] symbols = {'Z', 'M', 'U', 'S', 'h', 'j', 'G', 'k', 'N'};
        for (int index = 0; index < wallSprites.length; index++) {
            builder.placedRoom(RoomSymbolDemand.of(
                    Collections.singletonMap(symbols[index], wallSprites[index])));
        }
        LevelPalette palette = SymbolAllocator.allocate(builder.build());
        for (int index = 0; index < wallSprites.length; index++) {
            assertEquals(wallSprites[index], palette.spriteIdOf(symbols[index]),
                    "room reservation lost for '" + symbols[index] + "'");
        }
    }

    @Test
    void clashOnSameSymbolReroutesToDistinctFreeSymbol() {
        // Two placed rooms demand the SAME symbol 'Z' for DIFFERENT sprites. The first keeps 'Z'; the
        // second's sprite must be rehomed onto a distinct free WALL symbol — both must survive, no throw.
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        SymbolAllocationRequest request = SymbolAllocationRequest.builder(99L, budget, registry)
                .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('Z', "wall_cryo")))
                .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('Z', "wall_medical")))
                .build();
        LevelPalette palette = SymbolAllocator.allocate(request);

        assertEquals("wall_cryo", palette.spriteIdOf('Z'), "first room should keep the clashed symbol");
        boolean medicalRehomed = false;
        for (char wallSymbol : budget.flexibleSlots().get(TileCategory.WALL)) {
            if ("wall_medical".equals(palette.spriteIdOf(wallSymbol))) {
                medicalRehomed = true;
                break;
            }
        }
        assertTrue(medicalRehomed, "clashed second sprite wall_medical was dropped, not rehomed");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    // The shared production catalog (bootstrapped once). Every category has a non-empty pool.
    private static EnvironmentSpriteRegistry productionRegistry() {
        TilesetRegistries.bootstrap();
        return TilesetRegistries.sprites();
    }

    // A fresh catalog whose WALL pool (61 eligible) far exceeds the 20 accent slots, with 60 accent walls
    // split evenly across three coherence themes plus an untagged plain wall and one observatory wall.
    // Other categories carry the minimum the standard budget needs (a column, the vendor + generic props,
    // and generic decals), all untagged so the level theme is drawn only from the WALL themes.
    private static EnvironmentSpriteRegistry themedRegistry() {
        EnvironmentSpriteRegistry registry = new EnvironmentSpriteRegistry();

        registry.register(EnvironmentSpriteDefinition.builder("wall_plain", TileCategory.WALL, "Plain").build());
        registerThemedWalls(registry, TAG_CLINICAL);
        registerThemedWalls(registry, TAG_INDUSTRIAL);
        registerThemedWalls(registry, TAG_DERELICT);
        // One observatory-tagged wall to confirm coherence/anti-repetition never pull contained art.
        registry.register(EnvironmentSpriteDefinition.builder("wall_obs", TileCategory.WALL, "Obs")
                .themeTag(TilesetRegistries.THEME_TAG_OBSERVATORY).build());

        registry.register(EnvironmentSpriteDefinition.builder("column_round", TileCategory.COLUMN, "Column").build());

        registry.register(EnvironmentSpriteDefinition.builder("prop_vendor", TileCategory.SOLID_PROP, "Vendor").build());
        for (int index = 0; index < 4; index++) {
            registry.register(EnvironmentSpriteDefinition
                    .builder("prop_generic_" + index, TileCategory.SOLID_PROP, "Prop " + index).build());
        }
        for (int index = 0; index < 4; index++) {
            registry.register(EnvironmentSpriteDefinition
                    .builder("decal_generic_" + index, TileCategory.FLOOR_DECAL, "Decal " + index)
                    .occluder(false).build());
        }
        return registry;
    }

    private static void registerThemedWalls(EnvironmentSpriteRegistry registry, String themeTag) {
        for (int index = 0; index < WALLS_PER_THEME; index++) {
            registry.register(EnvironmentSpriteDefinition
                    .builder("wall_" + themeTag + "_" + index, TileCategory.WALL, themeTag + " " + index)
                    .themeTag(themeTag).build());
        }
    }

    // Mirrors SymbolAllocator.chooseLevelTheme's candidate-tag gathering: every non-containment theme tag
    // present across the flexible-category pools, sorted for reproducible indexing.
    private static List<String> sortedThemeTags(EnvironmentSpriteRegistry registry, SymbolBudget budget) {
        TreeSet<String> tags = new TreeSet<>();
        for (TileCategory category : budget.flexibleSlots().keySet()) {
            for (EnvironmentSpriteDefinition definition : registry.allInCategory(category)) {
                for (String tag : definition.themeTags()) {
                    if (!TilesetRegistries.THEME_TAG_OBSERVATORY.equals(tag)) {
                        tags.add(tag);
                    }
                }
            }
        }
        return new ArrayList<>(tags);
    }

    // Mirrors SymbolAllocator's theme roll exactly (same NUL key, same PaletteRng) so the test knows which
    // theme a seed selected without the allocator exposing it.
    private static String expectedLevelTheme(long seed, List<String> sortedThemeTags) {
        PaletteRng rng = new PaletteRng(GameMath.paletteSymbolSeed(seed, '\u0000'));
        int index = rng.nextIntInclusive(0, sortedThemeTags.size() - 1);
        return sortedThemeTags.get(index);
    }

    // Deep palette equality over every printable symbol (LevelPalette has no equals()).
    private static void assertPalettesEqual(LevelPalette expected, LevelPalette actual) {
        Map<Character, String> expectedBindings = new LinkedHashMap<>();
        for (char symbol = 32; symbol <= 126; symbol++) {
            assertEquals(expected.spriteIdOf(symbol), actual.spriteIdOf(symbol),
                    "sprite differs for '" + symbol + "'");
            assertEquals(expected.categoryOf(symbol), actual.categoryOf(symbol),
                    "category differs for '" + symbol + "'");
            if (expected.spriteIdOf(symbol) != null) {
                expectedBindings.put(symbol, expected.spriteIdOf(symbol));
            }
        }
        assertFalse(expectedBindings.isEmpty(), "palette bound nothing — test is vacuous");
    }
}
