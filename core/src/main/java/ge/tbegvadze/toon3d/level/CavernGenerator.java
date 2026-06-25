package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Cellular-automata cave generator — produces organic, winding caverns that feel
 * nothing like the rectangular rooms of {@link LevelGenerator} or the spine of
 * {@link LinearCorridorGenerator}.
 *
 * Phase 0 — Noise:         fill interior with 45 % solid probability.
 * Phase 1 — Smoothing:     5 × 4-5-rule CA passes → 1 birth-only rounding pass.
 * Phase 2 — Region audit:  flood-fill, erase small pockets, drunkard-walk stitch
 *                          isolated large regions back to the main cave.
 * Phase 3 — Chambers:      stamp 2-4 rectangular chambers where ≥65 % of the
 *                          footprint already overlaps existing cave floor.
 * Phase 4 — Room typing:   assign ENTRANCE, MEDICAL_BAY, ARMORY, CONTAINMENT_BLOCK,
 *                          CRYO_CHAMBER, STANDARD roles to the stamped chambers.
 * Phase 5 — Decoration:    varied cave walls, atmospheric floor lighting, decals,
 *                          stalagmite columns, optional barrel cluster.
 * Phase 6 — Pickups:       per-chamber loot based on room type.
 * Phase 7 — Enemies:       scatter spawn points in cave body outside safe radius.
 * Phase 8 — Stairs:        BFS to find tile farthest from player spawn.
 * Phase 9 — Connectivity:  final BFS audit; emergency tunnel if any chamber isolated.
 *
 * Grid convention: (0,0) = bottom-left tile, Y-up. No LibGDX imports — pure Java.
 */
public class CavernGenerator implements ILevelGenerator {

    private enum ChamberType {
        ENTRANCE, STANDARD, MEDICAL_BAY, ARMORY, CRYO_CHAMBER, CONTAINMENT_BLOCK
    }

    private static final class Chamber {
        final int leftColumn, bottomRow, rightColumn, topRow;
        ChamberType type;

        Chamber(int leftColumn, int bottomRow, int rightColumn, int topRow) {
            this.leftColumn  = leftColumn;
            this.bottomRow   = bottomRow;
            this.rightColumn = rightColumn;
            this.topRow      = topRow;
            this.type        = ChamberType.STANDARD;
        }

        int centerColumn()   { return (leftColumn + rightColumn) / 2; }
        int centerRow()      { return (bottomRow  + topRow)      / 2; }
        int interiorWidth()  { return Math.max(0, rightColumn - leftColumn - 1); }
        int interiorHeight() { return Math.max(0, topRow      - bottomRow  - 1); }
    }

    private final Random random;
    private       int    spawnColumn;
    private       int    spawnRow;
    private       List<WeaponSpawnPoint> weaponSpawnPoints;

    // Per-tile biome id for the open cave body; -1 = wall, chamber, or unseeded.
    // Populated by assignBiomes() and consumed by every cave-body decoration pass.
    private       int[][] biomeMap;

    // Dungeon floor this generator is building for (1-based); drives the encounter Threat-Point
    // budget (balance idea 4, Pillar 1). Defaults to 1; set via generate(int dungeonDepth).
    private int dungeonDepth = 1;

    public CavernGenerator(long seed) {
        this.random = new Random(seed);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public Level generate(int dungeonDepth) {
        this.dungeonDepth = Math.max(1, dungeonDepth);
        return generate();
    }

    @Override
    public Level generate() {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;

        weaponSpawnPoints = new ArrayList<>();
        spawnColumn = gridWidth  / 2;
        spawnRow    = gridHeight / 2;

        // Phase 0 — random noise
        boolean[][] solid = new boolean[gridHeight][gridWidth];
        initNoise(solid);

        // Phase 1 — cellular automata smoothing
        for (int pass = 0; pass < LevelGenConstants.LEVEL_GEN_CAVE_SMOOTH_PASSES; pass++) {
            solid = smoothPass(solid, false);
        }
        for (int pass = 0; pass < LevelGenConstants.LEVEL_GEN_CAVE_FINAL_FILL_PASSES; pass++) {
            solid = smoothPass(solid, true);
        }

        // Phase 2 — region audit: erase tiny pockets, stitch isolated regions
        auditRegions(solid);

        // Phase 3 — stamp rectangular chambers
        List<Chamber> chambers = stampChambers(solid);
        if (chambers.isEmpty()) return buildFallbackLevel();

        // Phase 4 — assign chamber types
        assignChamberTypes(chambers);

        // Phase 5 — convert boolean map to char grid
        char[][] grid = buildGrid(solid);
        boolean[][] chamberMask = buildChamberMask(chambers);

        // Phase 5a — player spawn first so biome props can honour a safe radius
        placePlayerSpawn(grid, chambers.get(0));

        // Phase 5b — partition the open cave into sci-fi biome regions, then decorate
        CaveBiome[] biomes = assignBiomes(grid, chamberMask);
        decorateCaveBody(grid, chamberMask, biomes);
        decorateChambers(grid, chambers);

        // Phase 6 — pickups
        placePickups(grid, chambers);

        // Phase 7 — weapon spawns
        placeWeaponSpawns(grid, chambers);

        // Phase 8 — enemies: spend the floor's encounter Threat-Point budget (balance idea 4,
        // Pillar 1) instead of a flat biome-weighted spawn count.
        List<EnemySpawnPoint> spawnPoints = placeEnemies(grid);

        // Phase 9 — stairs
        stampStairsDown(grid, spawnPoints);

        // Phase 10 — connectivity audit
        verifyChamberConnectivity(grid, chambers);

        return new Level(grid, spawnPoints, weaponSpawnPoints);
    }

    // -------------------------------------------------------------------------
    // Phase 0 — noise initialisation
    // -------------------------------------------------------------------------

    private void initNoise(boolean[][] solid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                // Force a 1-tile solid border around the entire grid
                if (tileRow == 0 || tileRow == gridHeight - 1
                        || tileColumn == 0 || tileColumn == gridWidth - 1) {
                    solid[tileRow][tileColumn] = true;
                } else {
                    solid[tileRow][tileColumn] =
                        random.nextFloat() < LevelGenConstants.LEVEL_GEN_CAVE_FILL_PROBABILITY;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 1 — cellular automata
    // -------------------------------------------------------------------------

    private boolean[][] smoothPass(boolean[][] solid, boolean birthOnly) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] next = new boolean[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                // Perimeter is always solid
                if (tileRow == 0 || tileRow == gridHeight - 1
                        || tileColumn == 0 || tileColumn == gridWidth - 1) {
                    next[tileRow][tileColumn] = true;
                    continue;
                }
                int solidNeighbors = countSolidNeighbors(solid, tileColumn, tileRow);
                if (solid[tileRow][tileColumn]) {
                    // Death rule: open up if fewer than DEATH_LIMIT solid neighbours
                    next[tileRow][tileColumn] =
                        solidNeighbors >= LevelGenConstants.LEVEL_GEN_CAVE_DEATH_LIMIT;
                } else {
                    // Birth rule: fill if enough solid neighbours; birth-only pass skips opening
                    if (solidNeighbors >= LevelGenConstants.LEVEL_GEN_CAVE_BIRTH_LIMIT) {
                        next[tileRow][tileColumn] = true;
                    } else {
                        next[tileRow][tileColumn] = birthOnly && solid[tileRow][tileColumn];
                    }
                }
            }
        }
        return next;
    }

    private int countSolidNeighbors(boolean[][] solid, int tileColumn, int tileRow) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        int count = 0;
        for (int deltaRow = -1; deltaRow <= 1; deltaRow++) {
            for (int deltaColumn = -1; deltaColumn <= 1; deltaColumn++) {
                if (deltaRow == 0 && deltaColumn == 0) continue;
                int neighborRow    = tileRow    + deltaRow;
                int neighborColumn = tileColumn + deltaColumn;
                if (neighborRow < 0 || neighborRow >= gridHeight
                        || neighborColumn < 0 || neighborColumn >= gridWidth) {
                    count++; // out-of-bounds counts as solid
                } else if (solid[neighborRow][neighborColumn]) {
                    count++;
                }
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Phase 2 — region audit
    // -------------------------------------------------------------------------

    private void auditRegions(boolean[][] solid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] visited    = new boolean[gridHeight][gridWidth];
        List<List<int[]>> regions = new ArrayList<>();

        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (!solid[tileRow][tileColumn] && !visited[tileRow][tileColumn]) {
                    List<int[]> region = floodFillRegion(solid, visited, tileColumn, tileRow);
                    regions.add(region);
                }
            }
        }

        if (regions.isEmpty()) return;

        // Sort regions by size descending; largest = main cave
        regions.sort((regionA, regionB) -> regionB.size() - regionA.size());
        List<int[]> mainRegion = regions.get(0);

        // Erase regions that are too small
        for (int regionIndex = 1; regionIndex < regions.size(); regionIndex++) {
            List<int[]> region = regions.get(regionIndex);
            if (region.size() < LevelGenConstants.LEVEL_GEN_CAVE_MIN_REGION_SIZE) {
                for (int[] tile : region) {
                    solid[tile[1]][tile[0]] = true;
                }
            } else {
                // Large isolated region — stitch to main with drunkard walk
                int[] fromTile = mainRegion.get(random.nextInt(mainRegion.size()));
                int[] toTile   = region.get(random.nextInt(region.size()));
                drunkardWalk(solid, fromTile[0], fromTile[1], toTile[0], toTile[1]);
            }
        }
    }

    private List<int[]> floodFillRegion(boolean[][] solid, boolean[][] visited,
                                         int startColumn, int startRow) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        List<int[]> region = new ArrayList<>();
        Queue<int[]> queue = new LinkedList<>();
        visited[startRow][startColumn] = true;
        queue.add(new int[]{startColumn, startRow});

        int[] deltaColumns = {0, 0, 1, -1};
        int[] deltaRows    = {1, -1, 0, 0};

        while (!queue.isEmpty()) {
            int[] current        = queue.poll();
            int   currentColumn  = current[0];
            int   currentRow     = current[1];
            region.add(current);
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (neighborColumn < 0 || neighborColumn >= gridWidth
                        || neighborRow < 0 || neighborRow >= gridHeight) continue;
                if (visited[neighborRow][neighborColumn]) continue;
                if (solid[neighborRow][neighborColumn]) continue;
                visited[neighborRow][neighborColumn] = true;
                queue.add(new int[]{neighborColumn, neighborRow});
            }
        }
        return region;
    }

    private void drunkardWalk(boolean[][] solid,
                               int fromColumn, int fromRow,
                               int toColumn,   int toRow) {
        int currentColumn = fromColumn;
        int currentRow    = fromRow;
        int attempts      = 0;

        while ((currentColumn != toColumn || currentRow != toRow)
                && attempts < LevelGenConstants.LEVEL_GEN_CAVE_MAX_STITCH_ATTEMPTS * 200) {
            attempts++;
            solid[currentRow][currentColumn] = false;

            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_CAVE_TUNNEL_STRAIGHTNESS) {
                // Move toward target
                int differenceColumn = toColumn - currentColumn;
                int differenceRow    = toRow    - currentRow;
                if (Math.abs(differenceColumn) > Math.abs(differenceRow)) {
                    currentColumn += Integer.signum(differenceColumn);
                } else if (differenceRow != 0) {
                    currentRow += Integer.signum(differenceRow);
                } else {
                    currentColumn += Integer.signum(differenceColumn);
                }
            } else {
                // Random step
                int direction      = random.nextInt(4);
                int[] deltaColumns = {0, 0, 1, -1};
                int[] deltaRows    = {1, -1, 0, 0};
                currentColumn += deltaColumns[direction];
                currentRow    += deltaRows[direction];
            }

            // Clamp to interior
            currentColumn = Math.max(1, Math.min(LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 2, currentColumn));
            currentRow    = Math.max(1, Math.min(LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 2, currentRow));
        }
        // Carve the arrival tile
        solid[currentRow][currentColumn] = false;
    }

    // -------------------------------------------------------------------------
    // Phase 3 — stamp rectangular chambers
    // -------------------------------------------------------------------------

    private List<Chamber> stampChambers(boolean[][] solid) {
        int chamberTarget = randomBetween(
            LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MIN,
            LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MAX);
        List<Chamber> chambers = new ArrayList<>();

        for (int attempt = 0;
             attempt < LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_TRIES && chambers.size() < chamberTarget;
             attempt++) {
            int width  = randomBetween(LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MIN_WIDTH,
                                       LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MAX_WIDTH);
            int height = randomBetween(LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MIN_HEIGHT,
                                       LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MAX_HEIGHT);
            int totalWidth  = width  + 2;
            int totalHeight = height + 2;

            int leftColumn  = 1 + random.nextInt(LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - totalWidth  - 2);
            int bottomRow   = 1 + random.nextInt(LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - totalHeight - 2);
            int rightColumn = leftColumn + totalWidth  - 1;
            int topRow      = bottomRow  + totalHeight - 1;

            // Count how much of the interior already overlaps cave floor
            int interiorArea  = width * height;
            int floorOverlap  = 0;
            for (int tileRow = bottomRow + 1; tileRow < topRow; tileRow++) {
                for (int tileColumn = leftColumn + 1; tileColumn < rightColumn; tileColumn++) {
                    if (!solid[tileRow][tileColumn]) floorOverlap++;
                }
            }

            float overlapFraction = interiorArea > 0 ? (float) floorOverlap / interiorArea : 0f;
            if (overlapFraction < LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_FLOOR_OVERLAP) continue;

            // Check no overlap with existing chamber bounding boxes
            Chamber candidate = new Chamber(leftColumn, bottomRow, rightColumn, topRow);
            boolean overlaps  = false;
            for (Chamber existing : chambers) {
                if (leftColumn  <= existing.rightColumn && rightColumn >= existing.leftColumn
                        && bottomRow <= existing.topRow   && topRow    >= existing.bottomRow) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) continue;

            // Carve the chamber interior into the solid map
            for (int tileRow = bottomRow + 1; tileRow < topRow; tileRow++) {
                for (int tileColumn = leftColumn + 1; tileColumn < rightColumn; tileColumn++) {
                    solid[tileRow][tileColumn] = false;
                }
            }

            chambers.add(candidate);
        }
        return chambers;
    }

    // -------------------------------------------------------------------------
    // Phase 4 — chamber type assignment
    // -------------------------------------------------------------------------

    private void assignChamberTypes(List<Chamber> chambers) {
        chambers.get(0).type = ChamberType.ENTRANCE;

        boolean medicalBayPlaced    = false;
        boolean armoryPlaced        = false;
        int     cryoChamberCount    = 0;
        int     containmentCount    = 0;

        // Medical Bay — prefer the chamber nearest grid centre (roughly mid-cave)
        int gridCenterColumn = LevelGenConstants.LEVEL_GEN_GRID_WIDTH  / 2;
        int gridCenterRow    = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT / 2;
        int closestIndex     = -1;
        int closestDistance  = Integer.MAX_VALUE;
        for (int chamberIndex = 1; chamberIndex < chambers.size(); chamberIndex++) {
            Chamber chamber     = chambers.get(chamberIndex);
            int     distanceCol = Math.abs(chamber.centerColumn() - gridCenterColumn);
            int     distanceRow = Math.abs(chamber.centerRow()    - gridCenterRow);
            int     distance    = distanceCol + distanceRow;
            if (chamber.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH
                    && chamber.interiorHeight() >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT
                    && distance < closestDistance) {
                closestDistance = distance;
                closestIndex    = chamberIndex;
            }
        }
        if (closestIndex >= 0) {
            chambers.get(closestIndex).type = ChamberType.MEDICAL_BAY;
            medicalBayPlaced = true;
        }

        // Armory — random eligible chamber at 80 % chance
        if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_CHANCE) {
            List<Chamber> eligible = new ArrayList<>();
            for (int chamberIndex = 1; chamberIndex < chambers.size(); chamberIndex++) {
                Chamber chamber = chambers.get(chamberIndex);
                if (chamber.type == ChamberType.STANDARD
                        && chamber.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_WIDTH
                        && chamber.interiorHeight() >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_HEIGHT) {
                    eligible.add(chamber);
                }
            }
            if (!eligible.isEmpty()) {
                eligible.get(random.nextInt(eligible.size())).type = ChamberType.ARMORY;
                armoryPlaced = true;
            }
        }

        // Remaining — cumulative band rolls
        for (int chamberIndex = 1; chamberIndex < chambers.size(); chamberIndex++) {
            Chamber chamber = chambers.get(chamberIndex);
            if (chamber.type != ChamberType.STANDARD) continue;
            float roll = random.nextFloat();

            if (cryoChamberCount < LevelGenConstants.LEVEL_GEN_CRYO_MAX
                    && chamber.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_WIDTH
                    && chamber.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_HEIGHT
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE) {
                chamber.type = ChamberType.CRYO_CHAMBER;
                cryoChamberCount++;
            } else if (containmentCount < LevelGenConstants.LEVEL_GEN_CONTAINMENT_MAX
                    && chamber.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_WIDTH
                    && chamber.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_HEIGHT
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE
                               + LevelGenConstants.LEVEL_GEN_CONTAINMENT_CHANCE) {
                chamber.type = ChamberType.CONTAINMENT_BLOCK;
                containmentCount++;
            }
        }

        // Suppress unused variable warnings for future-guard booleans
        boolean unused = medicalBayPlaced || armoryPlaced;
    }

    // -------------------------------------------------------------------------
    // Phase 5 — convert solid map to char grid
    // -------------------------------------------------------------------------

    private char[][] buildGrid(boolean[][] solid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        char[][] grid  = new char[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                grid[tileRow][tileColumn] = solid[tileRow][tileColumn] ? 'x' : ' ';
            }
        }
        return grid;
    }

    // -------------------------------------------------------------------------
    // Phase 5b — sci-fi biome regions
    // -------------------------------------------------------------------------

    /**
     * One contiguous region of the open cave body. Each biome reskins its own
     * walls, floor lighting, decals, scattered props and enemy mix so that a
     * single organic cavern reads as several distinct sci-fi environments fused
     * together. All symbols are existing tiles (see docs/tile-symbols.txt) — no
     * new symbol is introduced.
     */
    private enum CaveBiome {
        // Overgrown bioluminescent spore garden: flesh walls, glowing pools, bio-pods.
        FUNGAL_HOLLOW (new char[]{'G', 'j'},      new char[]{'O', '.', 's'}, new char[]{'&'},           'I', FloorMood.GLOW),
        // Frozen cryo-vault: frost walls, containment glass, dim blue light, cryo pods.
        FROST_VAULT   (new char[]{'Z', 'N'},      new char[]{'s', 'O'},      new char[]{'&', 'L'},      '&', FloorMood.DIM),
        // Radioactive reactor sump: radiation walls, leaking drums, flickering light.
        REACTOR_SUMP  (new char[]{'U', 'h', 'k'}, new char[]{'e', 'O', '.'}, new char[]{'g', 'E'},      '%', FloorMood.FLICKER),
        // Derelict data wreck: terminal/wire walls, dead consoles, an AI tomb.
        DERELICT_NEST (new char[]{'t', 'w', 'c'}, new char[]{'.', 'm', 'e'}, new char[]{'T', 'L', 'C'}, 'J', FloorMood.DIM),
        // Infested gore warren: flesh walls, corpse drifts, deep darkness, deathtrap drums.
        GORE_WARREN   (new char[]{'G', 'j'},      new char[]{'m', '.', 's'}, new char[]{'E'},           'E', FloorMood.DARK),
        // Bare mineral grotto: corroded rock, mining rubble, dense stalagmite forests.
        CRYSTAL_GROTTO(new char[]{'j', 'k', 'x'}, new char[]{'O', '.'},      new char[]{'C'},           'C', FloorMood.ROCKY);

        final char[]    wallChars;     // candidate reskins for walls bordering this biome
        final char[]    decalChars;    // walkable decals scattered on the floor
        final char[]    scatterProps;  // solid props scattered sparsely through the region
        final char      landmarkProp;  // rare set-piece prop anchoring a feature cluster
        final FloorMood mood;          // floor-lighting personality

        CaveBiome(char[] wallChars, char[] decalChars, char[] scatterProps,
                  char landmarkProp, FloorMood mood) {
            this.wallChars    = wallChars;
            this.decalChars   = decalChars;
            this.scatterProps = scatterProps;
            this.landmarkProp = landmarkProp;
            this.mood         = mood;
        }
    }

    /** Floor-lighting profile of a biome (drives the unlit/dim/lit/flicker mix). */
    private enum FloorMood { GLOW, DIM, DARK, FLICKER, ROCKY }

    /**
     * Partitions the open cave body into LEVEL_GEN_CAVE_BIOME_MIN..MAX contiguous
     * regions. Seeds are chosen by greedy farthest-point sampling so they spread
     * across the cave, then a multi-source flood fill grows every region outward
     * simultaneously, meeting along organic cave boundaries. Returns the biome
     * assigned to each id; biomeMap holds the per-tile id.
     */
    private CaveBiome[] assignBiomes(char[][] grid, boolean[][] chamberMask) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        biomeMap = new int[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                biomeMap[tileRow][tileColumn] = -1;
            }
        }

        // Collect every open cave-body floor tile (chambers are excluded so the
        // man-made boxes keep their own clean theming — the contrast is the point).
        List<int[]> caveFloor = new ArrayList<>();
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (chamberMask[tileRow][tileColumn]) continue;
                if (grid[tileRow][tileColumn] == ' ') {
                    caveFloor.add(new int[]{tileColumn, tileRow});
                }
            }
        }
        if (caveFloor.isEmpty()) return new CaveBiome[0];

        // Decide how many biomes and which types (shuffled for per-level variety).
        CaveBiome[] allBiomes = CaveBiome.values();
        int biomeCount = randomBetween(LevelGenConstants.LEVEL_GEN_CAVE_BIOME_MIN,
                                       LevelGenConstants.LEVEL_GEN_CAVE_BIOME_MAX);
        biomeCount = Math.min(biomeCount, allBiomes.length);
        biomeCount = Math.min(biomeCount, caveFloor.size());

        List<CaveBiome> pool = new ArrayList<>();
        for (CaveBiome biome : allBiomes) pool.add(biome);
        shuffle(pool);
        CaveBiome[] biomes = new CaveBiome[biomeCount];
        for (int biomeIndex = 0; biomeIndex < biomeCount; biomeIndex++) {
            biomes[biomeIndex] = pool.get(biomeIndex);
        }

        // Seed and multi-source flood the biome ids across the cave floor.
        List<int[]> seeds = pickDistinctTiles(caveFloor, biomeCount);
        Queue<int[]> frontier = new LinkedList<>();
        for (int biomeIndex = 0; biomeIndex < seeds.size(); biomeIndex++) {
            int[] seed = seeds.get(biomeIndex);
            biomeMap[seed[1]][seed[0]] = biomeIndex;
            frontier.add(seed);
        }

        int[] deltaColumns = {0, 0, 1, -1};
        int[] deltaRows    = {1, -1, 0, 0};
        while (!frontier.isEmpty()) {
            int[] current       = frontier.poll();
            int   currentColumn = current[0];
            int   currentRow    = current[1];
            int   currentBiome  = biomeMap[currentRow][currentColumn];
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (chamberMask[neighborRow][neighborColumn]) continue;
                if (biomeMap[neighborRow][neighborColumn] != -1) continue;
                if (grid[neighborRow][neighborColumn] != ' ') continue;
                biomeMap[neighborRow][neighborColumn] = currentBiome;
                frontier.add(new int[]{neighborColumn, neighborRow});
            }
        }

        // Any cave tile the flood could not reach (e.g. only linked via a chamber)
        // adopts the nearest seed's biome so no floor is left untyped.
        for (int[] tile : caveFloor) {
            if (biomeMap[tile[1]][tile[0]] != -1) continue;
            int nearestBiome    = 0;
            int nearestDistance = Integer.MAX_VALUE;
            for (int biomeIndex = 0; biomeIndex < seeds.size(); biomeIndex++) {
                int[] seed = seeds.get(biomeIndex);
                int distance = Math.abs(seed[0] - tile[0]) + Math.abs(seed[1] - tile[1]);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestBiome    = biomeIndex;
                }
            }
            biomeMap[tile[1]][tile[0]] = nearestBiome;
        }
        return biomes;
    }

    /** Fisher-Yates shuffle using the generator's seeded Random. */
    private <T> void shuffle(List<T> list) {
        for (int index = list.size() - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            T temporary = list.get(index);
            list.set(index, list.get(swapIndex));
            list.set(swapIndex, temporary);
        }
    }

    /**
     * Greedy farthest-point sampling: picks {@code count} tiles that are spread
     * across the source set so biome seeds do not clump in one corner of the cave.
     */
    private List<int[]> pickDistinctTiles(List<int[]> source, int count) {
        List<int[]> picked = new ArrayList<>();
        if (source.isEmpty()) return picked;
        picked.add(source.get(random.nextInt(source.size())));
        while (picked.size() < count) {
            int[] best         = null;
            int   bestDistance = -1;
            for (int sample = 0; sample < 64; sample++) {
                int[] candidate = source.get(random.nextInt(source.size()));
                int   nearest   = Integer.MAX_VALUE;
                for (int[] chosen : picked) {
                    int distance = Math.abs(chosen[0] - candidate[0])
                                 + Math.abs(chosen[1] - candidate[1]);
                    nearest = Math.min(nearest, distance);
                }
                if (nearest > bestDistance) {
                    bestDistance = nearest;
                    best         = candidate;
                }
            }
            if (best == null) break;
            picked.add(best);
        }
        return picked;
    }

    // -------------------------------------------------------------------------
    // Phase 5b — cave body decoration (biome-driven)
    // -------------------------------------------------------------------------

    private void decorateCaveBody(char[][] grid, boolean[][] chamberMask, CaveBiome[] biomes) {
        if (biomes.length == 0) return;
        themeCaveFloor(grid, chamberMask, biomes);
        themeCaveWalls(grid, chamberMask, biomes);
        placeCaveDecals(grid, chamberMask, biomes);
        placeStalagmites(grid, chamberMask, biomes);
        placeBiomeProps(grid, chamberMask, biomes);
    }

    /** Reskins each cave-floor tile per its biome's lighting mood, keeping a lit halo around spawn. */
    private void themeCaveFloor(char[][] grid, boolean[][] chamberMask, CaveBiome[] biomes) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        int flickerRemaining = LevelGenConstants.LEVEL_GEN_CAVE_FLICKER_BUDGET;
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (chamberMask[tileRow][tileColumn]) continue;
                if (grid[tileRow][tileColumn] != ' ') continue;
                int biomeId = biomeMap[tileRow][tileColumn];
                if (biomeId < 0) continue;
                int chebyshev = Math.max(Math.abs(tileColumn - spawnColumn),
                                         Math.abs(tileRow    - spawnRow));
                if (chebyshev <= 2) continue; // readable lit halo around the player spawn
                char floorChar = floorCharForMood(biomes[biomeId].mood, flickerRemaining > 0);
                if (floorChar == 'f') flickerRemaining--;
                grid[tileRow][tileColumn] = floorChar;
            }
        }
    }

    private char floorCharForMood(FloorMood mood, boolean flickerAllowed) {
        float roll = random.nextFloat();
        switch (mood) {
            case GLOW:  return roll < 0.10f ? 'u' : (roll < 0.45f ? 'l' : ' ');
            case DIM:   return roll < 0.22f ? 'u' : (roll < 0.78f ? 'l' : ' ');
            case DARK:  return roll < 0.62f ? 'u' : (roll < 0.88f ? 'l' : ' ');
            case ROCKY: return roll < 0.38f ? 'u' : (roll < 0.82f ? 'l' : ' ');
            case FLICKER:
                if (flickerAllowed && roll < 0.05f) return 'f';
                return roll < 0.55f ? 'u' : (roll < 0.82f ? 'l' : ' ');
            default:    return ' ';
        }
    }

    /** Reskins cave walls that border floor using the palette of the adjacent biome. */
    private void themeCaveWalls(char[][] grid, boolean[][] chamberMask, CaveBiome[] biomes) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (chamberMask[tileRow][tileColumn]) continue;
                if (grid[tileRow][tileColumn] != 'x') continue;
                int biomeId = adjacentBiome(tileColumn, tileRow);
                if (biomeId < 0) continue;
                if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_CAVE_BIOME_WALL_CHANCE) continue;
                char[] palette = biomes[biomeId].wallChars;
                grid[tileRow][tileColumn] = palette[random.nextInt(palette.length)];
            }
        }
    }

    /** Returns the biome id of the first cardinal neighbour belonging to a biome, or -1. */
    private int adjacentBiome(int tileColumn, int tileRow) {
        int[] deltaColumns = {0, 0, 1, -1};
        int[] deltaRows    = {1, -1, 0, 0};
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            if (biomeMap[neighborRow][neighborColumn] >= 0) {
                return biomeMap[neighborRow][neighborColumn];
            }
        }
        return -1;
    }

    /** Scatters biome-flavoured walkable decals (blood, oil, scorch, corpses) on cave floor. */
    private void placeCaveDecals(char[][] grid, boolean[][] chamberMask, CaveBiome[] biomes) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (chamberMask[tileRow][tileColumn]) continue;
                int biomeId = biomeMap[tileRow][tileColumn];
                if (biomeId < 0) continue;
                char cell = grid[tileRow][tileColumn];
                if (cell != ' ' && cell != 'u' && cell != 'l') continue;
                if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_CAVE_DECAL_CHANCE) continue;
                char[] decals = biomes[biomeId].decalChars;
                grid[tileRow][tileColumn] = decals[random.nextInt(decals.length)];
            }
        }
    }

    /** Scatters stalagmite columns 'P' in open cave space; mineral grottos grow forests of them. */
    private void placeStalagmites(char[][] grid, boolean[][] chamberMask, CaveBiome[] biomes) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (chamberMask[tileRow][tileColumn]) continue;
                int biomeId = biomeMap[tileRow][tileColumn];
                if (biomeId < 0) continue;
                char cell = grid[tileRow][tileColumn];
                if (cell != ' ' && cell != 'l' && cell != 'u') continue;
                if (openNeighborCount(grid, tileColumn, tileRow) < 4) continue;
                float chance = LevelGenConstants.LEVEL_GEN_CAVE_STALAGMITE_CHANCE;
                if (biomes[biomeId].mood == FloorMood.ROCKY) chance *= 3f;
                if (random.nextFloat() < chance) {
                    grid[tileRow][tileColumn] = 'P';
                }
            }
        }
    }

    /**
     * Populates each biome with solid props: a few landmark feature clusters per
     * region plus a sparse scatter of the biome's prop set. Every placement is
     * pinch-guarded (see canStampSolidProp) so a prop can never seal a tunnel.
     */
    private void placeBiomeProps(char[][] grid, boolean[][] chamberMask, CaveBiome[] biomes) {
        for (int biomeId = 0; biomeId < biomes.length; biomeId++) {
            int featureCount = randomBetween(LevelGenConstants.LEVEL_GEN_CAVE_BIOME_FEATURE_MIN,
                                             LevelGenConstants.LEVEL_GEN_CAVE_BIOME_FEATURE_MAX);
            for (int feature = 0; feature < featureCount; feature++) {
                placeFeatureCluster(grid, chamberMask, biomes, biomeId);
            }
        }

        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (chamberMask[tileRow][tileColumn]) continue;
                int biomeId = biomeMap[tileRow][tileColumn];
                if (biomeId < 0) continue;
                if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_CAVE_BIOME_PROP_CHANCE) continue;
                if (!canStampSolidProp(grid, tileColumn, tileRow)) continue;
                char[] props = biomes[biomeId].scatterProps;
                grid[tileRow][tileColumn] = props[random.nextInt(props.length)];
            }
        }
    }

    /** Stamps one small landmark cluster (e.g. pod nest, barrel pile, console bank) in a biome region. */
    private void placeFeatureCluster(char[][] grid, boolean[][] chamberMask,
                                     CaveBiome[] biomes, int biomeId) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        for (int attempt = 0; attempt < 60; attempt++) {
            int tileColumn = 2 + random.nextInt(gridWidth  - 4);
            int tileRow    = 2 + random.nextInt(gridHeight - 4);
            if (chamberMask[tileRow][tileColumn]) continue;
            if (biomeMap[tileRow][tileColumn] != biomeId) continue;
            if (!canStampSolidProp(grid, tileColumn, tileRow)) continue;

            char landmark = biomes[biomeId].landmarkProp;
            char filler   = biomes[biomeId].scatterProps[0];
            int  clusterSize = LevelGenConstants.LEVEL_GEN_CAVE_BIOME_CLUSTER_SIZE;
            int  placed      = 0;
            for (int stamp = 0; stamp < clusterSize * 4 && placed < clusterSize; stamp++) {
                int offsetColumn = tileColumn + random.nextInt(3) - 1;
                int offsetRow    = tileRow    + random.nextInt(3) - 1;
                if (!isInBounds(offsetColumn, offsetRow)) continue;
                if (chamberMask[offsetRow][offsetColumn]) continue;
                if (!canStampSolidProp(grid, offsetColumn, offsetRow)) continue;
                boolean anchorTile = placed == 0
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CAVE_BIOME_LANDMARK_CHANCE;
                grid[offsetRow][offsetColumn] = anchorTile ? landmark : filler;
                placed++;
            }
            return;
        }
    }

    /**
     * A solid prop may be stamped on a tile only when it is bare cave floor, sits
     * beyond the spawn safe radius, and keeps at least PROP_MIN_OPEN walkable
     * cardinal neighbours — guaranteeing it can never seal a one-tile pinch. Because
     * neighbours that already became props no longer count as walkable, the guard
     * tightens automatically as a cluster grows.
     */
    private boolean canStampSolidProp(char[][] grid, int tileColumn, int tileRow) {
        char cell = grid[tileRow][tileColumn];
        if (cell != ' ' && cell != 'u' && cell != 'l') return false;
        int chebyshev = Math.max(Math.abs(tileColumn - spawnColumn),
                                 Math.abs(tileRow    - spawnRow));
        if (chebyshev < LevelGenConstants.LEVEL_GEN_CAVE_BIOME_PROP_SAFE_RADIUS) return false;
        return openNeighborCount(grid, tileColumn, tileRow)
                >= LevelGenConstants.LEVEL_GEN_CAVE_BIOME_PROP_MIN_OPEN;
    }

    private int openNeighborCount(char[][] grid, int tileColumn, int tileRow) {
        int count = 0;
        int[] deltaColumns = {0, 0, 1, -1};
        int[] deltaRows    = {1, -1, 0, 0};
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            char neighbor = grid[neighborRow][neighborColumn];
            if (isWalkableTile(neighbor)) count++;
        }
        return count;
    }

    private boolean[][] buildChamberMask(List<Chamber> chambers) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] mask = new boolean[gridHeight][gridWidth];
        for (Chamber chamber : chambers) {
            for (int tileRow = chamber.bottomRow; tileRow <= chamber.topRow; tileRow++) {
                for (int tileColumn = chamber.leftColumn; tileColumn <= chamber.rightColumn; tileColumn++) {
                    if (isInBounds(tileColumn, tileRow)) mask[tileRow][tileColumn] = true;
                }
            }
        }
        return mask;
    }

    // -------------------------------------------------------------------------
    // Phase 5 — chamber decoration
    // -------------------------------------------------------------------------

    private void decorateChambers(char[][] grid, List<Chamber> chambers) {
        for (Chamber chamber : chambers) {
            switch (chamber.type) {
                case MEDICAL_BAY:       decorateMedicalBay(grid, chamber);       break;
                case ARMORY:            decorateArmory(grid, chamber);            break;
                case CRYO_CHAMBER:      decorateCryoChamber(grid, chamber);       break;
                case CONTAINMENT_BLOCK: decorateContainmentBlock(grid, chamber);  break;
                default:                break;
            }
        }
    }

    private void decorateMedicalBay(char[][] grid, Chamber chamber) {
        // Medical 'M' walls on the perimeter
        for (int tileColumn = chamber.leftColumn; tileColumn <= chamber.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ chamber.bottomRow, chamber.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_MEDICAL_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'M';
                }
            }
        }
        for (int tileRow = chamber.bottomRow; tileRow <= chamber.topRow; tileRow++) {
            for (int wallColumn : new int[]{ chamber.leftColumn, chamber.rightColumn }) {
                if (Level.isWall(grid[tileRow][wallColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_MEDICAL_WALL_CHANCE) {
                    grid[tileRow][wallColumn] = 'M';
                }
            }
        }
        // Wipe floor decals and set to bright lit floor inside a medical bay
        for (int tileRow = chamber.bottomRow + 1; tileRow < chamber.topRow; tileRow++) {
            for (int tileColumn = chamber.leftColumn + 1; tileColumn < chamber.rightColumn; tileColumn++) {
                char cell = grid[tileRow][tileColumn];
                if (isWalkableTile(cell) || cell == 'm' || cell == '.' || cell == 'O') {
                    grid[tileRow][tileColumn] = ' ';
                }
            }
        }
    }

    private void decorateArmory(char[][] grid, Chamber chamber) {
        // Blast 'X' walls on the perimeter
        for (int tileColumn = chamber.leftColumn; tileColumn <= chamber.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ chamber.bottomRow, chamber.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_BLAST_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'X';
                }
            }
        }
        // Weapon racks '=' along the back interior row
        if (chamber.interiorWidth() > 0) {
            int backRow = chamber.topRow - 1;
            for (int tileColumn = chamber.leftColumn + 1; tileColumn < chamber.rightColumn; tileColumn++) {
                if (isWalkableTile(grid[backRow][tileColumn])) {
                    grid[backRow][tileColumn] = '=';
                }
            }
        }
    }

    private void decorateCryoChamber(char[][] grid, Chamber chamber) {
        // Cryo 'Z' walls on the top perimeter
        for (int tileColumn = chamber.leftColumn; tileColumn <= chamber.rightColumn; tileColumn++) {
            if (Level.isWall(grid[chamber.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CRYO_WALL_CHANCE) {
                grid[chamber.topRow][tileColumn] = 'Z';
            }
        }
        // Bio-pod '&' rows at 2-tile spacing
        for (int tileRow = chamber.bottomRow + 2; tileRow < chamber.topRow - 1; tileRow += 2) {
            for (int tileColumn = chamber.leftColumn + 2; tileColumn < chamber.rightColumn - 1; tileColumn += 3) {
                if (isWalkableTile(grid[tileRow][tileColumn])) {
                    grid[tileRow][tileColumn] = '&';
                }
            }
        }
    }

    private void decorateContainmentBlock(char[][] grid, Chamber chamber) {
        // Glass 'N' cell fronts on side walls at intervals
        for (int tileRow = chamber.bottomRow + 2; tileRow < chamber.topRow - 1; tileRow += 3) {
            for (int wallColumn : new int[]{ chamber.leftColumn, chamber.rightColumn }) {
                if (Level.isWall(grid[tileRow][wallColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CONTAINMENT_GLASS_CHANCE) {
                    grid[tileRow][wallColumn] = 'N';
                }
            }
        }
        // Quarantine 'Q' walls on top perimeter
        for (int tileColumn = chamber.leftColumn; tileColumn <= chamber.rightColumn; tileColumn++) {
            if (Level.isWall(grid[chamber.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CONTAINMENT_BIO_CHANCE) {
                grid[chamber.topRow][tileColumn] = 'Q';
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 5 — player spawn
    // -------------------------------------------------------------------------

    private void placePlayerSpawn(char[][] grid, Chamber entrance) {
        int centerColumn = entrance.centerColumn();
        int centerRow    = entrance.centerRow();
        // Ensure the center tile is open cave floor (clear any decal/prop stamped there)
        if (isWalkableTile(grid[centerRow][centerColumn])
                || isDecal(grid[centerRow][centerColumn])) {
            grid[centerRow][centerColumn] = 'p';
            spawnColumn = centerColumn;
            spawnRow    = centerRow;
            return;
        }
        // Search interior
        for (int tileRow = entrance.bottomRow + 1; tileRow < entrance.topRow; tileRow++) {
            for (int tileColumn = entrance.leftColumn + 1; tileColumn < entrance.rightColumn; tileColumn++) {
                char cell = grid[tileRow][tileColumn];
                if (isWalkableTile(cell) || isDecal(cell)) {
                    grid[tileRow][tileColumn] = 'p';
                    spawnColumn = tileColumn;
                    spawnRow    = tileRow;
                    return;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 6 — pickups
    // -------------------------------------------------------------------------

    private void placePickups(char[][] grid, List<Chamber> chambers) {
        for (Chamber chamber : chambers) {
            if (chamber.type == ChamberType.ENTRANCE) continue;
            switch (chamber.type) {
                case MEDICAL_BAY:
                    tryPlacePickup(grid, chamber, 'H');
                    tryPlacePickup(grid, chamber, 'H');
                    break;
                case ARMORY:
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_ARMOUR_CHANCE)
                        tryPlacePickup(grid, chamber, 'A');
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_MEDKIT_CHANCE)
                        tryPlacePickup(grid, chamber, 'H');
                    tryPlacePickup(grid, chamber, randomAmmoChar());
                    if (random.nextBoolean()) tryPlacePickup(grid, chamber, randomAmmoChar());
                    break;
                case CRYO_CHAMBER:
                case CONTAINMENT_BLOCK:
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_MEDKIT_CHANCE)
                        tryPlacePickup(grid, chamber, 'H');
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_ARMOUR_CHANCE)
                        tryPlacePickup(grid, chamber, 'A');
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_AMMO_CHANCE)
                        tryPlacePickup(grid, chamber, randomAmmoChar());
                    break;
                default:
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_AMMO_CHANCE_PER_ROOM)
                        tryPlacePickup(grid, chamber, randomAmmoChar());
                    break;
            }
        }
    }

    private void tryPlacePickup(char[][] grid, Chamber chamber, char pickupChar) {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (chamber.interiorWidth() <= 0 || chamber.interiorHeight() <= 0) return;
            int tileColumn = chamber.leftColumn + 1 + random.nextInt(chamber.interiorWidth());
            int tileRow    = chamber.bottomRow  + 1 + random.nextInt(chamber.interiorHeight());
            if (isWalkableTile(grid[tileRow][tileColumn])) {
                grid[tileRow][tileColumn] = pickupChar;
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 6 — weapon spawns
    // -------------------------------------------------------------------------

    private void placeWeaponSpawns(char[][] grid, List<Chamber> chambers) {
        for (Chamber chamber : chambers) {
            if (chamber.type == ChamberType.ARMORY) {
                tryPlaceWeaponSpawn(grid, chamber);
                break;
            }
        }
        for (Chamber chamber : chambers) {
            if (chamber.type == ChamberType.ENTRANCE || chamber.type == ChamberType.ARMORY) continue;
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_RANDOM_ROOM_WEAPON_CHANCE) {
                tryPlaceWeaponSpawn(grid, chamber);
            }
        }
    }

    private boolean tryPlaceWeaponSpawn(char[][] grid, Chamber chamber) {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (chamber.interiorWidth() <= 0 || chamber.interiorHeight() <= 0) return false;
            int tileColumn = chamber.leftColumn + 1 + random.nextInt(chamber.interiorWidth());
            int tileRow    = chamber.bottomRow  + 1 + random.nextInt(chamber.interiorHeight());
            if (isWalkableTile(grid[tileRow][tileColumn])) {
                weaponSpawnPoints.add(new WeaponSpawnPoint(tileColumn, tileRow, randomWeaponItemType()));
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Phase 7 — enemy placement (cave body scatter)
    // -------------------------------------------------------------------------

    /**
     * Spends the floor's encounter Threat-Point budget (balance idea 4, Pillar 1) by scattering
     * the planned roster across eligible cave tiles. The cave has no discrete rooms, so the
     * EncounterBudgetPlanner roster (which already enforces the anchor / type-variety /
     * ranged-melee-mix composition rules) is simply realised on walkable tiles beyond the spawn
     * safe radius — replacing the old flat biome-weighted spawn count.
     */
    private List<EnemySpawnPoint> placeEnemies(char[][] grid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        List<EnemySpawnPoint> spawnPoints = new ArrayList<>();

        EncounterBudgetPlanner.Plan plan = new EncounterBudgetPlanner(dungeonDepth, random).plan();
        List<EnemyType> roster = plan.enemies();
        if (roster.isEmpty()) return spawnPoints;

        boolean[][] usedTiles = new boolean[gridHeight][gridWidth];
        for (EnemyType enemy : roster) {
            for (int attempt = 0; attempt < 80; attempt++) {
                int tileColumn = 1 + random.nextInt(gridWidth  - 2);
                int tileRow    = 1 + random.nextInt(gridHeight - 2);
                char cell = grid[tileRow][tileColumn];
                if (!isWalkableTile(cell) && !isDecal(cell)) continue;
                if (cell == 'p') continue;
                if (usedTiles[tileRow][tileColumn]) continue;
                int chebyshevDistance = Math.max(Math.abs(tileColumn - spawnColumn),
                                                 Math.abs(tileRow    - spawnRow));
                if (chebyshevDistance < LevelGenConstants.LEVEL_GEN_CAVE_SPAWN_SAFE_RADIUS) continue;
                usedTiles[tileRow][tileColumn] = true;
                spawnPoints.add(new EnemySpawnPoint(enemy.spawnChar(), tileColumn, tileRow));
                break;
            }
        }
        return spawnPoints;
    }

    // -------------------------------------------------------------------------
    // Phase 8 — stairs (BFS farthest from spawn)
    // -------------------------------------------------------------------------

    private void stampStairsDown(char[][] grid, List<EnemySpawnPoint> spawnPoints) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        int[][] distance = new int[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                distance[tileRow][tileColumn] = -1;
            }
        }

        Queue<int[]> queue = new LinkedList<>();
        distance[spawnRow][spawnColumn] = 0;
        queue.add(new int[]{spawnColumn, spawnRow});

        int[] deltaColumns = {0, 0, 1, -1};
        int[] deltaRows    = {1, -1, 0, 0};

        while (!queue.isEmpty()) {
            int[] current       = queue.poll();
            int   currentColumn = current[0];
            int   currentRow    = current[1];
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (distance[neighborRow][neighborColumn] >= 0) continue;
                if (!isPassableForBFS(grid[neighborRow][neighborColumn])) continue;
                distance[neighborRow][neighborColumn] = distance[currentRow][currentColumn] + 1;
                queue.add(new int[]{neighborColumn, neighborRow});
            }
        }

        // Find the reachable floor tile farthest from spawn
        int farthestColumn   = -1;
        int farthestRow      = -1;
        int farthestDistance = -1;
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (distance[tileRow][tileColumn] > farthestDistance
                        && isWalkableTile(grid[tileRow][tileColumn])) {
                    farthestDistance = distance[tileRow][tileColumn];
                    farthestColumn   = tileColumn;
                    farthestRow      = tileRow;
                }
            }
        }

        if (farthestColumn >= 0) {
            grid[farthestRow][farthestColumn] = RenderConstants.STAIRS_DOWN_CHAR;
        }
    }

    // -------------------------------------------------------------------------
    // Phase 9 — connectivity audit
    // -------------------------------------------------------------------------

    private void verifyChamberConnectivity(char[][] grid, List<Chamber> chambers) {
        if (chambers.isEmpty()) return;
        int startColumn = spawnColumn;
        int startRow    = spawnRow;
        for (int chamberIndex = 1; chamberIndex < chambers.size(); chamberIndex++) {
            Chamber chamber = chambers.get(chamberIndex);
            if (!isTileReachable(grid, startColumn, startRow,
                                 chamber.centerColumn(), chamber.centerRow())) {
                drunkardWalkGrid(grid, startColumn, startRow,
                                 chamber.centerColumn(), chamber.centerRow());
            }
        }
    }

    private boolean isTileReachable(char[][] grid,
                                     int startColumn, int startRow,
                                     int targetColumn, int targetRow) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] visited = new boolean[gridHeight][gridWidth];
        int capacity        = gridWidth * gridHeight;
        int[] stackColumns  = new int[capacity];
        int[] stackRows     = new int[capacity];
        int   stackTop      = 0;

        visited[startRow][startColumn] = true;
        stackColumns[stackTop] = startColumn;
        stackRows[stackTop]    = startRow;
        stackTop++;

        int[] deltaColumns = {0, 0, 1, -1};
        int[] deltaRows    = {1, -1, 0, 0};

        while (stackTop > 0) {
            stackTop--;
            int currentColumn = stackColumns[stackTop];
            int currentRow    = stackRows[stackTop];
            if (currentColumn == targetColumn && currentRow == targetRow) return true;
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (visited[neighborRow][neighborColumn]) continue;
                if (!isPassableForBFS(grid[neighborRow][neighborColumn])) continue;
                visited[neighborRow][neighborColumn] = true;
                stackColumns[stackTop] = neighborColumn;
                stackRows[stackTop]    = neighborRow;
                stackTop++;
            }
        }
        return false;
    }

    private void drunkardWalkGrid(char[][] grid,
                                   int fromColumn, int fromRow,
                                   int toColumn,   int toRow) {
        int currentColumn = fromColumn;
        int currentRow    = fromRow;

        for (int step = 0; step < 2000; step++) {
            if (Level.isWall(grid[currentRow][currentColumn])
                    || Level.isPropSolid(grid[currentRow][currentColumn])) {
                grid[currentRow][currentColumn] = 'l';
            }
            if (currentColumn == toColumn && currentRow == toRow) return;

            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_CAVE_TUNNEL_STRAIGHTNESS) {
                int differenceColumn = toColumn - currentColumn;
                int differenceRow    = toRow    - currentRow;
                if (Math.abs(differenceColumn) > Math.abs(differenceRow)) {
                    currentColumn += Integer.signum(differenceColumn);
                } else if (differenceRow != 0) {
                    currentRow += Integer.signum(differenceRow);
                } else {
                    currentColumn += Integer.signum(differenceColumn);
                }
            } else {
                int direction      = random.nextInt(4);
                int[] deltaColumns = {0, 0, 1, -1};
                int[] deltaRows    = {1, -1, 0, 0};
                currentColumn += deltaColumns[direction];
                currentRow    += deltaRows[direction];
            }
            currentColumn = Math.max(1, Math.min(LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 2, currentColumn));
            currentRow    = Math.max(1, Math.min(LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 2, currentRow));
        }
    }

    // -------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------

    private Level buildFallbackLevel() {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        char[][] grid  = new char[gridHeight][gridWidth];
        int centerColumn = gridWidth  / 2;
        int centerRow    = gridHeight / 2;
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                grid[tileRow][tileColumn] = 'x';
            }
        }
        for (int tileRow = centerRow - 3; tileRow <= centerRow + 3; tileRow++) {
            for (int tileColumn = centerColumn - 5; tileColumn <= centerColumn + 5; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }
        grid[centerRow][centerColumn] = 'p';
        return new Level(grid, new ArrayList<>(), new ArrayList<>());
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private boolean isWalkableTile(char cell) {
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f';
    }

    private boolean isDecal(char cell) {
        return cell == 'm' || cell == '.' || cell == 'O' || cell == 's' || cell == 'e';
    }

    private boolean isPassableForBFS(char cell) {
        return isWalkableTile(cell) || Level.isDoor(cell) || isDecal(cell)
            || cell == 'p' || cell == RenderConstants.STAIRS_DOWN_CHAR;
    }

    private boolean isInBounds(int tileColumn, int tileRow) {
        return tileColumn >= 0 && tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH
            && tileRow    >= 0 && tileRow    < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
    }

    private int randomBetween(int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) return minInclusive;
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private char randomAmmoChar() {
        switch (random.nextInt(5)) {
            case 0:  return '6';
            case 1:  return '7';
            case 2:  return '8';
            case 3:  return '9';
            default: return '0';
        }
    }

    private ItemType randomWeaponItemType() {
        switch (random.nextInt(5)) {
            case 0:  return ItemType.WEAPON_SHOTGUN;
            case 1:  return ItemType.WEAPON_CHAINGUN;
            case 2:  return ItemType.WEAPON_ASSAULT_RIFLE;
            case 3:  return ItemType.WEAPON_PLASMA;
            default: return ItemType.WEAPON_ROCKET;
        }
    }
}
