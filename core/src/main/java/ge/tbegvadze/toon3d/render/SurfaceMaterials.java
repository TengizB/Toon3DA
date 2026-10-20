package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * The one place the floor/ceiling surface materials are registered: ten procedural generators, two per
 * lighting mood (RECIPE C in {@code docs/art-direction.txt}: a new material is ONE {@code register()} line
 * plus one private generator here). Every generator is deterministic (integer hashing through
 * {@link GameMath#combineHash}, never {@code java.util.Random}), tileable at the material size, CPU-only,
 * and sits inside the R9 value band (floor average luminance 0.10-0.24, ceiling 0.06-0.18, pre-shade).
 * Pixel layout and orientation: see {@link SurfaceMaterialGenerator}.
 */
public final class SurfaceMaterials {

    private static final SurfaceMaterialRegistry REGISTRY = new SurfaceMaterialRegistry();
    private static boolean bootstrapped = false;

    private SurfaceMaterials() {}

    /** The shared material registry. */
    public static SurfaceMaterialRegistry registry() {
        return REGISTRY;
    }

    /** Registers the ten materials. Idempotent (a death-to-new-run rebuild is safe). */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        REGISTRY.register(ArtDirection.MATERIAL_ID_DECK_GRATING_STEEL, SurfaceMaterials::deckGratingSteel);
        REGISTRY.register(ArtDirection.MATERIAL_ID_CEILING_STRIP_LIGHTS, SurfaceMaterials::ceilingStripLights);
        REGISTRY.register(ArtDirection.MATERIAL_ID_LAB_TILE_WHITE, SurfaceMaterials::labTileWhite);
        REGISTRY.register(ArtDirection.MATERIAL_ID_CEILING_PANEL_GRID, SurfaceMaterials::ceilingPanelGrid);
        REGISTRY.register(ArtDirection.MATERIAL_ID_REACTOR_GRATE_HOT, SurfaceMaterials::reactorGrateHot);
        REGISTRY.register(ArtDirection.MATERIAL_ID_CEILING_PIPES, SurfaceMaterials::ceilingPipes);
        REGISTRY.register(ArtDirection.MATERIAL_ID_BREACH_FLESH_MEMBRANE, SurfaceMaterials::breachFleshMembrane);
        REGISTRY.register(ArtDirection.MATERIAL_ID_CEILING_TORN_CONDUIT, SurfaceMaterials::ceilingTornConduit);
        REGISTRY.register(ArtDirection.MATERIAL_ID_CORE_CIRCUIT_PLATE, SurfaceMaterials::coreCircuitPlate);
        REGISTRY.register(ArtDirection.MATERIAL_ID_CEILING_LATTICE, SurfaceMaterials::ceilingLattice);
        bootstrapped = true;
    }

    // ------------------------------------------------------------------ helpers

    /** Non-negative deterministic hash of a texel/tile coordinate pair plus a per-use salt. */
    private static int hash(int first, int second, int salt) {
        return (int) (GameMath.combineHash(first + salt * 7919, second ^ (salt * 104729)) >>> 40);
    }

    private static int wrap(int value, int size) {
        return ((value % size) + size) % size;
    }

    private static int packed(int rgb) {
        return (rgb << 8) | 0xFF;
    }

    private static void fill(int[] pixels, int rgb) {
        java.util.Arrays.fill(pixels, packed(rgb));
    }

    private static void put(int[] pixels, int size, int column, int row, int rgb) {
        pixels[wrap(row, size) * size + wrap(column, size)] = packed(rgb);
    }

    private static int adjustValue(int rgb, int amount) {
        int red = clampChannel(((rgb >> 16) & 0xFF) + amount);
        int green = clampChannel(((rgb >> 8) & 0xFF) + amount);
        int blue = clampChannel((rgb & 0xFF) + amount);
        return (red << 16) | (green << 8) | blue;
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // ------------------------------------------------------------------ cool_steel

    private static int[] deckGratingSteel(int size) {
        int[] pixels = new int[size * size];
        fill(pixels, 0x1C2228);
        int plate = size / 2;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                // Diamond-tread bumps: 2x1 px every 4x4, alternate tread rows offset by 2.
                if (row % 4 == 0 && ((column + ((row / 4) & 1) * 2) % 4) < 2) {
                    put(pixels, size, column, row, 0x262E36);
                }
                if (column % plate == 0 || row % plate == 0) {
                    put(pixels, size, column, row, 0x101418);
                }
                int localColumn = column % plate;
                int localRow = row % plate;
                boolean cornerColumn = (localColumn >= 2 && localColumn <= 3) || (localColumn >= plate - 4 && localColumn <= plate - 3);
                boolean cornerRow = (localRow >= 2 && localRow <= 3) || (localRow >= plate - 4 && localRow <= plate - 3);
                if (cornerColumn && cornerRow) {
                    put(pixels, size, column, row, 0x3A444E);
                }
            }
        }
        return pixels;
    }

    private static int[] ceilingStripLights(int size) {
        int[] pixels = new int[size * size];
        fill(pixels, 0x12161C);
        int panel = size / 2;
        int lensWidth = 4;
        int lensLength = 20;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int localColumn = column % panel;
                int localRow = row % panel;
                if (localColumn == 0 || localRow == 0) {
                    put(pixels, size, column, row, 0x0A0D11);
                    continue;
                }
                int lensLeft = (panel - lensWidth) / 2;
                int lensBottom = (panel - lensLength) / 2;
                boolean insideLens = localColumn >= lensLeft && localColumn < lensLeft + lensWidth
                        && localRow >= lensBottom && localRow < lensBottom + lensLength;
                if (insideLens) {
                    boolean rim = localColumn == lensLeft || localColumn == lensLeft + lensWidth - 1
                            || localRow == lensBottom || localRow == lensBottom + lensLength - 1;
                    put(pixels, size, column, row, rim ? 0x2A3038 : 0x56626C);
                }
            }
        }
        return pixels;
    }

    // ------------------------------------------------------------------ clinical_white

    private static int[] labTileWhite(int size) {
        int[] pixels = new int[size * size];
        int tile = 16;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int tileColumn = column / tile;
                int tileRow = row / tile;
                int tileHash = hash(tileColumn, tileRow, 3);
                int rgb;
                if (column % tile == 0 || row % tile == 0) {
                    rgb = 0x22262A;
                } else {
                    rgb = adjustValue(0x3A3F3C, (tileHash % 9) - 4);
                    if (tileHash % 7 == 0) {
                        // Stain blotch: a hashed ellipse inside the tile.
                        int centerColumn = tileColumn * tile + 5 + (tileHash >> 4) % 6;
                        int centerRow = tileRow * tile + 5 + (tileHash >> 9) % 6;
                        int differenceColumn = column - centerColumn;
                        int differenceRow = row - centerRow;
                        if (differenceColumn * differenceColumn + differenceRow * differenceRow <= 16) {
                            rgb = 0x2F3530;
                        }
                    }
                }
                put(pixels, size, column, row, rgb);
            }
        }
        return pixels;
    }

    private static int[] ceilingPanelGrid(int size) {
        int[] pixels = new int[size * size];
        fill(pixels, 0x2A2E2C);
        int panel = size / 2;
        // Recessed light: a slim 20x4 slot (a 24x24 panel would push the average above the ceiling band).
        int lightWidth = 20;
        int lightHeight = 4;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int localColumn = column % panel;
                int localRow = row % panel;
                if (localColumn == 0 || localRow == 0) {
                    put(pixels, size, column, row, 0x16191A);
                    continue;
                }
                int lightLeft = (panel - lightWidth) / 2;
                int lightBottom = (panel - lightHeight) / 2;
                if (localColumn >= lightLeft && localColumn < lightLeft + lightWidth
                        && localRow >= lightBottom && localRow < lightBottom + lightHeight) {
                    boolean rim = localColumn == lightLeft || localColumn == lightLeft + lightWidth - 1
                            || localRow == lightBottom || localRow == lightBottom + lightHeight - 1;
                    put(pixels, size, column, row, rim ? 0x343836 : 0x464C48);
                }
            }
        }
        return pixels;
    }

    // ------------------------------------------------------------------ amber_hot

    private static int[] reactorGrateHot(int size) {
        int[] pixels = new int[size * size];
        // Coarse grating (4 cells per tile): at quarter-resolution backdrop distances an 8-texel
        // period aliases into moire, so the cell is 16 with 6-texel bars and a lifted gap colour.
        int period = 16;
        int barWidth = 6;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int cellColumn = column % period;
                int cellRow = row % period;
                int rgb;
                if (cellColumn < barWidth || cellRow < barWidth) {
                    rgb = 0x2A1E14;
                } else if (cellColumn >= 9 && cellColumn <= 12 && cellRow >= 9 && cellRow <= 12) {
                    rgb = 0x3A1806;
                } else {
                    rgb = 0x1A1008;
                }
                // Hazard chevron band along the south edge: diagonal alternation every 4 texels,
                // kept low-contrast so it reads as paint wear rather than a stripe across the floor.
                if (row < 4) {
                    rgb = (((column + row) / 4) & 1) == 0 ? 0x4A300C : 0x1A1208;
                }
                put(pixels, size, column, row, rgb);
            }
        }
        return pixels;
    }

    private static int[] ceilingPipes(int size) {
        int[] pixels = new int[size * size];
        fill(pixels, 0x140E0A);
        int[] pipeBottomRows = {16, 44};
        int pipeWidth = 6;
        for (int pipeBottom : pipeBottomRows) {
            for (int offset = 0; offset < pipeWidth; offset++) {
                int row = pipeBottom + offset;
                int rgb = offset == pipeWidth - 1 ? 0x3C3024 : 0x2C221A;
                for (int column = 0; column < size; column++) {
                    // Clamp rings every 16, 2 px wide.
                    put(pixels, size, column, row, column % 16 < 2 ? 0x1E1610 : rgb);
                }
            }
        }
        return pixels;
    }

    // ------------------------------------------------------------------ corrupted_violet

    private static int[] breachFleshMembrane(int size) {
        int[] pixels = new int[size * size];
        fill(pixels, 0x2A0E16);
        // Worley cells: one hashed point per 32x32 cell, nearest-two distance on the wrapped plane.
        int cell = size / 2;
        int cellCount = size / cell;
        int pointCount = cellCount * cellCount;
        int[] pointColumns = new int[pointCount];
        int[] pointRows = new int[pointCount];
        for (int cellRow = 0; cellRow < cellCount; cellRow++) {
            for (int cellColumn = 0; cellColumn < cellCount; cellColumn++) {
                int pointIndex = cellRow * cellCount + cellColumn;
                pointColumns[pointIndex] = cellColumn * cell + 4 + hash(cellColumn, cellRow, 11) % (cell - 8);
                pointRows[pointIndex] = cellRow * cell + 4 + hash(cellColumn, cellRow, 12) % (cell - 8);
            }
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float nearest = Float.MAX_VALUE;
                float secondNearest = Float.MAX_VALUE;
                for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                    int differenceColumn = Math.abs(column - pointColumns[pointIndex]);
                    int differenceRow = Math.abs(row - pointRows[pointIndex]);
                    differenceColumn = Math.min(differenceColumn, size - differenceColumn);
                    differenceRow = Math.min(differenceRow, size - differenceRow);
                    float distance = (float) Math.sqrt(differenceColumn * differenceColumn + differenceRow * differenceRow);
                    if (distance < nearest) {
                        secondNearest = nearest;
                        nearest = distance;
                    } else if (distance < secondNearest) {
                        secondNearest = distance;
                    }
                }
                if (secondNearest - nearest < 1.2f) {
                    put(pixels, size, column, row, 0x14060A);
                } else if (hash(column, row, 13) % 60 == 0) {
                    put(pixels, size, column, row, 0x4A1E28);
                } else {
                    // Cells swell towards their nucleus: a soft lift keeps the membrane in the floor value band.
                    put(pixels, size, column, row, adjustValue(0x2A0E16, (int) Math.max(0f, (22f - nearest) / 2f)));
                }
            }
        }
        // Deck-plate fragments: one quadrant in four (hashed), 20x20 in the quadrant's corner.
        for (int cellRow = 0; cellRow < cellCount; cellRow++) {
            for (int cellColumn = 0; cellColumn < cellCount; cellColumn++) {
                if (hash(cellColumn, cellRow, 14) % 4 == 0) {
                    for (int offsetRow = 0; offsetRow < 20; offsetRow++) {
                        for (int offsetColumn = 0; offsetColumn < 20; offsetColumn++) {
                            put(pixels, size, cellColumn * cell + offsetColumn, cellRow * cell + offsetRow, 0x1E1E22);
                        }
                    }
                }
            }
        }
        return pixels;
    }

    private static int[] ceilingTornConduit(int size) {
        int[] pixels = new int[size * size];
        fill(pixels, 0x120810);
        // Hanging conduit lines along Y every 21 texels, 4 px wide.
        for (int row = 0; row < size; row++) {
            for (int lineStart = 0; lineStart < size; lineStart += 21) {
                for (int offset = 0; offset < 4; offset++) {
                    put(pixels, size, lineStart + offset, row, 0x2A1420);
                }
            }
        }
        // Torn panel edges: jagged hashed 1-2 px horizontal runs.
        for (int tearIndex = 0; tearIndex < 6; tearIndex++) {
            int tearRow = hash(tearIndex, 0, 21) % size;
            int tearColumn = hash(tearIndex, 1, 21) % size;
            int tearLength = 10 + hash(tearIndex, 2, 21) % 14;
            for (int step = 0; step < tearLength; step++) {
                int jitter = hash(tearIndex, step, 22) % 3 - 1;
                put(pixels, size, tearColumn + step, tearRow + jitter, 0x08040A);
                if (hash(tearIndex, step, 23) % 2 == 0) {
                    put(pixels, size, tearColumn + step, tearRow + jitter + 1, 0x08040A);
                }
            }
        }
        // Fleshy growth blobs: hashed discs.
        int blobCount = 6;
        int radius = 4;
        for (int blobIndex = 0; blobIndex < blobCount; blobIndex++) {
            int centerColumn = hash(blobIndex, 0, 24) % size;
            int centerRow = hash(blobIndex, 1, 24) % size;
            for (int offsetRow = -radius; offsetRow <= radius; offsetRow++) {
                for (int offsetColumn = -radius; offsetColumn <= radius; offsetColumn++) {
                    if (offsetColumn * offsetColumn + offsetRow * offsetRow <= radius * radius) {
                        put(pixels, size, centerColumn + offsetColumn, centerRow + offsetRow, 0x3A1424);
                    }
                }
            }
        }
        return pixels;
    }

    // ------------------------------------------------------------------ core_white_violet

    private static int[] coreCircuitPlate(int size) {
        int[] pixels = new int[size * size];
        fill(pixels, 0x14121E);
        int grid = 8;
        int gridCount = size / grid;
        int routeCount = 40;
        for (int routeIndex = 0; routeIndex < routeCount; routeIndex++) {
            int startColumn = (hash(routeIndex, 0, 31) % gridCount) * grid + grid / 2;
            int startRow = (hash(routeIndex, 1, 31) % gridCount) * grid + grid / 2;
            int horizontalSteps = (1 + hash(routeIndex, 2, 31) % 4) * grid * (hash(routeIndex, 3, 31) % 2 == 0 ? 1 : -1);
            int verticalSteps = (1 + hash(routeIndex, 4, 31) % 4) * grid * (hash(routeIndex, 5, 31) % 2 == 0 ? 1 : -1);
            int columnDirection = Integer.signum(horizontalSteps);
            int rowDirection = Integer.signum(verticalSteps);
            for (int step = 0; step <= Math.abs(horizontalSteps); step++) {
                put(pixels, size, startColumn + step * columnDirection, startRow, 0x2C2846);
            }
            int cornerColumn = startColumn + horizontalSteps;
            for (int step = 0; step <= Math.abs(verticalSteps); step++) {
                put(pixels, size, cornerColumn, startRow + step * rowDirection, 0x2C2846);
            }
            // Node pads (3x3) at both trace ends.
            stampPad(pixels, size, startColumn, startRow);
            stampPad(pixels, size, cornerColumn, startRow + verticalSteps);
        }
        int plate = size / 2;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (column % plate == 0 || row % plate == 0) {
                    put(pixels, size, column, row, 0x0C0A14);
                }
            }
        }
        return pixels;
    }

    private static void stampPad(int[] pixels, int size, int centerColumn, int centerRow) {
        for (int offsetRow = -1; offsetRow <= 1; offsetRow++) {
            for (int offsetColumn = -1; offsetColumn <= 1; offsetColumn++) {
                put(pixels, size, centerColumn + offsetColumn, centerRow + offsetRow, 0x4A3E78);
            }
        }
    }

    private static int[] ceilingLattice(int size) {
        int[] pixels = new int[size * size];
        fill(pixels, 0x0E0C18);
        int period = 16;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                boolean risingBeam = wrap(column - row, period) < 2;
                boolean fallingBeam = wrap(column + row, period) < 2;
                if (risingBeam || fallingBeam) {
                    put(pixels, size, column, row, 0x24203A);
                }
            }
        }
        // 2x2 node where both beams cross.
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (wrap(column - row, period) < 2 && wrap(column + row, period) < 2) {
                    put(pixels, size, column, row, 0x34304E);
                }
            }
        }
        return pixels;
    }
}
