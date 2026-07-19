package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The deterministic engine that turns a {@link SymbolAllocationRequest} (the floor's seed + the rooms
 * actually placed) into a concrete {@link LevelPalette}. THIS IS THE HEART of the symbol/sprite-reuse
 * series (tileset order-4): symbol reuse, freed-symbol reclamation, and rule-governed (never random)
 * variety all happen here. It is a PURE FUNCTION — no static mutable state, no clock, no iteration-order
 * dependence — so the same request always yields a byte-identical palette (the seed-sharing / permadeath
 * fairness promise). Headless: it touches no LibGDX; it does NOT run the generator (order-8 calls it).
 *
 * <p>THE ALGORITHM (see docs/environment-tileset-system.txt § Allocation for the prose spec):
 * <ol>
 *   <li><b>Step 1 — FIXED bindings.</b> Every {@link SymbolBudget#fixedAssignments() fixed} sprite symbol
 *       ('@' → "prop_vendor", …) is bound verbatim and never rolled — EXCEPT the bulk wall symbol 'x',
 *       handled in Step 1b.</li>
 *   <li><b>Step 1b — BASE-WALL variety.</b> The bulk wall symbol 'x' resolves to one of the registered
 *       base walls (the default OR an alternate) chosen deterministically from the floor seed, so two
 *       seeds no longer share one base wall. It stays a base wall (WALL category, never an accent); hand
 *       / legacy levels keep the default because they use {@link LevelPalettes#legacy()}, not this
 *       allocator.</li>
 *   <li><b>Step 2 — RESERVE for placed rooms.</b> Each placed {@link RoomSymbolDemand}'s required symbols
 *       are bound to their required sprites and marked consumed so variety cannot overwrite them. A clash
 *       (two placed rooms wanting the same symbol for different sprites) is resolved by handing the second
 *       sprite a distinct free flexible symbol from the same category.</li>
 *   <li><b>Step 3 — VARIETY fill.</b> Every remaining FLEXIBLE symbol gets a seeded, rule-constrained
 *       sprite from its category pool (rules R1–R5 below).</li>
 *   <li><b>Step 4 — Return</b> the immutable palette.</li>
 * </ol>
 * Symbols of an ABSENT room are never reserved (Step 2 only sees placed rooms), so they fall through to
 * Step 3 as free variety slots — that IS the "freed symbols" mechanic, emergent from the request shape.
 *
 * <p>THE RULES (variety is seeded and CONSTRAINED, never a raw random grab):
 * <ul>
 *   <li><b>R1 COHERENCE</b> — a per-level THEME (one tag chosen from the seed, drawn from the tags the
 *       catalog actually carries) weights matching sprites up ({@code PALETTE_COHERENCE_WEIGHT_MULTIPLIER})
 *       so a level reads as one place.</li>
 *   <li><b>R2 CONTAINMENT</b> — room-contained sprites (the "observatory" tag) are excluded from the
 *       generic variety pool; they are only ever bound when their owning room reserves them in Step 2.</li>
 *   <li><b>R3 ANTI-REPETITION</b> — within one category on one level, two symbols avoid the same sprite
 *       unless the eligible pool is smaller than the slot count (then repeats are allowed,
 *       deterministically).</li>
 *   <li><b>R4 PLAIN-WALL FLOOR</b> — 'x' is bound to a base wall in Step 1b (the default OR an alternate,
 *       never an accent sprite), so accent variety only ever decorates the accent walls and corridors
 *       stay legible; the base wall merely varies which plain material fills the bulk.</li>
 *   <li><b>R5 DEPTH INFLUENCE</b> — deeper levels add a mild weight to {@code PALETTE_HARSH_THEME_TAGS}
 *       sprites, driven entirely by {@link GameBalance}.</li>
 * </ul>
 * All rules read registered data (tags, weights) — the allocator never switches on a specific char.
 */
public final class SymbolAllocator {

    /** Theme tags that mark room-contained art excluded from generic variety (R2). */
    private static final Set<String> ROOM_CONTAINMENT_TAGS =
            Collections.singleton(TilesetRegistries.THEME_TAG_OBSERVATORY);

    /**
     * The reserved NUL "symbol" whose seed stream selects the per-level theme (R1). It is not a real
     * level-file char (all level symbols are printable), so its stream never collides with a symbol's
     * variety roll; {@code GameMath.paletteSymbolSeed} documents char 0 as a handled edge case.
     */
    private static final char THEME_SELECTION_KEY = '\u0000';

    private SymbolAllocator() {}

    /**
     * Allocates the immutable {@link LevelPalette} for the request. Pure function: equal requests yield
     * deep-equal palettes.
     */
    public static LevelPalette allocate(SymbolAllocationRequest request) {
        SymbolBudget budget = request.budget();
        EnvironmentSpriteRegistry registry = request.registry();

        LevelPalette.Builder paletteBuilder = LevelPalette.builder();
        // Local mirrors of the palette so the algorithm can query what is already bound (the builder is
        // write-only). boundSpriteBySymbol: symbol -> sprite id; usedByCategory: sprites already spent in
        // a category (feeds anti-repetition R3).
        Map<Character, String> boundSpriteBySymbol = new HashMap<>();
        Map<TileCategory, Set<String>> usedByCategory = new HashMap<>();

        // Step 1 — FIXED bindings (never rolled), EXCEPT the bulk wall symbol 'x', which gets per-level
        // BASE-WALL variety in Step 1b. Handling 'x' there (instead of binding its default here) keeps
        // exactly one base-wall sprite recorded in usedByCategory, so anti-repetition (R3) treats the
        // chosen base wall — not the unused default — as the spent WALL sprite.
        char baseWallSymbol = budget.baseWallSymbol();
        for (Map.Entry<Character, String> assignment : budget.fixedAssignments().entrySet()) {
            char symbol = assignment.getKey();
            if (symbol == baseWallSymbol) {
                continue; // bound in Step 1b
            }
            String spriteId = assignment.getValue();
            TileCategory category = registry.get(spriteId).category();
            bind(paletteBuilder, boundSpriteBySymbol, usedByCategory, symbol, category, spriteId);
        }

        // Step 1b — BASE-WALL VARIETY. The bulk wall symbol resolves per level to one of the registered
        // base walls (default OR an alternate), seeded from the floor seed so two seeds no longer share
        // one base wall — the fix for "every generated level looks the same". Deterministic: same seed ⇒
        // same base wall. It stays a base wall (WALL category, never an accent), and hand/legacy levels
        // never reach here (they use LevelPalettes.legacy()), so their bulk walls are unchanged.
        bindBaseWall(request, paletteBuilder, boundSpriteBySymbol, usedByCategory, baseWallSymbol);

        // Step 2 — RESERVE the sprites required by every PLACED signature room.
        for (RoomSymbolDemand demand : request.placedRoomDemands()) {
            for (Map.Entry<Character, String> required : demand.requiredSprites().entrySet()) {
                reserveRoomSprite(request, paletteBuilder, boundSpriteBySymbol, usedByCategory,
                        required.getKey(), required.getValue());
            }
        }

        // Step 3 — VARIETY fill for every still-unbound FLEXIBLE symbol.
        Set<String> levelTheme = chooseLevelTheme(request);
        for (TileCategory category : TileCategory.values()) {
            List<Character> flexibleSymbols = budget.flexibleSlots().get(category);
            if (flexibleSymbols == null) {
                continue;
            }
            for (char symbol : flexibleSymbols) {
                if (boundSpriteBySymbol.containsKey(symbol)) {
                    continue; // already fixed (Step 1) or reserved (Step 2)
                }
                EnvironmentSpriteDefinition chosen =
                        pickVarietySprite(request, category, symbol, levelTheme, usedByCategory);
                bind(paletteBuilder, boundSpriteBySymbol, usedByCategory, symbol, category, chosen.id());
            }
        }

        // Step 4 — freeze.
        return paletteBuilder.build();
    }

    // Step 1b helper: bind the bulk wall symbol to a seed-chosen base wall. Filters the budget's ordered
    // base-wall candidates to the ids actually present in this request's registry (so a minimal test
    // registry with only the default resolves safely), then picks one with a dedicated per-symbol RNG
    // stream — GameMath.paletteSymbolSeed(levelSeed, baseWallSymbol), independent of the accent-variety
    // rolls. With a single candidate the pick is forced, reproducing today's fixed base wall.
    private static void bindBaseWall(SymbolAllocationRequest request,
                                     LevelPalette.Builder paletteBuilder,
                                     Map<Character, String> boundSpriteBySymbol,
                                     Map<TileCategory, Set<String>> usedByCategory,
                                     char baseWallSymbol) {
        SymbolBudget budget = request.budget();
        EnvironmentSpriteRegistry registry = request.registry();

        List<String> candidates = new ArrayList<>();
        for (String spriteId : budget.baseWallSpriteIds()) {
            if (registry.contains(spriteId)) {
                candidates.add(spriteId);
            }
        }
        if (candidates.isEmpty()) {
            // Degenerate: not even the default base wall is registered in this request's registry (the
            // default is always the first candidate, so an empty list means it too is absent). Leave the
            // symbol unbound rather than call registry.get() on a missing id — the same behaviour any
            // symbol gets when its sprite is not registered.
            return;
        }

        PaletteRng rng = new PaletteRng(GameMath.paletteSymbolSeed(request.levelSeed(), baseWallSymbol));
        String chosen = candidates.get(rng.nextIntInclusive(0, candidates.size() - 1));
        TileCategory category = registry.get(chosen).category();
        bind(paletteBuilder, boundSpriteBySymbol, usedByCategory, baseWallSymbol, category, chosen);
    }

    // Binds one symbol and records it in the local mirrors so later steps see it as consumed.
    private static void bind(LevelPalette.Builder paletteBuilder,
                             Map<Character, String> boundSpriteBySymbol,
                             Map<TileCategory, Set<String>> usedByCategory,
                             char symbol, TileCategory category, String spriteId) {
        paletteBuilder.bind(symbol, category, spriteId);
        boundSpriteBySymbol.put(symbol, spriteId);
        usedByCategory.computeIfAbsent(category, unused -> new HashSet<>()).add(spriteId);
    }

    // Step 2 helper: reserve one room-required (symbol -> sprite) binding, resolving a clash if the
    // symbol is already bound to a DIFFERENT sprite by an earlier fixed binding or room.
    private static void reserveRoomSprite(SymbolAllocationRequest request,
                                          LevelPalette.Builder paletteBuilder,
                                          Map<Character, String> boundSpriteBySymbol,
                                          Map<TileCategory, Set<String>> usedByCategory,
                                          char symbol, String spriteId) {
        TileCategory category = request.registry().get(spriteId).category();
        String existing = boundSpriteBySymbol.get(symbol);
        if (existing == null) {
            bind(paletteBuilder, boundSpriteBySymbol, usedByCategory, symbol, category, spriteId);
            return;
        }
        if (existing.equals(spriteId)) {
            return; // already reserved to the same sprite — nothing to do
        }
        // Clash: give this room's sprite a DISTINCT free flexible symbol from the same category. Capacity
        // exists because rooms that were NOT placed freed their symbols. Deterministic: the first unbound
        // flexible symbol in declaration order.
        Character freeSymbol =
                firstUnboundFlexibleSymbol(request.budget(), category, boundSpriteBySymbol);
        if (freeSymbol != null) {
            bind(paletteBuilder, boundSpriteBySymbol, usedByCategory, freeSymbol, category, spriteId);
        }
        // else: the category's flexible symbols are all bound — deterministic fallback is to drop this
        // duplicate reservation (the sprite is simply not bound this level). With current room caps this
        // branch is unreachable; SymbolAllocatorTest asserts it never triggers.
    }

    // The first flexible symbol of a category not yet bound, in the budget's declaration order, or null
    // if every flexible symbol in that category is already spent.
    private static Character firstUnboundFlexibleSymbol(SymbolBudget budget, TileCategory category,
                                                        Map<Character, String> boundSpriteBySymbol) {
        List<Character> flexibleSymbols = budget.flexibleSlots().get(category);
        if (flexibleSymbols == null) {
            return null;
        }
        for (char symbol : flexibleSymbols) {
            if (!boundSpriteBySymbol.containsKey(symbol)) {
                return symbol;
            }
        }
        return null;
    }

    // Step 3 core: seeded, rule-constrained pick of one sprite for a flexible symbol.
    private static EnvironmentSpriteDefinition pickVarietySprite(SymbolAllocationRequest request,
                                                                 TileCategory category, char symbol,
                                                                 Set<String> levelTheme,
                                                                 Map<TileCategory, Set<String>> usedByCategory) {
        PaletteRng rng = new PaletteRng(GameMath.paletteSymbolSeed(request.levelSeed(), symbol));

        // R2 CONTAINMENT: drop room-contained sprites from the generic pool.
        List<EnvironmentSpriteDefinition> pool = request.registry().allInCategory(category);
        List<EnvironmentSpriteDefinition> eligible = new ArrayList<>(pool.size());
        for (EnvironmentSpriteDefinition definition : pool) {
            if (!isRoomContained(definition)) {
                eligible.add(definition);
            }
        }
        if (eligible.isEmpty()) {
            // Degenerate: an entire category is room-contained. Fall back to the raw pool so a flexible
            // symbol always resolves to some sprite rather than throwing.
            eligible = new ArrayList<>(pool);
        }

        // R3 ANTI-REPETITION: prefer sprites not already spent in this category; only when the eligible
        // pool is smaller than the number of slots (all already used) do we allow a repeat.
        Set<String> used = usedByCategory.getOrDefault(category, Collections.emptySet());
        List<EnvironmentSpriteDefinition> candidates = new ArrayList<>(eligible.size());
        for (EnvironmentSpriteDefinition definition : eligible) {
            if (!used.contains(definition.id())) {
                candidates.add(definition);
            }
        }
        if (candidates.isEmpty()) {
            candidates = eligible; // pool < slots — deterministic repeats permitted
        }

        // R1 COHERENCE + R5 DEPTH INFLUENCE: build selection weights, then roulette-pick with the seeded
        // roll. weightedChoiceIndex is the shared route/tileset picker (no duplicate implementation).
        float[] weights = new float[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            weights[index] = varietyWeight(candidates.get(index), levelTheme, request.depth());
        }
        int chosenIndex = GameMath.weightedChoiceIndex(weights, rng.nextFloat01());
        return candidates.get(chosenIndex);
    }

    // R1 + R5 weight for one candidate: base, times the coherence multiplier if it matches the level
    // theme, plus a mild depth bonus if it carries a harsh tag.
    private static float varietyWeight(EnvironmentSpriteDefinition definition, Set<String> levelTheme,
                                       int depth) {
        float weight = GameBalance.PALETTE_VARIETY_BASE_WEIGHT;
        if (sharesAnyTag(definition, levelTheme)) {
            weight *= GameBalance.PALETTE_COHERENCE_WEIGHT_MULTIPLIER;
        }
        if (hasAnyHarshTag(definition)) {
            weight += depth * GameBalance.PALETTE_DEPTH_HARSH_WEIGHT_PER_DEPTH;
        }
        return weight;
    }

    // R1: the per-level theme is ONE tag chosen deterministically from the tags the catalog carries
    // across the flexible categories (containment tags excluded). Returns an empty set when the catalog
    // carries no coherence tags — coherence then has no effect and variety is a plain seeded pick.
    private static Set<String> chooseLevelTheme(SymbolAllocationRequest request) {
        TreeSet<String> candidateTags = new TreeSet<>(); // sorted for reproducible indexing
        for (TileCategory category : request.budget().flexibleSlots().keySet()) {
            for (EnvironmentSpriteDefinition definition : request.registry().allInCategory(category)) {
                for (String tag : definition.themeTags()) {
                    if (!ROOM_CONTAINMENT_TAGS.contains(tag)) {
                        candidateTags.add(tag);
                    }
                }
            }
        }
        if (candidateTags.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> sortedTags = new ArrayList<>(candidateTags);
        PaletteRng rng =
                new PaletteRng(GameMath.paletteSymbolSeed(request.levelSeed(), THEME_SELECTION_KEY));
        int themeIndex = rng.nextIntInclusive(0, sortedTags.size() - 1);
        return Collections.singleton(sortedTags.get(themeIndex));
    }

    private static boolean isRoomContained(EnvironmentSpriteDefinition definition) {
        for (String tag : ROOM_CONTAINMENT_TAGS) {
            if (definition.hasThemeTag(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sharesAnyTag(EnvironmentSpriteDefinition definition, Set<String> tags) {
        for (String tag : tags) {
            if (definition.hasThemeTag(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyHarshTag(EnvironmentSpriteDefinition definition) {
        for (String tag : GameBalance.PALETTE_HARSH_THEME_TAGS) {
            if (definition.hasThemeTag(tag)) {
                return true;
            }
        }
        return false;
    }
}
