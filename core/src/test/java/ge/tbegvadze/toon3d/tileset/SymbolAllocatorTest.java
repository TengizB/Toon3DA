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
 *
 * <p>generated-wall-art order 2: the allocator no longer binds walls or the column (the LevelFinisher dresses
 * them from the floor's region wall set), so the rules are exercised on the SOLID_PROP slots, which carry the
 * same reservation, containment, anti-repetition and coherence behaviour.
 */
class SymbolAllocatorTest {

    // Coherence-tag vocabulary for the themed registry. SOLID_PROP sprites carry exactly one of these; every
    // other category's sprites stay untagged so the level theme is drawn only from these three.
    private static final String TAG_CLINICAL = "clinical";
    private static final String TAG_INDUSTRIAL = "industrial";
    private static final String TAG_DERELICT = "derelict";
    // 20 props per theme (60 themed props total). Each theme group is >= the 16 flexible prop slots, so a
    // coherent level can fill the MAJORITY of its props from a single theme — what R1 promises.
    private static final int PROPS_PER_THEME = 20;

    // ── Determinism ──────────────────────────────────────────────────────────────────────────────

    @Test
    void sameRequestYieldsIdenticalPalette() {
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        long seed = 0xA11CE5EEDL;

        SymbolAllocationRequest request = SymbolAllocationRequest.builder(seed, budget, registry)
                .depth(4)
                .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('L', "prop_locker")))
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
        // '@' -> prop_vendor is a true fixed sprite for EVERY seed.
        for (long seed = 0; seed < 200; seed++) {
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());
            assertEquals("prop_vendor", palette.spriteIdOf('@'), "@ rolled at seed " + seed);
            assertEquals(TileCategory.SOLID_PROP, palette.categoryOf('@'));
        }
    }

    @Test
    void allocatorNeverBindsAWallOrTheColumn() {
        // Walls and 'P' belong to the floor's region wall set (RegionPaletteBuilder), never to the allocator:
        // across many seeds no wall symbol and no column gets an allocator binding.
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        for (long seed = 0; seed < 200; seed++) {
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());
            for (char symbol : WallSlotTable.symbols().toCharArray()) {
                assertEquals(null, palette.spriteIdOf(symbol), "allocator bound wall '" + symbol + "'");
            }
            assertEquals(null, palette.spriteIdOf('P'), "allocator bound the column");
        }
    }

    @Test
    void registryWithoutAnyWallOrColumnSpriteAllocates() {
        // The catalog no longer holds walls or columns; a registry with none must allocate every flexible
        // prop and decal slot without touching a missing id.
        EnvironmentSpriteRegistry registry = themedRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        assertTrue(registry.allInCategory(TileCategory.WALL).isEmpty());
        assertTrue(registry.allInCategory(TileCategory.COLUMN).isEmpty());
        for (long seed = 0; seed < 50; seed++) {
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());
            for (char propSymbol : budget.flexibleSlots().get(TileCategory.SOLID_PROP)) {
                assertNotNull(palette.spriteIdOf(propSymbol), "prop '" + propSymbol + "' unbound at seed " + seed);
            }
        }
    }

    // ── Reservation + freed symbols ───────────────────────────────────────────────────────────────

    @Test
    void placedRoomReservesItsRequiredSprite() {
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        SymbolAllocationRequest request = SymbolAllocationRequest.builder(42L, budget, registry)
                .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('T', "prop_terminal")))
                .build();
        LevelPalette palette = SymbolAllocator.allocate(request);
        assertEquals("prop_terminal", palette.spriteIdOf('T'));
        assertEquals(TileCategory.SOLID_PROP, palette.categoryOf('T'));
    }

    @Test
    void absentRoomFreesSymbolForVariety() {
        // The core freed-symbol test: with a room reserving 'T' it renders that room's sprite; WITHOUT
        // the room, 'T' is free and gets a generic variety SOLID_PROP sprite instead. Reservation must also be
        // able to override what variety would otherwise pick — proven by demanding a DIFFERENT sprite. The
        // themed registry's prop pool exceeds the slots, so a consumed sprite can never be repeated.
        EnvironmentSpriteRegistry registry = themedRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        long seed = 12345L;

        LevelPalette free = SymbolAllocator.allocate(
                SymbolAllocationRequest.builder(seed, budget, registry).build());
        String freeSprite = free.spriteIdOf('T');
        assertNotNull(freeSprite, "freed 'T' should still get a variety sprite");
        assertEquals(TileCategory.SOLID_PROP, free.categoryOf('T'));
        assertEquals(TileCategory.SOLID_PROP, registry.get(freeSprite).category());

        LevelPalette reserved = SymbolAllocator.allocate(
                SymbolAllocationRequest.builder(seed, budget, registry)
                        .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('T', "prop_generic_1")))
                        .build());
        assertEquals("prop_generic_1", reserved.spriteIdOf('T'));

        // A reserved sprite is consumed: it must not ALSO appear as a variety fill on another prop symbol.
        int lockerCount = 0;
        for (char propSymbol : budget.flexibleSlots().get(TileCategory.SOLID_PROP)) {
            if ("prop_generic_1".equals(reserved.spriteIdOf(propSymbol))) {
                lockerCount++;
            }
        }
        assertEquals(1, lockerCount, "reserved prop_generic_1 leaked to a second symbol");
    }

    // ── Containment (R2) ─────────────────────────────────────────────────────────────────────────

    @Test
    void observatoryArtNeverAppearsInAGenericSlot() {
        // With no observatory room placed, no generic SOLID_PROP/FLOOR_DECAL slot may bind an
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
    void propsAreDistinctWhenPoolExceedsSlots() {
        EnvironmentSpriteRegistry registry = themedRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        List<Character> propSymbols = budget.flexibleSlots().get(TileCategory.SOLID_PROP);
        // themedRegistry has 60+ eligible props for 16 flexible slots, so every prop must be distinct.
        assertTrue(registry.allInCategory(TileCategory.SOLID_PROP).size() > propSymbols.size(),
                "test precondition: SOLID_PROP pool must exceed slot count");

        for (long seed = 0; seed < 50; seed++) {
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());
            Set<String> seen = new HashSet<>();
            for (char propSymbol : propSymbols) {
                String spriteId = palette.spriteIdOf(propSymbol);
                assertNotNull(spriteId);
                assertTrue(seen.add(spriteId),
                        "duplicate prop " + spriteId + " at seed " + seed);
            }
        }
    }

    // ── Coherence (R1) ───────────────────────────────────────────────────────────────────────────

    @Test
    void majorityOfVarietyFillsShareTheLevelTheme() {
        EnvironmentSpriteRegistry registry = themedRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        List<Character> propSymbols = budget.flexibleSlots().get(TileCategory.SOLID_PROP);
        List<String> candidateThemeTags = sortedThemeTags(registry, budget);

        double totalThemeShare = 0;
        int seedCount = 400;
        for (long seed = 0; seed < seedCount; seed++) {
            String levelTheme = expectedLevelTheme(seed, candidateThemeTags);
            LevelPalette palette = SymbolAllocator.allocate(
                    SymbolAllocationRequest.builder(seed, budget, registry).build());

            int matches = 0;
            for (char propSymbol : propSymbols) {
                String spriteId = palette.spriteIdOf(propSymbol);
                if (registry.get(spriteId).hasThemeTag(levelTheme)) {
                    matches++;
                }
            }
            totalThemeShare += matches / (double) propSymbols.size();
        }
        double averageThemeShare = totalThemeShare / seedCount;
        // Base rate is 20/65 ≈ 0.31 (one theme's props as a fraction of the candidate pool). Coherence
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
        // Many rooms placed together, each reserving a distinct real SOLID_PROP sprite on a distinct symbol.
        SymbolAllocationRequest.Builder builder = SymbolAllocationRequest.builder(7L, budget, registry);
        String[] propSprites = {"prop_terminal", "prop_locker", "prop_crate", "prop_camera", "prop_generator",
                "prop_biopod", "prop_weapon_rack", "prop_specimen_tank", "prop_ai_core"};
        char[] symbols = {'T', 'L', 'C', '#', '%', '&', '=', 'I', 'J'};
        for (int index = 0; index < propSprites.length; index++) {
            builder.placedRoom(RoomSymbolDemand.of(
                    Collections.singletonMap(symbols[index], propSprites[index])));
        }
        LevelPalette palette = SymbolAllocator.allocate(builder.build());
        for (int index = 0; index < propSprites.length; index++) {
            assertEquals(propSprites[index], palette.spriteIdOf(symbols[index]),
                    "room reservation lost for '" + symbols[index] + "'");
        }
    }

    @Test
    void clashOnSameSymbolReroutesToDistinctFreeSymbol() {
        // Two placed rooms demand the SAME symbol 'T' for DIFFERENT sprites. The first keeps 'T'; the
        // second's sprite must be rehomed onto a distinct free prop symbol — both must survive, no throw.
        EnvironmentSpriteRegistry registry = productionRegistry();
        SymbolBudget budget = SymbolBudget.standard();
        SymbolAllocationRequest request = SymbolAllocationRequest.builder(99L, budget, registry)
                .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('T', "prop_terminal")))
                .placedRoom(RoomSymbolDemand.of(Collections.singletonMap('T', "prop_locker")))
                .build();
        LevelPalette palette = SymbolAllocator.allocate(request);

        assertEquals("prop_terminal", palette.spriteIdOf('T'), "first room should keep the clashed symbol");
        boolean lockerRehomed = false;
        for (char propSymbol : budget.flexibleSlots().get(TileCategory.SOLID_PROP)) {
            if ("prop_locker".equals(palette.spriteIdOf(propSymbol))) {
                lockerRehomed = true;
                break;
            }
        }
        assertTrue(lockerRehomed, "clashed second sprite prop_locker was dropped, not rehomed");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    // The shared production catalog (bootstrapped once). Every category has a non-empty pool.
    private static EnvironmentSpriteRegistry productionRegistry() {
        TilesetRegistries.bootstrap();
        return TilesetRegistries.sprites();
    }

    // A fresh catalog whose SOLID_PROP pool (60 themed + the vendor + generic + one observatory prop) far
    // exceeds the 16 flexible prop slots, split evenly across three coherence themes. Decals carry the
    // minimum the standard budget needs, untagged, so the level theme is drawn only from the prop themes.
    // It holds NO wall and NO column sprite — exactly like the production catalog since wall-art order 2.
    private static EnvironmentSpriteRegistry themedRegistry() {
        EnvironmentSpriteRegistry registry = new EnvironmentSpriteRegistry();

        registry.register(EnvironmentSpriteDefinition.builder("prop_vendor", TileCategory.SOLID_PROP, "Vendor").build());
        registerThemedProps(registry, TAG_CLINICAL);
        registerThemedProps(registry, TAG_INDUSTRIAL);
        registerThemedProps(registry, TAG_DERELICT);
        // One observatory-tagged prop to confirm coherence/anti-repetition never pull contained art.
        registry.register(EnvironmentSpriteDefinition.builder("prop_obs", TileCategory.SOLID_PROP, "Obs")
                .themeTag(TilesetRegistries.THEME_TAG_OBSERVATORY).build());
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

    private static void registerThemedProps(EnvironmentSpriteRegistry registry, String themeTag) {
        for (int index = 0; index < PROPS_PER_THEME; index++) {
            registry.register(EnvironmentSpriteDefinition
                    .builder("prop_" + themeTag + "_" + index, TileCategory.SOLID_PROP, themeTag + " " + index)
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
