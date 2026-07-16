package ge.tbegvadze.toon3d.tileset;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The immutable INPUT to the {@link SymbolAllocator} (tileset order-4): everything the deterministic
 * assignment engine needs to turn "this floor's seed and the rooms actually placed" into a concrete
 * {@link LevelPalette}, and nothing else. Because the allocator is a pure function of this request, two
 * equal requests always produce a byte-identical palette — the fairness promise behind seed sharing and
 * permadeath.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@link #levelSeed()} — the floor's master seed (the same source {@code World.floorSeed} feeds
 *       {@code LevelGenerator}); every per-symbol roll derives from it via
 *       {@code GameMath.paletteSymbolSeed}.</li>
 *   <li>{@link #depth()} — dungeon depth, for the mild depth-influence rule (R5). Optional flavour, never
 *       structural.</li>
 *   <li>{@link #budget()} — the FIXED/FLEXIBLE partition ({@link SymbolBudget}); the allocator may only
 *       assign sprites to FLEXIBLE symbols and binds FIXED ones verbatim.</li>
 *   <li>{@link #placedRoomDemands()} — one {@link RoomSymbolDemand} PER SIGNATURE ROOM ACTUALLY PLACED.
 *       Rooms not placed contribute no demand, so their symbols are never reserved and become free
 *       variety slots — the emergent "freed symbols" mechanic.</li>
 *   <li>{@link #registry()} — the art pool ({@link EnvironmentSpriteRegistry}) the allocator draws
 *       variety from.</li>
 * </ul>
 *
 * Headless — no LibGDX.
 */
public final class SymbolAllocationRequest {

    private final long levelSeed;
    private final int depth;
    private final SymbolBudget budget;
    private final List<RoomSymbolDemand> placedRoomDemands;
    private final EnvironmentSpriteRegistry registry;

    private SymbolAllocationRequest(Builder builder) {
        this.levelSeed = builder.levelSeed;
        this.depth = builder.depth;
        this.budget = Objects.requireNonNull(builder.budget, "budget");
        this.registry = Objects.requireNonNull(builder.registry, "registry");
        // Defensive copy so a caller mutating its list after building cannot change the request.
        this.placedRoomDemands =
                Collections.unmodifiableList(new ArrayList<>(builder.placedRoomDemands));
    }

    /** The floor's master seed — every palette roll derives from it. */
    public long levelSeed() {
        return levelSeed;
    }

    /** Dungeon depth, used only for the mild depth-influence variety rule (R5). */
    public int depth() {
        return depth;
    }

    /** The FIXED/FLEXIBLE symbol partition the allocator must respect. */
    public SymbolBudget budget() {
        return budget;
    }

    /**
     * One demand per signature room actually placed on this level. Unmodifiable; may be empty (a level
     * with no signature rooms — every flexible symbol then becomes free variety).
     */
    public List<RoomSymbolDemand> placedRoomDemands() {
        return placedRoomDemands;
    }

    /** The art catalog the allocator selects variety sprites from. */
    public EnvironmentSpriteRegistry registry() {
        return registry;
    }

    /** Starts a request builder for the given seed, budget, and art registry. */
    public static Builder builder(long levelSeed, SymbolBudget budget, EnvironmentSpriteRegistry registry) {
        return new Builder(levelSeed, budget, registry);
    }

    /**
     * Fluent builder. {@code depth} defaults to 0 and {@code placedRoomDemands} to empty, so a request
     * with no signature rooms (every flexible symbol free) needs only the three required inputs.
     */
    public static final class Builder {
        private final long levelSeed;
        private final SymbolBudget budget;
        private final EnvironmentSpriteRegistry registry;
        private int depth = 0;
        private final List<RoomSymbolDemand> placedRoomDemands = new ArrayList<>();

        private Builder(long levelSeed, SymbolBudget budget, EnvironmentSpriteRegistry registry) {
            this.levelSeed = levelSeed;
            this.budget = budget;
            this.registry = registry;
        }

        public Builder depth(int value) {
            this.depth = value;
            return this;
        }

        /** Adds one placed signature room's demand (repeatable). */
        public Builder placedRoom(RoomSymbolDemand demand) {
            this.placedRoomDemands.add(Objects.requireNonNull(demand, "demand"));
            return this;
        }

        public SymbolAllocationRequest build() {
            return new SymbolAllocationRequest(this);
        }
    }
}
