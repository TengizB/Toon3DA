package capture;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import ge.tbegvadze.toon3d.Main;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * DEV-ONLY runtime capture driver for toon3D (see .claude/skills/run-game/SKILL.md).
 *
 * <p>NOT a test and NOT part of the game build: it lives under .claude/skills, is compiled only inside the
 * scratch "mirror" build that setup.sh creates, and is never wired into Gradle, CI or the test suite.
 *
 * <p>It wraps the real {@link Main}, replaces {@code Gdx.input} with a proxy that (a) reports a multitouch screen
 * (desktop otherwise never enables the touch controls — World.initTouchControls returns early) and (b) plays a
 * scripted list of taps, and reads the GL back buffer into PNGs. Script: system property {@code cap.script},
 * semicolon-separated {@code <seconds>:<command>[:args]} steps:
 * <pre>
 *   tap:X:Y            tap at WORLD coordinates (1280x720, Y-UP, same as the game's constants) for 0.25 s
 *   hold:X:Y:SECONDS   press and hold
 *   shot:NAME          write {cap.out}/NAME.png after this frame renders
 *   pulse:ITEM_TYPE    reflection: World.atmosphereGrade.pulseMuzzleLight(ItemType.ITEM_TYPE)
 *   timestart / timestop   average frame time (render + glFinish) between the two, printed to stdout
 *   exit               quit
 * </pre>
 */
public class CaptureLauncher extends ApplicationAdapter {
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float TAP_SECONDS = 0.25f;

    private final Main main = new Main();
    private final List<String[]> steps = new ArrayList<>();
    private String outputDirectory;
    private float clockSeconds;
    private int stepIndex;
    private boolean touched;
    private boolean justTouched;
    private int touchScreenX;
    private int touchScreenY;
    private float releaseAtSeconds = -1f;
    private Input realInput;
    private Input scriptedInput;
    private boolean timing;
    private long frameNanosTotal;
    private int frameCount;

    public static void main(String[] arguments) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("toon3d-capture");
        configuration.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        configuration.useVsync(false);
        int framesPerSecondCap = Integer.getInteger("cap.fps", 30);   // 0 = uncapped (use for timing)
        configuration.setForegroundFPS(framesPerSecondCap);
        configuration.setIdleFPS(framesPerSecondCap);
        new Lwjgl3Application(new CaptureLauncher(), configuration);
    }

    @Override
    public void create() {
        outputDirectory = System.getProperty("cap.out", "/tmp/toon3d-shots");
        Gdx.files.absolute(outputDirectory).mkdirs();
        for (String step : System.getProperty("cap.script", "").split(";")) {
            if (!step.isBlank()) steps.add(step.trim().split(":"));
        }
        realInput = Gdx.input;
        scriptedInput = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class},
                (proxy, method, methodArguments) -> {
                    switch (method.getName()) {
                        case "isTouched":             return touched;
                        case "justTouched":           return justTouched;
                        case "getX":                  return touchScreenX;
                        case "getY":                  return touchScreenY;
                        case "isButtonPressed":       return touched;
                        case "isPeripheralAvailable": return true;
                        default:
                            try {
                                return method.invoke(realInput, methodArguments);
                            } catch (InvocationTargetException exception) {
                                throw exception.getCause();
                            }
                    }
                });
        Gdx.input = scriptedInput;
        main.create();
    }

    @Override
    public void resize(int width, int height) {
        main.resize(width, height);
    }

    @Override
    public void render() {
        clockSeconds += Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        justTouched = false;
        if (touched && clockSeconds >= releaseAtSeconds) {
            touched = false;
            InputProcessor processor = realInput.getInputProcessor();
            if (processor != null) processor.touchUp(touchScreenX, touchScreenY, 0, Input.Buttons.LEFT);
        }
        String shotName = null;
        while (stepIndex < steps.size() && clockSeconds >= Float.parseFloat(steps.get(stepIndex)[0])) {
            String[] step = steps.get(stepIndex++);
            switch (step[1]) {
                case "tap":
                case "hold":
                    press(Float.parseFloat(step[2]), Float.parseFloat(step[3]),
                          step[1].equals("hold") ? Float.parseFloat(step[4]) : TAP_SECONDS);
                    break;
                case "shot":
                    shotName = step[2];
                    break;
                case "pulse":
                    pulseMuzzleLight(step[2]);
                    break;
                case "call":
                    callOnWorld(step);
                    break;
                case "set":
                    setOnWorld(step);
                    break;
                case "timestart":
                    timing = true;
                    frameNanosTotal = 0;
                    frameCount = 0;
                    break;
                case "timestop":
                    timing = false;
                    System.out.println("[capture] avg frame ms=" + (frameNanosTotal / 1e6 / Math.max(1, frameCount))
                            + " over " + frameCount + " frames");
                    break;
                case "exit":
                    Gdx.app.exit();
                    break;
                default:
                    System.out.println("[capture] unknown step " + String.join(":", step));
            }
        }
        // Lwjgl3Application re-points Gdx.input at the window's own input every frame: re-install the proxy.
        Gdx.input = scriptedInput;
        long frameStartNanos = System.nanoTime();
        main.render();
        Gdx.gl.glFinish();
        if (timing) {
            frameNanosTotal += System.nanoTime() - frameStartNanos;
            frameCount++;
        }
        if (shotName != null) writeShot(shotName);
    }

    /** World coordinates (Y-up, 1280x720) -> window pixels (Y-down), honouring the FitViewport letterbox. */
    private void press(float worldX, float worldY, float holdSeconds) {
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();
        float scale = Math.min(screenWidth / WORLD_WIDTH, screenHeight / WORLD_HEIGHT);
        float offsetX = (screenWidth - WORLD_WIDTH * scale) / 2f;
        float offsetY = (screenHeight - WORLD_HEIGHT * scale) / 2f;
        touchScreenX = Math.round(offsetX + worldX * scale);
        touchScreenY = Math.round(offsetY + (WORLD_HEIGHT - worldY) * scale);
        touched = true;
        justTouched = true;
        releaseAtSeconds = clockSeconds + holdSeconds;
        InputProcessor processor = realInput.getInputProcessor();
        if (processor != null) processor.touchDown(touchScreenX, touchScreenY, 0, Input.Buttons.LEFT);
        System.out.println("[capture] tap world(" + worldX + "," + worldY + ") at " + clockSeconds + "s");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void pulseMuzzleLight(String itemTypeName) {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object world = worldField.get(main);
            Field gradeField = world.getClass().getDeclaredField("atmosphereGrade");
            gradeField.setAccessible(true);
            Object grade = gradeField.get(world);
            Class itemType = Class.forName("ge.tbegvadze.toon3d.item.ItemType");
            grade.getClass().getMethod("pulseMuzzleLight", itemType)
                 .invoke(grade, Enum.valueOf(itemType, itemTypeName));
            System.out.println("[capture] pulsed " + itemTypeName);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code call:FIELD_PATH:METHOD[:INT]} — invokes a (private or public) no-arg or int-arg method on the object
     * reached from {@code Main.world} through the dot-separated field path ({@code .} = the World itself).
     * Examples: {@code call:.:onDescentRequested}, {@code call:player:applyDamage:9999},
     * {@code call:playerProgress:addXp:500}. A dev hook for reaching a screen, never game logic.
     */
    private void callOnWorld(String[] step) {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object target = worldField.get(main);
            if (!step[2].equals(".")) {
                for (String fieldName : step[2].split("\\.")) {
                    Field field = findField(target.getClass(), fieldName);
                    field.setAccessible(true);
                    target = field.get(target);
                }
            }
            boolean hasArgument = step.length > 4;
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals(step[3])) continue;
                    if (method.getParameterCount() != (hasArgument ? 1 : 0)) continue;
                    method.setAccessible(true);
                    Object result = hasArgument ? method.invoke(target, Integer.parseInt(step[4])) : method.invoke(target);
                    System.out.println("[capture] called " + String.join(":", step) + " -> " + result);
                    return;
                }
            }
            System.out.println("[capture] no method for " + String.join(":", step));
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code set:FIELD_PATH:VALUE} — sets a boolean or int field reached from {@code Main.world} by a dot-separated
     * path (last segment = the field). E.g. {@code set:startRoomChoiceResolved:true} lets the start-room portal open
     * without the random weapon walk. A dev hook for reaching a screen, never game logic.
     */
    private void setOnWorld(String[] step) {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object target = worldField.get(main);
            String[] path = step[2].split("\\.");
            for (int index = 0; index < path.length - 1; index++) {
                Field field = findField(target.getClass(), path[index]);
                field.setAccessible(true);
                target = field.get(target);
            }
            Field field = findField(target.getClass(), path[path.length - 1]);
            field.setAccessible(true);
            if (field.getType() == boolean.class) field.setBoolean(target, Boolean.parseBoolean(step[3]));
            else field.setInt(target, Integer.parseInt(step[3]));
            System.out.println("[capture] set " + String.join(":", step));
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchFieldException(name);
    }

    private void writeShot(String shotName) {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
        PixmapIO.writePNG(Gdx.files.absolute(outputDirectory + "/" + shotName + ".png"), pixmap);
        pixmap.dispose();
        System.out.println("[capture] " + shotName + " written at " + clockSeconds + "s");
    }

    @Override public void pause()   { main.pause(); }
    @Override public void resume()  { main.resume(); }
    @Override public void dispose() { main.dispose(); }
}
