package ge.tbegvadze.toon3d.tileset;

/**
 * The FIXED semantic class a level-file symbol belongs to — "what a symbol MEANS", never varies per
 * level. A symbol's category is permanent; only WHICH {@link EnvironmentSpriteDefinition} fills that
 * category's slot becomes per-level later in the tileset system (see
 * {@code docs/environment-tileset-system.txt}). order-1 covers ONLY the four FLEXIBLE-eligible visual
 * categories below — one per existing {@code Level} predicate group. Doors, pickups, keycards, ammo,
 * hazards, enemy spawns, player-start, and exit are FIXED symbols with permanent meaning AND sprite;
 * they keep their dedicated handling and are NOT part of the sprite registry.
 *
 * <p>Each member carries METADATA (not behaviour) so consumers stay data-driven — a renderer routes a
 * sprite to the wall pipeline vs the billboard pipeline by reading {@link #rendersAsStripe()} rather
 * than switching on a hardcoded char. Headless — no LibGDX imports.
 */
public enum TileCategory {

    /** Solid wall — blocks movement + raycasting, drawn as a vertical stripe. {@code Level.isWall}. */
    WALL("wall", true, true),

    /** Sub-cell cylinder/pillar drawn via ray-circle, blocks movement. {@code Level.isColumn} ('P'). */
    COLUMN("column", true, true),

    /** Billboard prop that blocks movement (barrels, terminals, lockers…). {@code Level.isPropSolid}. */
    SOLID_PROP("solid_prop", false, true),

    /** Walkable flat billboard (corpses, stains, scorches). {@code Level.isPropDecal}, non-pickup. */
    FLOOR_DECAL("floor_decal", false, false);

    private final String  id;
    private final boolean rendersAsStripe;
    private final boolean blocksMovement;

    TileCategory(String id, boolean rendersAsStripe, boolean blocksMovement) {
        this.id              = id;
        this.rendersAsStripe = rendersAsStripe;
        this.blocksMovement  = blocksMovement;
    }

    /** Stable string id (e.g. "wall") — used for logging / tooling and stable across code changes. */
    public String id() {
        return id;
    }

    /**
     * Whether sprites in this category are drawn by the wall/stripe pipeline (WALL, COLUMN) rather
     * than the billboard pipeline (SOLID_PROP, FLOOR_DECAL). Lets a renderer route a sprite without a
     * hardcoded char check.
     */
    public boolean rendersAsStripe() {
        return rendersAsStripe;
    }

    /** Whether a tile in this category blocks player movement (WALL/COLUMN/SOLID_PROP true). */
    public boolean blocksMovement() {
        return blocksMovement;
    }
}
