package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RenderConstants;

/**
 * DEV-ONLY performance probe (render-performance-full-hd R10-R12): times each {@code World.render} pass on
 * the GL thread, reads LibGDX's {@link GLProfiler} per frame, and reports rolling averages over
 * {@link RenderConstants#PERF_PROBE_WINDOW_FRAMES} frames (plus the worst frame in the window) to the log
 * every {@link RenderConstants#PERF_PROBE_LOG_INTERVAL_SECONDS} and as a two-line readout at the top centre.
 *
 * <p>Off by default. Enabled by {@code -Dtoon3d.perfProbe=cpu} (timing only) or {@code =sync} (a
 * {@code glFinish} after every pass, so GPU work is charged to the pass that queued it — this inflates the
 * total and is for attribution only), or by {@link RenderConstants#PERF_PROBE_FORCE_ON} for a phone build.
 * When off, every method is one boolean check: no profiler, no {@code nanoTime}, no GPU objects, no text.
 *
 * <p>It is a measuring instrument, not a test, and never enters the test suite. Nothing allocates per frame
 * while on, except the one log String every few seconds.
 */
public final class RenderPerformanceProbe implements Disposable {

    /** The World.render passes the probe brackets, in draw order. */
    public enum Pass {
        FLOOR_CEILING("floor"),
        WALLS("walls"),
        PROPS("props"),
        SHOP_MACHINE("shop"),
        ENEMIES("enemies"),
        VIGNETTE("vignette"),
        WEAPON("weapon"),
        SCREEN_EFFECTS("screenfx"),
        MINIMAP("minimap"),
        HUD("hud"),
        OVERLAYS("overlays");

        private final String logLabel;

        Pass(String logLabel) {
            this.logLabel = logLabel;
        }
    }

    private static final Pass[] PASSES = Pass.values();
    private static final int WINDOW = RenderConstants.PERF_PROBE_WINDOW_FRAMES;

    private final boolean enabled;
    private final boolean syncMode;

    // Everything below exists only when enabled.
    private GLProfiler  profiler;
    private SpriteBatch batch;
    private BitmapFont  font;
    private Texture     plateTexture;
    private final StringBuilder readoutBuilder = new StringBuilder(RenderConstants.PERF_PROBE_READOUT_CAPACITY);
    private final StringBuilder logBuilder     = new StringBuilder(RenderConstants.PERF_PROBE_LOG_CAPACITY);

    private long frameStartNanos;
    private final long[] passStartNanos     = new long[PASSES.length];
    private final int[]  passStartDrawCalls = new int[PASSES.length];

    // Ring buffers over the window: frame ms, per-pass ms, per-pass draw calls, and per-frame GL counters.
    private final float[] frameMillisRing      = new float[WINDOW];
    private final float[] passMillisRing       = new float[PASSES.length * WINDOW];
    private final int[]   passDrawCallsRing    = new int[PASSES.length * WINDOW];
    private final int[]   drawCallsRing        = new int[WINDOW];
    private final int[]   textureBindingsRing  = new int[WINDOW];
    private final int[]   shaderSwitchesRing   = new int[WINDOW];
    private final float[] vertexCountRing      = new float[WINDOW];
    private final long[]  uploadBytesRing      = new long[WINDOW];
    // Per-frame scratch, folded into the rings at endFrame.
    private final float[] framePassMillis      = new float[PASSES.length];
    private final int[]   framePassDrawCalls   = new int[PASSES.length];
    private long  frameUploadBytes;
    private int   ringIndex;
    private int   ringFilled;
    private float secondsSinceLog;
    private float secondsSinceReadout = Float.MAX_VALUE;

    public RenderPerformanceProbe() {
        String mode = System.getProperty(RenderConstants.PERF_PROBE_PROPERTY_KEY);
        boolean sync = RenderConstants.PERF_PROBE_MODE_SYNC.equalsIgnoreCase(mode);
        boolean cpu  = RenderConstants.PERF_PROBE_MODE_CPU.equalsIgnoreCase(mode);
        this.enabled  = RenderConstants.PERF_PROBE_FORCE_ON || cpu || sync;
        this.syncMode = sync;
        if (!enabled) return;

        profiler = new GLProfiler(Gdx.graphics);
        profiler.enable();
        batch = new SpriteBatch(RenderConstants.PERF_PROBE_BATCH_SIZE);
        font  = GameFonts.newFont(FontFace.REGULAR);
        font.getData().setScale(RenderConstants.PERF_PROBE_TEXT_SCALE);
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(1f, 1f, 1f, 1f);
        pixel.fill();
        plateTexture = new Texture(pixel);
        pixel.dispose();
        Gdx.app.log(RenderConstants.PERF_PROBE_LOG_TAG, "probe on, mode " + (syncMode ? "sync" : "cpu"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Marks the start of a World.render frame and resets the GL counters. */
    public void beginFrame() {
        if (!enabled) return;
        if (syncMode) Gdx.gl.glFinish();
        profiler.reset();
        frameUploadBytes = 0L;
        for (int passIndex = 0; passIndex < PASSES.length; passIndex++) {
            framePassMillis[passIndex]    = 0f;
            framePassDrawCalls[passIndex] = 0;
        }
        frameStartNanos = System.nanoTime();
    }

    public void begin(Pass pass) {
        if (!enabled) return;
        passStartDrawCalls[pass.ordinal()] = profiler.getDrawCalls();
        passStartNanos[pass.ordinal()]     = System.nanoTime();
    }

    /** Ends a pass; a pass may be bracketed more than once a frame (its times add up). */
    public void end(Pass pass) {
        if (!enabled) return;
        if (syncMode) Gdx.gl.glFinish();
        int passIndex = pass.ordinal();
        framePassMillis[passIndex]    += (System.nanoTime() - passStartNanos[passIndex]) / 1_000_000f;
        framePassDrawCalls[passIndex] += profiler.getDrawCalls() - passStartDrawCalls[passIndex];
    }

    /** Bytes a renderer uploaded to the GPU this frame (the floor backdrop, until the span pipeline). */
    public void addUploadBytes(long bytes) {
        if (!enabled) return;
        frameUploadBytes += bytes;
    }

    /**
     * Closes the frame: folds it into the window, logs every few seconds, and draws the readout (whose own
     * draw calls are outside the measured frame, because the next beginFrame resets the counters).
     */
    public void endFrame(OrthographicCamera camera) {
        if (!enabled) return;
        if (syncMode) Gdx.gl.glFinish();
        float frameMillis = (System.nanoTime() - frameStartNanos) / 1_000_000f;

        frameMillisRing[ringIndex]     = frameMillis;
        drawCallsRing[ringIndex]       = profiler.getDrawCalls();
        textureBindingsRing[ringIndex] = profiler.getTextureBindings();
        shaderSwitchesRing[ringIndex]  = profiler.getShaderSwitches();
        vertexCountRing[ringIndex]     = profiler.getVertexCount().total;
        uploadBytesRing[ringIndex]     = frameUploadBytes;
        for (int passIndex = 0; passIndex < PASSES.length; passIndex++) {
            passMillisRing[passIndex * WINDOW + ringIndex]    = framePassMillis[passIndex];
            passDrawCallsRing[passIndex * WINDOW + ringIndex] = framePassDrawCalls[passIndex];
        }
        ringIndex = (ringIndex + 1) % WINDOW;
        if (ringFilled < WINDOW) ringFilled++;

        float deltaTime = Gdx.graphics.getDeltaTime();
        secondsSinceLog += deltaTime;
        if (secondsSinceLog >= RenderConstants.PERF_PROBE_LOG_INTERVAL_SECONDS) {
            secondsSinceLog = 0f;
            writeLogLine();
        }
        secondsSinceReadout += deltaTime;
        if (secondsSinceReadout >= RenderConstants.PERF_PROBE_READOUT_REFRESH_SECONDS) {
            secondsSinceReadout = 0f;
            rebuildReadout();
        }
        drawReadout(camera);
    }

    private float averageFrameMillis() {
        return GameMath.rollingAverage(frameMillisRing, 0, ringFilled);
    }

    private float worstFrameMillis() {
        float worst = 0f;
        for (int index = 0; index < ringFilled; index++) worst = Math.max(worst, frameMillisRing[index]);
        return worst;
    }

    private float averagePassMillis(Pass pass) {
        return GameMath.rollingAverage(passMillisRing, pass.ordinal() * WINDOW, ringFilled);
    }

    private float averagePassDrawCalls(Pass pass) {
        long sum = 0L;
        int offset = pass.ordinal() * WINDOW;
        for (int index = 0; index < ringFilled; index++) sum += passDrawCallsRing[offset + index];
        return ringFilled == 0 ? 0f : sum / (float) ringFilled;
    }

    private static float averageOf(int[] ring, int count) {
        long sum = 0L;
        for (int index = 0; index < count; index++) sum += ring[index];
        return count == 0 ? 0f : sum / (float) count;
    }

    private static float averageOf(long[] ring, int count) {
        long sum = 0L;
        for (int index = 0; index < count; index++) sum += ring[index];
        return count == 0 ? 0f : sum / (float) count;
    }

    private void rebuildReadout() {
        float averageMillis = averageFrameMillis();
        readoutBuilder.setLength(0);
        readoutBuilder.append(RenderConstants.PERF_PROBE_LABEL_AVERAGE);
        appendFixed(readoutBuilder, averageMillis);
        readoutBuilder.append(RenderConstants.PERF_PROBE_LABEL_WORST);
        appendFixed(readoutBuilder, worstFrameMillis());
        readoutBuilder.append(RenderConstants.PERF_PROBE_UNIT_MILLISECONDS);
        readoutBuilder.append(RenderConstants.PERF_PROBE_LABEL_FPS);
        appendFixed(readoutBuilder, averageMillis > 0f ? 1000f / averageMillis : 0f);
        readoutBuilder.append('\n');
        readoutBuilder.append(RenderConstants.PERF_PROBE_LABEL_FLOOR);
        appendFixed(readoutBuilder, averagePassMillis(Pass.FLOOR_CEILING));
        readoutBuilder.append(RenderConstants.PERF_PROBE_LABEL_WALLS);
        appendFixed(readoutBuilder, averagePassMillis(Pass.WALLS));
        readoutBuilder.append(RenderConstants.PERF_PROBE_LABEL_DRAW_CALLS);
        appendFixed(readoutBuilder, averageOf(drawCallsRing, ringFilled));
    }

    private void writeLogLine() {
        logBuilder.setLength(0);
        logBuilder.append(syncMode ? "sync" : "cpu");
        logBuilder.append(" frames ").append(ringFilled);
        logBuilder.append(" | frame avg ");
        appendFixed(logBuilder, averageFrameMillis());
        logBuilder.append(" worst ");
        appendFixed(logBuilder, worstFrameMillis());
        logBuilder.append(" ms | pass ms");
        for (Pass pass : PASSES) {
            logBuilder.append(' ').append(pass.logLabel).append('=');
            appendFixed(logBuilder, averagePassMillis(pass));
        }
        logBuilder.append(" | pass dc");
        for (Pass pass : PASSES) {
            logBuilder.append(' ').append(pass.logLabel).append('=');
            appendFixed(logBuilder, averagePassDrawCalls(pass));
        }
        logBuilder.append(" | draws ");
        appendFixed(logBuilder, averageOf(drawCallsRing, ringFilled));
        logBuilder.append(" binds ");
        appendFixed(logBuilder, averageOf(textureBindingsRing, ringFilled));
        logBuilder.append(" shaders ");
        appendFixed(logBuilder, averageOf(shaderSwitchesRing, ringFilled));
        logBuilder.append(" verts ");
        appendFixed(logBuilder, GameMath.rollingAverage(vertexCountRing, 0, ringFilled));
        logBuilder.append(" upload ");
        appendFixed(logBuilder, averageOf(uploadBytesRing, ringFilled));
        logBuilder.append(" B/frame");
        Gdx.app.log(RenderConstants.PERF_PROBE_LOG_TAG, logBuilder.toString());
    }

    private void drawReadout(OrthographicCamera camera) {
        float plateWidth  = RenderConstants.PERF_PROBE_PLATE_WIDTH;
        float plateHeight = RenderConstants.PERF_PROBE_PLATE_HEIGHT;
        float plateX = (Constants.WORLD_WIDTH - plateWidth) / 2f;
        float plateY = Constants.WORLD_HEIGHT - RenderConstants.PERF_PROBE_PLATE_TOP_MARGIN - plateHeight;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE,
                       RenderConstants.PERF_PROBE_PLATE_ALPHA);
        batch.draw(plateTexture, plateX, plateY, plateWidth, plateHeight);
        batch.setColor(1f, 1f, 1f, 1f);
        font.setColor(ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                      ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
        float padding = RenderConstants.PERF_PROBE_TEXT_PADDING;
        font.draw(batch, readoutBuilder, plateX + padding, plateY + plateHeight - padding);
        batch.end();
    }

    /** Appends value with PERF_PROBE_DECIMALS fixed decimals, without allocating. */
    private static void appendFixed(StringBuilder builder, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            builder.append('-');
            return;
        }
        int decimals = RenderConstants.PERF_PROBE_DECIMALS;
        long scale = 1L;
        for (int digit = 0; digit < decimals; digit++) scale *= 10L;
        long scaled = Math.round(Math.abs(value) * scale);
        if (value < 0f && scaled != 0L) builder.append('-');
        builder.append(scaled / scale);
        if (decimals > 0) {
            builder.append('.');
            long fraction = scaled % scale;
            for (long divisor = scale / 10L; divisor > 0L; divisor /= 10L) {
                builder.append((char) ('0' + (fraction / divisor) % 10L));
            }
        }
    }

    @Override
    public void dispose() {
        if (!enabled) return;
        // Restore the real GL before the next World builds its own probe (a second enable would wrap ours).
        profiler.disable();
        batch.dispose();
        font.dispose();
        plateTexture.dispose();
    }
}
