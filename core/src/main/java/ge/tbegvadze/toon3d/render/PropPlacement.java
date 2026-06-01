package ge.tbegvadze.toon3d.render;

/** Immutable record of a single prop tile — position, type, and precomputed world centre. */
final class PropPlacement {
    final int   tileColumn;
    final int   tileRow;
    final char  propChar;
    final float worldCenterX;
    final float worldCenterY;

    PropPlacement(int tileColumn, int tileRow, char propChar,
                  float worldCenterX, float worldCenterY) {
        this.tileColumn   = tileColumn;
        this.tileRow      = tileRow;
        this.propChar     = propChar;
        this.worldCenterX = worldCenterX;
        this.worldCenterY = worldCenterY;
    }
}
