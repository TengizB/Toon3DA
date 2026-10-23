package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The ARCHITECTURE facade a {@link RoomBlueprint#build(RoomBuildContext)} writes through (level-generation-overhaul):
 * one {@link LayoutRoom} (its sockets already opened), the floor grid and the layout's seeded stream. A build stamps
 * ARCHITECTURE only — floor lighting, the observatory rotunda, the research lab's alcove wall, a standard room's
 * pillar slots — and exports FEATURE slots for the room's kit; every prop is then placed by the
 * {@link RoomDresser}. Architecture never touches a door clearance zone or a shell (R9). Headless — no LibGDX.
 */
public final class RoomBuildContext {

    private static final int[] STEP_COLUMNS = { 0, 0, 1, -1 };
    private static final int[] STEP_ROWS    = { 1, -1, 0, 0 };

    private final char[][]         grid;
    private final LayoutRoom       room;
    private final Random           random;
    private final LevelGenConfig   config;
    private final List<LayoutRoom> rooms;

    /**
     * @param grid   the floor grid ({@code grid[row][column]}); the room's interior is carved, its sockets open
     * @param room   the room being built
     * @param random the layout's seeded stream
     * @param config the floor's config (standard floor lighting flags)
     * @param rooms  every room on the floor (door clearance zones)
     */
    public RoomBuildContext(char[][] grid, LayoutRoom room, Random random, LevelGenConfig config, List<LayoutRoom> rooms) {
        this.grid   = grid;
        this.room   = room;
        this.random = random;
        this.config = config != null ? config : new LevelGenConfig();
        this.rooms  = rooms;
    }

    public LayoutRoom room() {
        return room;
    }

    private boolean isReservedForDoor(int tileColumn, int tileRow) {
        return DoorwayRules.isInDoorClearance(grid, rooms, tileColumn, tileRow);
    }

    // -------------------------------------------------------------------------
    // Floor lighting (one method per look; each blueprint row names its own — never a switch)
    // -------------------------------------------------------------------------

    /** A generic room: mostly lit, with unlit / normal / (when the config allows) flickering patches. */
    public void standardFloor() {
        int flickerBudget = config.flickeringFloors ? 2 + random.nextInt(2) : 0;
        for (int[] tile : litInterior()) {
            float roll = random.nextFloat();
            if (config.flickeringFloors && roll < 0.05f && flickerBudget > 0) {
                set(tile, 'f');
                flickerBudget--;
            } else if (config.unlitFloors && roll < 0.10f) {
                set(tile, 'u');
            } else if (config.normalFloors && roll < 0.35f) {
                set(tile, 'l');
            }
        }
    }

    /** Server room: dark 'u' dominant, a sprinkle of flicker, no bright floor. */
    public void serverRoomFloor() {
        int flickerBudget = 2;
        for (int[] tile : litInterior()) {
            float roll = random.nextFloat();
            if (roll < LevelGenConstants.LEVEL_GEN_SERVER_FLICKER_CHANCE && flickerBudget > 0) {
                set(tile, 'f');
                flickerBudget--;
            } else {
                set(tile, roll < 0.55f ? 'u' : 'l');
            }
        }
    }

    /** Armory: mostly lit, dark corners. */
    public void armoryFloor() {
        for (int[] tile : litInterior()) {
            boolean corner = (tile[0] <= room.leftColumn + 2 || tile[0] >= room.rightColumn - 2)
                          && (tile[1] <= room.bottomRow + 2 || tile[1] >= room.topRow - 2);
            if (corner && random.nextFloat() < 0.60f) set(tile, 'u');
            else if (random.nextFloat() < 0.12f) set(tile, 'l');
        }
    }

    /** Cryo chamber: dim 'l' dominant with rare dark patches. */
    public void cryoChamberFloor() {
        for (int[] tile : litInterior()) set(tile, random.nextFloat() < 0.15f ? 'u' : 'l');
    }

    /** Power plant: dark floor, flicker near the centre (the reactor hum). */
    public void powerPlantFloor() {
        int flickerBudget = 4;
        for (int[] tile : litInterior()) {
            boolean nearCentre = Math.abs(tile[0] - room.centerColumn()) <= 2 && Math.abs(tile[1] - room.centerRow()) <= 2;
            if (nearCentre && flickerBudget > 0 && random.nextFloat() < 0.40f) {
                set(tile, 'f');
                flickerBudget--;
            } else {
                set(tile, 'u');
            }
        }
    }

    /** Command center: lit, a thin normal ring at the perimeter. */
    public void commandCenterFloor() {
        for (int[] tile : litInterior()) {
            boolean edge = tile[0] == room.leftColumn + 1 || tile[0] == room.rightColumn - 1
                        || tile[1] == room.bottomRow + 1 || tile[1] == room.topRow - 1;
            if (edge && random.nextFloat() < 0.35f) set(tile, 'l');
        }
    }

    /** Containment block: very dark, a few flickering cell fronts. */
    public void containmentBlockFloor() {
        int flickerBudget = 3;
        for (int[] tile : litInterior()) {
            if (random.nextFloat() < 0.08f && flickerBudget > 0) {
                set(tile, 'f');
                flickerBudget--;
            } else {
                set(tile, 'u');
            }
        }
    }

    /** Gore nest: mostly unlit, a few flickering seams. */
    public void goreNestFloor() {
        int flickerBudget = 3;
        for (int[] tile : litInterior()) {
            float roll = random.nextFloat();
            if (roll < LevelGenConstants.LEVEL_GEN_GORE_NEST_FLICKER_CHANCE && flickerBudget > 0) {
                set(tile, 'f');
                flickerBudget--;
            } else {
                set(tile, roll < LevelGenConstants.LEVEL_GEN_GORE_NEST_DARK_FLOOR_CHANCE ? 'u' : 'l');
            }
        }
    }

    /** Atmospheric plant: dim with strobing failing hazard lights. */
    public void atmosphericPlantFloor() {
        int flickerBudget = 4;
        for (int[] tile : litInterior()) {
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ATMO_PLANT_SCORCH_CHANCE && flickerBudget > 0) {
                set(tile, 'f');
                flickerBudget--;
            } else {
                set(tile, 'l');
            }
        }
    }

    /** Salvage bay: dingy 'l' with dark patches. */
    public void salvageBayFloor() {
        for (int[] tile : litInterior()) {
            set(tile, random.nextFloat() < LevelGenConstants.LEVEL_GEN_SALVAGE_DARK_FLOOR_CHANCE ? 'u' : 'l');
        }
    }

    // -------------------------------------------------------------------------
    // Feature architecture
    // -------------------------------------------------------------------------

    /**
     * A standard room's PILLAR slots (the BARE kit's two 'P', R25): two tiles symmetric about the room's main
     * lane — the axis through the centre from the primary socket — a quarter of the way in, at least two tiles
     * off the lane. Exported only when the interior is at least 7 x 7; the dresser places them under R18.
     */
    public void exportPillarSlots() {
        if (!config.columns) return;
        if (room.interiorWidth() < LevelGenConstants.LEVEL_GEN_CENTRE_ROW_MIN_INTERIOR
                || room.interiorHeight() < LevelGenConstants.LEVEL_GEN_CENTRE_ROW_MIN_INTERIOR) return;
        DoorSocket primary = room.primarySocket();
        boolean laneVertical = primary == null || primary.side.isHorizontalWall();
        List<int[]> slots = new ArrayList<>();
        if (laneVertical) {
            int offset = Math.max(2, room.interiorWidth() / 4);
            slots.add(new int[]{ room.centerColumn() - offset, room.centerRow() });
            slots.add(new int[]{ room.centerColumn() + offset, room.centerRow() });
        } else {
            int offset = Math.max(2, room.interiorHeight() / 4);
            slots.add(new int[]{ room.centerColumn(), room.centerRow() - offset });
            slots.add(new int[]{ room.centerColumn(), room.centerRow() + offset });
        }
        room.exportFeature(RoomBlueprints.FEATURE_PILLARS, slots);
    }

    /**
     * The research lab's force-field wall stub (AS22): a short wall across a third of the room, two thirds of the
     * way up, which makes an ALCOVE behind it for the AI core. Wall tiles skip door clearance zones; the alcove
     * (the tiles just above the stub) is exported as FEATURE_LAB_ALCOVE.
     */
    public void researchLabAlcove() {
        if (room.interiorHeight() < 4 || room.interiorWidth() < 5) return;
        int barrierRow   = room.bottomRow + room.interiorHeight() * 2 / 3;
        int barrierStart = room.leftColumn + 2;
        int barrierLength = Math.min(3, room.interiorWidth() - 2);
        List<int[]> alcove = new ArrayList<>();
        for (int tileColumn = barrierStart; tileColumn < barrierStart + barrierLength; tileColumn++) {
            if (!room.containsInterior(tileColumn, barrierRow) || isReservedForDoor(tileColumn, barrierRow)) continue;
            if (isReservedForDoor(tileColumn, barrierRow + 1)) continue;
            grid[barrierRow][tileColumn] = 'x';
            if (room.containsInterior(tileColumn, barrierRow + 1)) alcove.add(new int[]{ tileColumn, barrierRow + 1 });
        }
        room.exportFeature(RoomBlueprints.FEATURE_LAB_ALCOVE, alcove);
    }

    /**
     * The stellar observatory's rotunda (stellar-observatory-gravity-well-room): a true circle rasterised by
     * {@link GameMath#classifyRotundaTile} inside the square interior (everything outside the circle sealed to
     * wall), a passage from every socket's inner tile to the circle, the catwalk ring '?', four cardinal spokes
     * '-' and a little far-quadrant dread lighting. The core tile is CLAIMED as the room's centre (the HUB is its
     * ring) and exported with the pylon slots beside the spokes, the open outer annulus and its shell band.
     */
    public void stellarObservatoryRotunda() {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        int rawRadius = Math.min(room.interiorWidth(), room.interiorHeight()) / 2
                      - LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_RADIUS_MARGIN;
        float radius = Math.max(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_RADIUS,
                        Math.min(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MAX_RADIUS, rawRadius));
        int radiusTiles = Math.round(radius);

        boolean[][] insideCircle = new boolean[grid.length][grid[0].length];
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                GameMath.RotundaTileClass tileClass = GameMath.classifyRotundaTile(
                        tileColumn - centerColumn, tileRow - centerRow, radius);
                if (tileClass == GameMath.RotundaTileClass.FLOOR) {
                    grid[tileRow][tileColumn] = ' ';
                    insideCircle[tileRow][tileColumn] = true;
                } else {
                    grid[tileRow][tileColumn] = 'x';
                }
            }
        }
        // A passage from every socket into the circle: the shortest 4-connected interior path, carved to floor.
        for (DoorSocket socket : room.sockets()) carvePassageToCircle(socket, insideCircle);

        int catwalkRadius = Math.round(radius * LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CATWALK_RATIO);
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (!insideCircle[tileRow][tileColumn] || grid[tileRow][tileColumn] != ' ') continue;
                if (GameMath.isOnCatwalkAnnulus(tileColumn - centerColumn, tileRow - centerRow, catwalkRadius,
                        LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CATWALK_BAND_HALF_WIDTH)) {
                    grid[tileRow][tileColumn] = '?';
                }
            }
        }
        List<int[]> core = new ArrayList<>();
        core.add(new int[]{ centerColumn, centerRow });
        grid[centerRow][centerColumn] = ' ';
        room.claimCentre(core);
        room.exportFeature(RoomBlueprints.FEATURE_OBSERVATORY_CORE, core);

        List<int[]> pylonSlots = new ArrayList<>();
        int[][] spokes = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] direction : spokes) {
            for (int distance = catwalkRadius + 1; distance <= radiusTiles - 1; distance++) {
                int tileColumn = centerColumn + direction[0] * distance;
                int tileRow    = centerRow + direction[1] * distance;
                if (!room.containsInterior(tileColumn, tileRow) || grid[tileRow][tileColumn] != ' ') continue;
                grid[tileRow][tileColumn] = '-';
                // The pylon slots flank the spoke (perpendicular neighbours), mid-way out.
                if (distance == (catwalkRadius + radiusTiles) / 2) {
                    pylonSlots.add(new int[]{ tileColumn + direction[1], tileRow + direction[0] });
                    pylonSlots.add(new int[]{ tileColumn - direction[1], tileRow - direction[0] });
                }
            }
        }
        room.exportFeature(RoomBlueprints.FEATURE_OBSERVATORY_SPOKES, pylonSlots);

        float innerBound = catwalkRadius + LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PROP_CLEARANCE;
        float outerBound = radius - LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PROP_CLEARANCE;
        List<int[]> annulus = new ArrayList<>();
        List<int[]> shellBand = new ArrayList<>();
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (!insideCircle[tileRow][tileColumn] || grid[tileRow][tileColumn] != ' ') continue;
                double distance = Math.hypot(tileColumn - centerColumn, tileRow - centerRow);
                if (distance <= innerBound) continue;
                if (distance < outerBound) annulus.add(new int[]{ tileColumn, tileRow });
                if (touchesWall(tileColumn, tileRow)) shellBand.add(new int[]{ tileColumn, tileRow });
            }
        }
        room.exportFeature(RoomBlueprints.FEATURE_OBSERVATORY_ANNULUS, annulus);
        room.exportFeature(RoomBlueprints.FEATURE_OBSERVATORY_SHELL_BAND, shellBand);

        // Sparse dread lighting: up to UNLIT_MAX annulus tiles far (in bearing) from the primary entrance.
        DoorSocket primary = room.primarySocket();
        if (primary == null || annulus.isEmpty()) return;
        float entranceBearing = (float) Math.atan2(primary.tileRow - centerRow, primary.tileColumn - centerColumn);
        int unlitTarget = random.nextInt(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_UNLIT_MAX + 1);
        for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PROP_PLACEMENT_ATTEMPTS
                && unlitTarget > 0; attempt++) {
            int[] tile = annulus.get(random.nextInt(annulus.size()));
            if (grid[tile[1]][tile[0]] != ' ') continue;
            float bearing = (float) Math.atan2(tile[1] - centerRow, tile[0] - centerColumn);
            if (GameMath.angularDifferenceRadians(bearing, entranceBearing)
                    < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_FAR_QUADRANT_RADIANS) continue;
            grid[tile[1]][tile[0]] = 'u';
            unlitTarget--;
        }
    }

    private void carvePassageToCircle(DoorSocket socket, boolean[][] insideCircle) {
        int startColumn = socket.innerColumn();
        int startRow    = socket.innerRow();
        if (!room.containsInterior(startColumn, startRow)) return;
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        long start = key(startColumn, startRow);
        parent.put(start, start);
        queue.add(new int[]{ startColumn, startRow });
        long reached = -1;
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            if (insideCircle[current[1]][current[0]]) {
                reached = key(current[0], current[1]);
                break;
            }
            for (int direction = 0; direction < 4; direction++) {
                int column = current[0] + STEP_COLUMNS[direction], row = current[1] + STEP_ROWS[direction];
                if (!room.containsInterior(column, row)) continue;
                long next = key(column, row);
                if (parent.containsKey(next)) continue;
                parent.put(next, key(current[0], current[1]));
                queue.add(new int[]{ column, row });
            }
        }
        if (reached < 0) return;
        for (long cursor = reached; ; cursor = parent.get(cursor)) {
            int column = (int) cursor, row = (int) (cursor >> 32);
            if (Level.isWall(grid[row][column])) grid[row][column] = ' ';
            if (parent.get(cursor) == cursor) break;
        }
    }

    // -------------------------------------------------------------------------

    /** Interior tiles still lit floor ' ' (row by row) — the tiles a floor-lighting pass may change. */
    private List<int[]> litInterior() {
        List<int[]> tiles = new ArrayList<>();
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] == ' ') tiles.add(new int[]{ tileColumn, tileRow });
            }
        }
        return tiles;
    }

    private void set(int[] tile, char floor) {
        grid[tile[1]][tile[0]] = floor;
    }

    private boolean touchesWall(int tileColumn, int tileRow) {
        for (int direction = 0; direction < 4; direction++) {
            int column = tileColumn + STEP_COLUMNS[direction], row = tileRow + STEP_ROWS[direction];
            if (Level.isWall(grid[row][column])) return true;
        }
        return false;
    }

    private static long key(int tileColumn, int tileRow) {
        return ((long) tileRow << 32) | (tileColumn & 0xFFFFFFFFL);
    }
}
