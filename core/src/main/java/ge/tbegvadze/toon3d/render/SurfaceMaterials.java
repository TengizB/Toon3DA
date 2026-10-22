package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import ge.tbegvadze.toon3d.tileset.RegionWallSet;
import ge.tbegvadze.toon3d.tileset.RegionWallSets;
import ge.tbegvadze.toon3d.util.TilesetConstants;

/**
 * The floor/ceiling surface materials (generated-wall-art order 2, decision D10): every region wall set ships its
 * own 64x64 floor and ceiling (generated with the enemy-art kit, guide section 11.3), so the material registry
 * holds exactly two entries per registered set id — {@link #floorMaterialId(String)} and
 * {@link #ceilingMaterialId(String)} — and a lighting mood binds the surfaces of its region's set
 * (LightingMoods). An alias set (regions B-E until orders 3-4) loads the set it borrows.
 *
 * <p>Each loader reads the shipped PNG once (lazily, on the GL thread at floor build) and converts its top-left
 * image origin to the {@link SurfaceMaterialGenerator} layout: row 0 = the SOUTH edge, column 0 = the WEST edge.
 * The GPU pipeline (FloorCeilingRenderer, SurfaceMaterialTextures, the mips and bands) is unchanged; only its
 * texels come from the set. R7: a surface that fails to load falls back to set A's.
 */
public final class SurfaceMaterials {

    private static final SurfaceMaterialRegistry REGISTRY = new SurfaceMaterialRegistry();
    private static boolean bootstrapped = false;

    private SurfaceMaterials() {}

    /** The shared material registry. */
    public static SurfaceMaterialRegistry registry() {
        return REGISTRY;
    }

    /** The floor material id of a region wall set (real or alias). */
    public static String floorMaterialId(String wallSetId) {
        return wallSetId + ".floor";
    }

    /** The ceiling material id of a region wall set (real or alias). */
    public static String ceilingMaterialId(String wallSetId) {
        return wallSetId + ".ceiling";
    }

    /** Registers the floor and ceiling of every registered region wall set. Idempotent. */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        for (String setId : RegionWallSets.registry().ids()) {
            REGISTRY.register(floorMaterialId(setId), size -> loadSurface(setId, true, size));
            REGISTRY.register(ceilingMaterialId(setId), size -> loadSurface(setId, false, size));
        }
        bootstrapped = true;
    }

    private static int[] loadSurface(String setId, boolean floor, int size) {
        RegionWallSet set = RegionWallSets.resolveOrDefault(setId);
        try {
            return readSurface(set, floor, size);
        } catch (RuntimeException failure) {
            if (Gdx.app != null) {
                Gdx.app.error("SurfaceMaterials", "surface of '" + set.id() + "' failed to load ("
                        + failure.getMessage() + ") - using " + TilesetConstants.WALL_SET_DEFAULT);
            }
            return readSurface(RegionWallSets.resolveOrDefault(TilesetConstants.WALL_SET_DEFAULT), floor, size);
        }
    }

    private static int[] readSurface(RegionWallSet set, boolean floor, int size) {
        String path = TilesetConstants.WALL_SET_ASSET_DIRECTORY + (floor ? set.floorFileName() : set.ceilingFileName());
        Pixmap image = new Pixmap(Gdx.files.internal(path));
        try {
            if (image.getWidth() != size || image.getHeight() != size) {
                throw new IllegalStateException(path + " is " + image.getWidth() + "x" + image.getHeight()
                        + ", expected " + size + "x" + size);
            }
            int[] pixels = new int[size * size];
            for (int row = 0; row < size; row++) {
                int imageRow = size - 1 - row;   // image row 0 = north edge; layout row 0 = south edge
                for (int column = 0; column < size; column++) {
                    pixels[row * size + column] = image.getPixel(column, imageRow) | 0xFF;
                }
            }
            return pixels;
        } finally {
            image.dispose();
        }
    }
}
