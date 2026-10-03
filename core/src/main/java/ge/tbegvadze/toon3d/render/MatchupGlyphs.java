package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import ge.tbegvadze.toon3d.enemy.EnemyTrait;
import ge.tbegvadze.toon3d.entity.DamageClass;

/**
 * The procedural matchup glyphs (balance-overhaul order 3, C2/C3/C5): five trait glyphs (hexagon, plate,
 * flame, segmented arc, horned ember — FLESH has none) and eight damage-class glyphs (bullet, spread fan,
 * bolt, rail line, flame, blast star, blade, hammer). No textures.
 *
 * <p>Every glyph is a pre-built list of triangles in a unit box (0..1 on both axes, origin bottom-left,
 * Y up) — built ONCE in the static initialiser, never per frame. Two sinks draw the same data so every
 * renderer shares one figure: a {@link ShapeRenderer} in {@code Filled} mode, or a {@link Batch} through a
 * white-pixel texture (each triangle is sent as a degenerate quad straight into the batch buffer, so the
 * enemy health-bar pass needs no extra {@code begin/end}). The batch path ignores the batch colour and
 * packs the colour into the vertices, and neither path allocates.
 *
 * <p>Not thread-safe: the batch path writes into one shared scratch vertex array.
 */
public final class MatchupGlyphs {

    private static final int   BATCH_FLOATS_PER_VERTEX = 5;   // x, y, packed colour, u, v
    private static final int   BATCH_VERTEX_COUNT      = 4;
    private static final float WHITE_PIXEL_CENTER      = 0.5f;
    private static final int   CIRCLE_SEGMENTS         = 10;
    private static final float TAU                     = (float) (Math.PI * 2.0);

    private static final float[][] TRAIT_TRIANGLES = new float[EnemyTrait.values().length][];
    private static final float[][] CLASS_TRIANGLES = new float[DamageClass.values().length][];

    private final float[] batchScratch = new float[BATCH_FLOATS_PER_VERTEX * BATCH_VERTEX_COUNT];

    static {
        float[] flame = buildFlame();
        TRAIT_TRIANGLES[EnemyTrait.FLESH.ordinal()]    = new float[0];
        TRAIT_TRIANGLES[EnemyTrait.SHIELDED.ordinal()] = buildHexagonRing();
        TRAIT_TRIANGLES[EnemyTrait.PLATED.ordinal()]   = buildPlate();
        TRAIT_TRIANGLES[EnemyTrait.BURNABLE.ordinal()] = flame;
        TRAIT_TRIANGLES[EnemyTrait.CHITIN.ordinal()]   = buildSegmentedArc();
        TRAIT_TRIANGLES[EnemyTrait.INFERNAL.ordinal()] = buildHornedEmber();

        CLASS_TRIANGLES[DamageClass.BALLISTIC.ordinal()] = buildBullet();
        CLASS_TRIANGLES[DamageClass.SPREAD.ordinal()]    = buildSpreadFan();
        CLASS_TRIANGLES[DamageClass.ENERGY.ordinal()]    = buildBolt();
        CLASS_TRIANGLES[DamageClass.RAIL.ordinal()]      = buildRailLine();
        CLASS_TRIANGLES[DamageClass.FIRE.ordinal()]      = flame;
        CLASS_TRIANGLES[DamageClass.EXPLOSIVE.ordinal()] = buildBlastStar();
        CLASS_TRIANGLES[DamageClass.BLADE.ordinal()]     = buildBlade();
        CLASS_TRIANGLES[DamageClass.BLUNT.ordinal()]     = buildHammer();
    }

    // ── ShapeRenderer sink (renderer must be inside begin(Filled)) ────────────

    public void drawClass(ShapeRenderer shapes, DamageClass damageClass, float x, float y, float size,
                          float red, float green, float blue, float alpha) {
        drawTriangles(shapes, CLASS_TRIANGLES[damageClass.ordinal()], x, y, size, red, green, blue, alpha);
    }

    public void drawTrait(ShapeRenderer shapes, EnemyTrait trait, float x, float y, float size,
                          float red, float green, float blue, float alpha) {
        drawTriangles(shapes, TRAIT_TRIANGLES[trait.ordinal()], x, y, size, red, green, blue, alpha);
    }

    // ── Batch sink (batch must be between begin/end; whitePixel is any opaque white texture) ─

    public void drawClass(Batch batch, Texture whitePixel, DamageClass damageClass, float x, float y,
                          float size, float red, float green, float blue, float alpha) {
        drawTriangles(batch, whitePixel, CLASS_TRIANGLES[damageClass.ordinal()], x, y, size,
                red, green, blue, alpha);
    }

    public void drawTrait(Batch batch, Texture whitePixel, EnemyTrait trait, float x, float y,
                          float size, float red, float green, float blue, float alpha) {
        drawTriangles(batch, whitePixel, TRAIT_TRIANGLES[trait.ordinal()], x, y, size,
                red, green, blue, alpha);
    }

    private static void drawTriangles(ShapeRenderer shapes, float[] triangles, float x, float y, float size,
                                      float red, float green, float blue, float alpha) {
        shapes.setColor(red, green, blue, alpha);
        for (int offset = 0; offset < triangles.length; offset += 6) {
            shapes.triangle(
                    x + triangles[offset]     * size, y + triangles[offset + 1] * size,
                    x + triangles[offset + 2] * size, y + triangles[offset + 3] * size,
                    x + triangles[offset + 4] * size, y + triangles[offset + 5] * size);
        }
    }

    private void drawTriangles(Batch batch, Texture whitePixel, float[] triangles, float x, float y,
                               float size, float red, float green, float blue, float alpha) {
        if (triangles.length == 0) return;
        float packedColor = Color.toFloatBits(red, green, blue, alpha);
        float[] scratch = batchScratch;
        for (int offset = 0; offset < triangles.length; offset += 6) {
            writeVertex(scratch, 0, x + triangles[offset]     * size, y + triangles[offset + 1] * size, packedColor);
            writeVertex(scratch, 1, x + triangles[offset + 2] * size, y + triangles[offset + 3] * size, packedColor);
            writeVertex(scratch, 2, x + triangles[offset + 4] * size, y + triangles[offset + 5] * size, packedColor);
            // Fourth vertex repeats the third: a degenerate quad is exactly one triangle.
            writeVertex(scratch, 3, x + triangles[offset + 4] * size, y + triangles[offset + 5] * size, packedColor);
            batch.draw(whitePixel, scratch, 0, scratch.length);
        }
    }

    private static void writeVertex(float[] scratch, int vertexIndex, float x, float y, float packedColor) {
        int base = vertexIndex * BATCH_FLOATS_PER_VERTEX;
        scratch[base]     = x;
        scratch[base + 1] = y;
        scratch[base + 2] = packedColor;
        scratch[base + 3] = WHITE_PIXEL_CENTER;
        scratch[base + 4] = WHITE_PIXEL_CENTER;
    }

    // ── Triangle-list builders (unit box; run once) ───────────────────────────

    /** Growable triangle list used only while building. */
    private static final class Builder {
        private float[] data = new float[96];
        private int length = 0;

        void triangle(float fromX, float fromY, float middleX, float middleY, float toX, float toY) {
            if (length + 6 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            data[length++] = fromX;   data[length++] = fromY;
            data[length++] = middleX; data[length++] = middleY;
            data[length++] = toX;     data[length++] = toY;
        }

        void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
            triangle(x0, y0, x1, y1, x2, y2);
            triangle(x0, y0, x2, y2, x3, y3);
        }

        void rect(float left, float bottom, float right, float top) {
            quad(left, bottom, right, bottom, right, top, left, top);
        }

        /** Annular sector between two radii, from startRadians to endRadians, in {@code steps} slices. */
        void arcBand(float centerX, float centerY, float innerRadius, float outerRadius,
                     float startRadians, float endRadians, int steps) {
            for (int step = 0; step < steps; step++) {
                float angleA = startRadians + (endRadians - startRadians) * step / steps;
                float angleB = startRadians + (endRadians - startRadians) * (step + 1) / steps;
                quad(centerX + innerRadius * cos(angleA), centerY + innerRadius * sin(angleA),
                     centerX + outerRadius * cos(angleA), centerY + outerRadius * sin(angleA),
                     centerX + outerRadius * cos(angleB), centerY + outerRadius * sin(angleB),
                     centerX + innerRadius * cos(angleB), centerY + innerRadius * sin(angleB));
            }
        }

        /** Filled disc (fan) between two angles. */
        void fan(float centerX, float centerY, float radius, float startRadians, float endRadians, int steps) {
            for (int step = 0; step < steps; step++) {
                float angleA = startRadians + (endRadians - startRadians) * step / steps;
                float angleB = startRadians + (endRadians - startRadians) * (step + 1) / steps;
                triangle(centerX, centerY,
                         centerX + radius * cos(angleA), centerY + radius * sin(angleA),
                         centerX + radius * cos(angleB), centerY + radius * sin(angleB));
            }
        }

        float[] build() {
            return java.util.Arrays.copyOf(data, length);
        }
    }

    private static float cos(float radians) { return (float) Math.cos(radians); }
    private static float sin(float radians) { return (float) Math.sin(radians); }

    /** SHIELDED — hexagonal ring (a machine's shield cell). */
    private static float[] buildHexagonRing() {
        Builder builder = new Builder();
        builder.arcBand(0.5f, 0.5f, 0.27f, 0.5f, 0f, TAU, 6);   // 6 slices of a quad band = hexagon
        return builder.build();
    }

    /** PLATED — a solid armour plate with chamfered corners. */
    private static float[] buildPlate() {
        Builder builder = new Builder();
        float chamfer = 0.18f;
        builder.rect(0.06f, 0.06f + chamfer, 0.94f, 0.94f - chamfer);
        builder.rect(0.06f + chamfer, 0.06f, 0.94f - chamfer, 0.94f);
        builder.triangle(0.06f, 0.06f + chamfer, 0.06f + chamfer, 0.06f, 0.06f + chamfer, 0.06f + chamfer);
        builder.triangle(0.94f, 0.06f + chamfer, 0.94f - chamfer, 0.06f, 0.94f - chamfer, 0.06f + chamfer);
        builder.triangle(0.06f, 0.94f - chamfer, 0.06f + chamfer, 0.94f, 0.06f + chamfer, 0.94f - chamfer);
        builder.triangle(0.94f, 0.94f - chamfer, 0.94f - chamfer, 0.94f, 0.94f - chamfer, 0.94f - chamfer);
        return builder.build();
    }

    /** BURNABLE / FIRE — a teardrop flame: round belly with a pointed tip and a flicker spur. */
    private static float[] buildFlame() {
        Builder builder = new Builder();
        builder.fan(0.5f, 0.34f, 0.34f, (float) Math.PI, TAU, 6);                 // lower half of the belly
        builder.rect(0.16f, 0.34f, 0.84f, 0.40f);                                  // belly waist
        builder.triangle(0.16f, 0.40f, 0.84f, 0.40f, 0.52f, 1.00f);               // tip
        builder.triangle(0.16f, 0.40f, 0.30f, 0.40f, 0.14f, 0.72f);               // flicker spur
        return builder.build();
    }

    /** CHITIN — three separated arc segments of a carapace ring (open at the bottom). */
    private static float[] buildSegmentedArc() {
        Builder builder = new Builder();
        float gap = 0.12f;
        float segment = (float) Math.PI * 1.25f / 3f;
        float start = (float) Math.PI * -0.125f;
        for (int segmentIndex = 0; segmentIndex < 3; segmentIndex++) {
            float segmentStart = start + segmentIndex * segment + gap / 2f;
            builder.arcBand(0.5f, 0.42f, 0.26f, 0.5f, segmentStart, segmentStart + segment - gap, 2);
        }
        return builder.build();
    }

    /** INFERNAL — a glowing ember disc crowned with two horns. */
    private static float[] buildHornedEmber() {
        Builder builder = new Builder();
        builder.fan(0.5f, 0.38f, 0.34f, 0f, TAU, CIRCLE_SEGMENTS);
        builder.triangle(0.18f, 0.52f, 0.34f, 0.66f, 0.06f, 1.00f);
        builder.triangle(0.82f, 0.52f, 0.66f, 0.66f, 0.94f, 1.00f);
        return builder.build();
    }

    /** BALLISTIC — a round: straight casing and pointed tip. */
    private static float[] buildBullet() {
        Builder builder = new Builder();
        builder.rect(0.36f, 0.0f, 0.64f, 0.60f);
        builder.triangle(0.36f, 0.60f, 0.64f, 0.60f, 0.50f, 1.0f);
        return builder.build();
    }

    /** SPREAD — three pellet wedges fanning from one point. */
    private static float[] buildSpreadFan() {
        Builder builder = new Builder();
        float apexX = 0.5f;
        float apexY = 0.02f;
        float[] angles = {(float) Math.toRadians(55), (float) Math.toRadians(90), (float) Math.toRadians(125)};
        float halfWidth = 0.09f;
        for (float angle : angles) {
            float tipX = apexX + 0.95f * cos(angle);
            float tipY = apexY + 0.95f * sin(angle);
            float sideX = -sin(angle) * halfWidth;
            float sideY = cos(angle) * halfWidth;
            builder.triangle(apexX, apexY, tipX + sideX, tipY + sideY, tipX - sideX, tipY - sideY);
        }
        return builder.build();
    }

    /** ENERGY — a lightning bolt. */
    private static float[] buildBolt() {
        Builder builder = new Builder();
        builder.triangle(0.60f, 1.00f, 0.10f, 0.45f, 0.55f, 0.60f);
        builder.triangle(0.10f, 0.45f, 0.45f, 0.45f, 0.55f, 0.60f);
        builder.triangle(0.45f, 0.45f, 0.90f, 0.60f, 0.55f, 0.60f);
        builder.triangle(0.45f, 0.45f, 0.35f, 0.00f, 0.90f, 0.60f);
        return builder.build();
    }

    /** RAIL — a long thin slug with an arrowhead. */
    private static float[] buildRailLine() {
        Builder builder = new Builder();
        builder.rect(0.0f, 0.42f, 0.74f, 0.58f);
        builder.triangle(0.64f, 0.14f, 1.0f, 0.50f, 0.64f, 0.86f);
        return builder.build();
    }

    /** EXPLOSIVE — an eight-point blast star. */
    private static float[] buildBlastStar() {
        Builder builder = new Builder();
        int points = 8;
        for (int pointIndex = 0; pointIndex < points; pointIndex++) {
            float tipAngle = TAU * pointIndex / points;
            float leftAngle = tipAngle - TAU / (points * 2f);
            float rightAngle = tipAngle + TAU / (points * 2f);
            float innerRadius = 0.22f;
            builder.triangle(0.5f + innerRadius * cos(leftAngle), 0.5f + innerRadius * sin(leftAngle),
                             0.5f + 0.5f * cos(tipAngle), 0.5f + 0.5f * sin(tipAngle),
                             0.5f + innerRadius * cos(rightAngle), 0.5f + innerRadius * sin(rightAngle));
        }
        builder.fan(0.5f, 0.5f, 0.23f, 0f, TAU, CIRCLE_SEGMENTS);
        return builder.build();
    }

    /** BLADE — a diagonal blade with a crossguard and grip. */
    private static float[] buildBlade() {
        Builder builder = new Builder();
        builder.triangle(0.30f, 0.52f, 0.52f, 0.30f, 0.98f, 0.98f);               // blade
        builder.quad(0.20f, 0.40f, 0.40f, 0.20f, 0.46f, 0.26f, 0.26f, 0.46f);     // crossguard
        builder.quad(0.04f, 0.14f, 0.14f, 0.04f, 0.30f, 0.20f, 0.20f, 0.30f);     // grip
        return builder.build();
    }

    /** BLUNT — a hammer: wide head on a haft. */
    private static float[] buildHammer() {
        Builder builder = new Builder();
        builder.rect(0.10f, 0.62f, 0.90f, 0.96f);
        builder.rect(0.42f, 0.0f, 0.58f, 0.62f);
        return builder.build();
    }
}
