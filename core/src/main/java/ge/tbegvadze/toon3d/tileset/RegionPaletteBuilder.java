package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.TilesetConstants;

/**
 * Builds a level's palette from its REGION WALL SET (generated-wall-art order 2): every wall symbol of
 * {@link WallSlotTable} is bound to its slot's sprite in the set (variant 0 — the renderer picks a BASE
 * variant per face), and the column symbol 'P' to the set's first column (the renderer picks the floor's
 * column from the set). Every other binding (props, decals) is taken unchanged from {@code base}, so this
 * never changes what a non-wall symbol looks like. Pure; headless.
 */
public final class RegionPaletteBuilder {

    private RegionPaletteBuilder() {}

    /** {@code set} must be a REAL set (resolve aliases through the registry first). */
    public static LevelPalette build(RegionWallSet set, LevelPalette base) {
        LevelPalette.Builder builder = LevelPalette.builder().copyOf(base).wallSetId(set.id());
        String symbols = WallSlotTable.symbols();
        for (int index = 0; index < symbols.length(); index++) {
            char symbol = symbols.charAt(index);
            builder.bind(symbol, TileCategory.WALL,
                    set.resolve(WallSlotTable.groupOf(symbol), WallSlotTable.slotOf(symbol)).get(0));
        }
        builder.bind(TilesetConstants.WALL_SET_COLUMN_SYMBOL, TileCategory.COLUMN, set.columnIds().get(0));
        return builder.build();
    }
}
