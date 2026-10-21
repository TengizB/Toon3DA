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
 *   spawn:TYPE:FORWARD:RIGHT   reflection: a DORMANT enemy of EnemyType TYPE, FORWARD tiles ahead and RIGHT tiles
 *                      to the player's right (negative = behind / left), through EnemyManager.spawnEnemy
 *   alert:INDEX        wakes enemy INDEX (list order; negative counts from the end, -1 = last spawned) and
 *                      reports the ALERTED visual moment, as perception would
 *   damage:INDEX:AMOUNT   EnemyManager.applyDamageTo(enemy, AMOUNT) — the real player-hit path
 *   kill:INDEX         the real hit path with an overwhelming amount
 *   clip:INDEX         prints the animated clip enemy INDEX is showing (render/enemyvisual)
 *   burst:NAME:COUNT:INTERVAL   COUNT shots NAME_00.. every INTERVAL seconds from now (one per frame at most)
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
    private String burstName;
    private int burstTaken;
    private int burstCount;
    private float burstInterval;
    private float burstNextAt;

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
                case "hittest":
                    hitTest(Float.parseFloat(step[2]), Float.parseFloat(step[3]));
                    break;
                case "bosshud":
                    showBossHud(Integer.parseInt(step[2]), step.length > 3 ? step[3] : null);
                    break;
                case "alertall":
                    alertAllEnemies();
                    break;
                case "engage":
                    engageFirstRouteNode();
                    break;
                case "damagenumber":
                    spawnDamageNumber(Integer.parseInt(step[2]), step.length > 3 && Boolean.parseBoolean(step[3]));
                    break;
                case "eventtext":
                    spawnEventText(Byte.parseByte(step[2]), step[3]);
                    break;
                case "status":
                    setPlayerStatus(step[2], Integer.parseInt(step[3]));
                    break;
                case "equip":
                    equipArsenalWeapon(Integer.parseInt(step[2]));
                    break;
                case "inspectweapon":
                    inspectFirstGroundWeapon();
                    break;
                case "additem":
                    addInventoryItem(step[2], Integer.parseInt(step[3]));
                    break;
                case "seed":
                    setRunSeed(Long.parseLong(step[2]));
                    break;
                case "ending":
                    presentEndingCard(step[2]);
                    break;
                case "event":
                    openEventConsole(step.length > 2 ? step[2] : "");
                    break;
                case "shop":
                    openFirstShop();
                    break;
                case "barkspeaker":
                    reassignBarkSpeaker(step[2]);
                    break;
                case "spawn":
                    spawnDormantEnemy(step[2], Integer.parseInt(step[3]), Integer.parseInt(step[4]));
                    break;
                case "alert":
                    alertEnemy(Integer.parseInt(step[2]));
                    break;
                case "damage":
                    damageEnemy(Integer.parseInt(step[2]), Integer.parseInt(step[3]));
                    break;
                case "kill":
                    damageEnemy(Integer.parseInt(step[2]), 1_000_000);
                    break;
                case "clip":
                    printEnemyClip(Integer.parseInt(step[2]));
                    break;
                case "burst":
                    burstName = step[2];
                    burstCount = Integer.parseInt(step[3]);
                    burstInterval = Float.parseFloat(step[4]);
                    burstTaken = 0;
                    burstNextAt = clockSeconds;
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
        if (shotName == null && burstName != null && clockSeconds >= burstNextAt) {
            shotName = String.format("%s_%02d", burstName, burstTaken++);
            burstNextAt += burstInterval;
            if (burstTaken >= burstCount) burstName = null;
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

    /**
     * {@code equip:INDEX} — puts arsenal weapon INDEX (World.inventory's arsenal: 0 shotgun, 1 double barrel,
     * 2 plasma, 3 chaingun, ... on the start room) into the loadout and selects it, so a HUD shot can show a
     * ranged weapon with ammo. A dev hook for reaching a screen, never game logic.
     */
    private void equipArsenalWeapon(int arsenalIndex) {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object world = worldField.get(main);
            Field inventoryField = findField(world.getClass(), "inventory");
            inventoryField.setAccessible(true);
            Object inventory = inventoryField.get(world);
            Object weapon = ((java.util.List<?>) inventory.getClass().getMethod("getArsenal").invoke(inventory))
                    .get(arsenalIndex);
            Object loadout = inventory.getClass().getMethod("getLoadout").invoke(inventory);
            Class<?> weaponType = Class.forName("ge.tbegvadze.toon3d.entity.Weapon");
            loadout.getClass().getMethod("tryEquip", weaponType).invoke(loadout, weapon);
            inventory.getClass().getMethod("selectLoadoutWeapon", weaponType).invoke(inventory, weapon);
            System.out.println("[capture] equipped arsenal weapon " + arsenalIndex);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code inspectweapon} — records the first weapon lying on the floor as the one the player stands on
     * ({@code PlayerController.standingOnWeapon}) and opens the weapon inspect card for it
     * ({@code World.openWeaponInspectOverlay}), so the card can be captured without walking onto a weapon whose
     * position is random every run. A dev hook for reaching a screen, never game logic.
     */
    private void inspectFirstGroundWeapon() {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object world = worldField.get(main);
            Field groundField = findField(world.getClass(), "groundItems");
            groundField.setAccessible(true);
            Object chosen = null;
            for (Object groundItem : (Iterable<?>) groundField.get(world)) {
                Field stackField = findField(groundItem.getClass(), "stack");
                stackField.setAccessible(true);
                Object stack = stackField.get(groundItem);
                Object type = stack.getClass().getMethod("getType").invoke(stack);
                Object category = type.getClass().getMethod("getCategory").invoke(type);
                if (String.valueOf(category).equals("WEAPON")) { chosen = groundItem; break; }
            }
            if (chosen == null) { System.out.println("[capture] inspectweapon: no weapon on the floor"); return; }
            Field controllerField = findField(world.getClass(), "playerController");
            controllerField.setAccessible(true);
            Object controller = controllerField.get(world);
            Field standingField = findField(controller.getClass(), "standingOnWeapon");
            standingField.setAccessible(true);
            standingField.set(controller, chosen);
            java.lang.reflect.Method open = world.getClass().getDeclaredMethod("openWeaponInspectOverlay");
            open.setAccessible(true);
            open.invoke(world);
            System.out.println("[capture] inspectweapon opened");
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code additem:ITEM_TYPE:QUANTITY} — puts QUANTITY of an {@code ItemType} into the bag
     * ({@code World.itemInventory.tryAdd}), so the inventory grid and its item windows can be captured with
     * content. A dev hook for reaching a screen, never game logic.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addInventoryItem(String itemTypeName, int quantity) {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object world = worldField.get(main);
            Field inventoryField = findField(world.getClass(), "itemInventory");
            inventoryField.setAccessible(true);
            Object inventory = inventoryField.get(world);
            Class itemType = Class.forName("ge.tbegvadze.toon3d.item.ItemType");
            Object added = inventory.getClass().getMethod("tryAdd", itemType, int.class)
                    .invoke(inventory, Enum.valueOf(itemType, itemTypeName), quantity);
            System.out.println("[capture] additem " + itemTypeName + " x" + quantity + " -> " + added);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code hittest:X:Y} — prints which touch buttons' hit areas contain world point (X, Y), testing every button
     * as if visible (contextual INSPECT / USE included) and restoring visibility after. Proves a tap area without
     * needing the context that shows the button. A dev hook, never game logic.
     */
    private void hitTest(float worldX, float worldY) {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object world = worldField.get(main);
            Field stateField = findField(world.getClass(), "touchInputState");
            stateField.setAccessible(true);
            Object state = stateField.get(world);
            Field buttonsField = findField(state.getClass(), "buttons");
            buttonsField.setAccessible(true);
            Object[] buttons = (Object[]) buttonsField.get(state);
            StringBuilder hits = new StringBuilder();
            for (Object button : buttons) {
                Field visibleField = findField(button.getClass(), "visible");
                Field actionField = findField(button.getClass(), "action");
                visibleField.setAccessible(true);
                actionField.setAccessible(true);
                boolean wasVisible = visibleField.getBoolean(button);
                visibleField.setBoolean(button, true);
                java.lang.reflect.Method contains = button.getClass().getDeclaredMethod("contains", float.class, float.class);
                contains.setAccessible(true);
                boolean hit = (Boolean) contains.invoke(button, worldX, worldY);
                visibleField.setBoolean(button, wasVisible);
                if (hit) hits.append(actionField.get(button)).append(' ');
            }
            System.out.println("[capture] hittest (" + worldX + "," + worldY + ") -> " + hits);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code bosshud:PERCENT[:VISUAL_STATE]} — attaches a BossHudRenderer to the World with a stand-in Boss (first
     * EnemyType, a fixed name/epithet/accent) at PERCENT health, so the boss bar can be seen from floor 1. The World
     * disposes the renderer on teardown as it does a real one. A dev hook for reaching a screen, never game logic.
     */
    private void showBossHud(int healthPercent, String visualStateName) {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object world = worldField.get(main);
            Class<?> enemyTypeClass = Class.forName("ge.tbegvadze.toon3d.enemy.EnemyType");
            Class<?> patternClass = Class.forName("ge.tbegvadze.toon3d.entity.boss.BossAttackPattern");
            Class<?> bossClass = Class.forName("ge.tbegvadze.toon3d.entity.boss.Boss");
            Object enemyType = enemyTypeClass.getEnumConstants()[0];
            Object boss = bossClass.getConstructor(enemyTypeClass, int.class, int.class, String.class, String.class,
                    String.class, float.class, float.class, float.class, float.class, patternClass, patternClass)
                    .newInstance(enemyType, 0, 0, "THE WARDEN", "KEEPER OF THE OUTER LOCKS", "", 0.95f, 0.55f, 0.2f,
                            1f, null, null);
            Field maxHealthField = findField(bossClass, "maxHealth");
            Field healthField = findField(bossClass, "health");
            int maxHealth = Math.max(100, maxHealthField.getInt(boss));
            maxHealthField.setInt(boss, maxHealth);
            healthField.setInt(boss, maxHealth * healthPercent / 100);
            if (visualStateName != null) {
                Class<?> stateClass = Class.forName("ge.tbegvadze.toon3d.entity.boss.BossVisualState");
                findField(bossClass, "visualState").set(boss, stateClass.getField(visualStateName).get(null));
            }
            Class<?> rendererClass = Class.forName("ge.tbegvadze.toon3d.render.BossHudRenderer");
            Object renderer = rendererClass.getConstructor().newInstance();
            rendererClass.getMethod("setBoss", bossClass).invoke(renderer, boss);
            Field rendererField = findField(world.getClass(), "bossHudRenderer");
            rendererField.setAccessible(true);
            rendererField.set(world, renderer);
            System.out.println("[capture] boss hud at " + healthPercent + "%");
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code alertall} — calls {@code alert()} on every enemy of World.enemyManager, so the mini-map draws their
     * threat markers without a scripted hunt. A dev hook for reaching a screen, never game logic.
     */
    private void alertAllEnemies() {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object world = worldField.get(main);
            Field managerField = findField(world.getClass(), "enemyManager");
            managerField.setAccessible(true);
            Object manager = managerField.get(world);
            java.util.List<?> enemies = (java.util.List<?>) manager.getClass().getMethod("getEnemies").invoke(manager);
            for (Object enemy : enemies) enemy.getClass().getMethod("alert").invoke(enemy);
            System.out.println("[capture] alerted " + enemies.size() + " enemies");
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code engage} — commits the route map's first selectable next node (World.commitRouteNode), the same path
     * a tap on a node + ENGAGE takes. The map is seeded by time, so tapping a fixed spot is not repeatable; this
     * is. Open the map first ({@code set:startRoomChoiceResolved:true} + {@code call:.:onDescentRequested}).
     * A dev hook for reaching a screen, never game logic.
     */
    private void engageFirstRouteNode() {
        try {
            Field worldField = Main.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object world = worldField.get(main);
            Field mapField = findField(world.getClass(), "routeMap");
            mapField.setAccessible(true);
            Object routeMap = mapField.get(world);
            Object node = ((java.util.List<?>) routeMap.getClass().getMethod("getSelectableNext").invoke(routeMap)).get(0);
            for (java.lang.reflect.Method method : world.getClass().getDeclaredMethods()) {
                if (!method.getName().equals("commitRouteNode")) continue;
                method.setAccessible(true);
                method.invoke(world, node);
                System.out.println("[capture] engaged " + node);
                return;
            }
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code damagenumber:AMOUNT[:KILL]} — spawns a floating damage number (ImpactEffectSystem.onEnemyHit, or
     * onEnemyKilled when KILL is true) two tiles in front of the player. A dev hook, never game logic.
     */
    private void spawnDamageNumber(int amount, boolean kill) {
        try {
            Object world = worldObject();
            Object player = fieldValue(world, "player");
            float cellSize = Class.forName("ge.tbegvadze.toon3d.util.Constants").getField("CELL_SIZE").getFloat(null);
            float worldX = fieldFloat(player, "positionX") + fieldFloat(player, "directionX") * 2f * cellSize;
            float worldY = fieldFloat(player, "positionY") + fieldFloat(player, "directionY") * 2f * cellSize;
            Object system = fieldValue(world, "impactEffectSystem");
            system.getClass().getMethod(kill ? "onEnemyKilled" : "onEnemyHit", float.class, float.class, float.class,
                    int.class).invoke(system, worldX, worldY, 1f, amount);
            System.out.println("[capture] damage number " + amount + (kill ? " (kill)" : ""));
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code eventtext:COLOR_CODE:TEXT} — EventTextSystem.spawnWithColor(TEXT, code) with the system's byte colour
     * code (0 white, 1 green, 2 red, 3 grey, 4 gold, 5 credit, 6 blue, 7 armour, 8 amber). TEXT may not contain
     * ';' or ':'. A dev hook, never game logic.
     */
    private void spawnEventText(byte colorCode, String text) {
        try {
            Object system = fieldValue(worldObject(), "eventTextSystem");
            system.getClass().getMethod("spawnWithColor", String.class, byte.class).invoke(system, text, colorCode);
            System.out.println("[capture] event text " + text);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code status:STATUS_TYPE:TURNS} — makes the player's StatusEffect of that type active for TURNS turns
     * (magnitude 5, one stack) by setting its fields, so the HUD's status icons and debuff list can be seen.
     * A dev hook for reaching a screen, never game logic.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setPlayerStatus(String typeName, int turns) {
        try {
            Object player = fieldValue(worldObject(), "player");
            Class statusType = Class.forName("ge.tbegvadze.toon3d.status.StatusType");
            Object effects = player.getClass().getMethod("getActiveEffects").invoke(player);
            Object effect = ((java.util.Map) effects).get(Enum.valueOf(statusType, typeName));
            for (String name : new String[] {"remainingTurns", "magnitude", "stacks"}) {
                Field field = findField(effect.getClass(), name);
                field.setAccessible(true);
                field.setInt(effect, name.equals("remainingTurns") ? turns : name.equals("magnitude") ? 5 : 1);
            }
            System.out.println("[capture] status " + typeName + " " + turns);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code seed:N} — overwrites World.runSeed (a final field, set by reflection) and drops any route map already
     * generated, so the next {@code call:.:onDescentRequested} builds the SAME map in every run (the map is
     * otherwise time-seeded). Use it before opening the map. A dev hook for reaching a screen, never game logic.
     */
    private void setRunSeed(long seed) {
        try {
            Object world = worldObject();
            Field seedField = findField(world.getClass(), "runSeed");
            seedField.setAccessible(true);
            seedField.setLong(world, seed);
            Field mapField = findField(world.getClass(), "routeMap");
            mapField.setAccessible(true);
            mapField.set(world, null);
            System.out.println("[capture] run seed " + seed);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code ending:VARIANT} — World.presentEndingCard(BootCardVariant.VARIANT) (FREE / KILL give the terminal card
     * with the RETURN plate; OBEY a continuing card). A dev hook for reaching a screen, never game logic.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void presentEndingCard(String variantName) {
        try {
            Object world = worldObject();
            Class variantClass = Class.forName("ge.tbegvadze.toon3d.narrative.BootCardVariant");
            Object result = world.getClass().getMethod("presentEndingCard", variantClass)
                    .invoke(world, Enum.valueOf(variantClass, variantName));
            System.out.println("[capture] ending " + variantName + " -> " + result);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code event[:EVENT_ID]} — opens the FACILITY EVENT console on the registered event EVENT_ID (the first one
     * registered when omitted) exactly as World.updateEventStations does on arrival at a station: activeEvent,
     * eventChoiceOverlayRenderer.present(event), runPhase EVENT_CHOICE. A dev hook for reaching a screen, never
     * game logic.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void openEventConsole(String eventId) {
        try {
            Object world = worldObject();
            Object registry = Class.forName("ge.tbegvadze.toon3d.route.RouteRegistries").getMethod("events").invoke(null);
            Object event = eventId.isEmpty()
                    ? ((java.util.List<?>) registry.getClass().getMethod("all").invoke(registry)).get(0)
                    : registry.getClass().getMethod("get", String.class).invoke(registry, eventId);
            if (event == null) { System.out.println("[capture] event: no event " + eventId); return; }
            Field activeField = findField(world.getClass(), "activeEvent");
            activeField.setAccessible(true);
            activeField.set(world, event);
            Object renderer = fieldValue(world, "eventChoiceOverlayRenderer");
            for (java.lang.reflect.Method method : renderer.getClass().getMethods()) {
                if (method.getName().equals("present") && method.getParameterCount() == 1) {
                    method.invoke(renderer, event);
                    break;
                }
            }
            Field phaseField = findField(world.getClass(), "runPhase");
            phaseField.setAccessible(true);
            phaseField.set(world, Enum.valueOf((Class) phaseField.getType(), "EVENT_CHOICE"));
            System.out.println("[capture] event console " + event);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code barkspeaker:SPEAKER} — puts the first registered bark of that Speaker (AI / ORGANIZATION / PLANET) on
     * screen through BarkSystem's own private deliver(definition), skipping gating and pacing, so another speaker's
     * panel can be seen without reaching their region. A dev hook for reaching a screen, never game logic.
     */
    private void reassignBarkSpeaker(String speakerName) {
        try {
            Object system = fieldValue(worldObject(), "barkSystem");
            Object registry = fieldValue(system, "registry");
            Object chosen = null;
            for (Object definition : (Iterable<?>) registry.getClass().getMethod("getAll").invoke(registry)) {
                Object speaker = definition.getClass().getMethod("getSpeaker").invoke(definition);
                if (String.valueOf(speaker).equals(speakerName)) { chosen = definition; break; }
            }
            if (chosen == null) { System.out.println("[capture] barkspeaker: none for " + speakerName); return; }
            java.lang.reflect.Method deliver = system.getClass().getDeclaredMethod("deliver", chosen.getClass());
            deliver.setAccessible(true);
            deliver.invoke(system, chosen);
            System.out.println("[capture] bark speaker -> " + speakerName);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * {@code shop} — opens the fabricator shop on the floor's first vending machine exactly as World.openShop does
     * for the machine in front of the player (openMachine, shopOverlayRenderer.open(machine, credits), runPhase
     * SHOP_OPEN), so the shop can be captured without walking to a machine. A dev hook, never game logic.
     */
    private void openFirstShop() {
        try {
            Object world = worldObject();
            java.util.List<?> machines = (java.util.List<?>) fieldValue(world, "vendingMachines");
            if (machines.isEmpty()) { System.out.println("[capture] shop: no vending machine on this floor"); return; }
            Object machine = machines.get(0);
            Field openField = findField(world.getClass(), "openMachine");
            openField.setAccessible(true);
            openField.set(world, machine);
            Object stats = fieldValue(world, "playerStats");
            int credits = (Integer) stats.getClass().getMethod("getCredits").invoke(stats);
            Object renderer = fieldValue(world, "shopOverlayRenderer");
            for (java.lang.reflect.Method method : renderer.getClass().getMethods()) {
                if (method.getName().equals("open") && method.getParameterCount() == 2) {
                    method.invoke(renderer, machine, credits);
                    break;
                }
            }
            Field phaseField = findField(world.getClass(), "runPhase");
            phaseField.setAccessible(true);
            phaseField.set(world, Enum.valueOf((Class) phaseField.getType(), "SHOP_OPEN"));
            System.out.println("[capture] shop opened with " + credits + " credits");
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    // ---- generated-enemy-art order 2: the animated enemy runtime -------------------------------------------

    /** {@code spawn:TYPE:FORWARD:RIGHT} — a DORMANT enemy relative to the player, with no visual moment fired. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void spawnDormantEnemy(String typeName, int forward, int right) {
        try {
            Object world = worldObject();
            Object player = fieldValue(world, "player");
            Object manager = fieldValue(world, "enemyManager");
            float cellSize = Class.forName("ge.tbegvadze.toon3d.util.Constants").getField("CELL_SIZE").getFloat(null);
            int directionX = Math.round(fieldFloat(player, "directionX"));
            int directionY = Math.round(fieldFloat(player, "directionY"));
            int column = (int) Math.floor(fieldFloat(player, "positionX") / cellSize)
                    + directionX * forward + directionY * right;
            int row = (int) Math.floor(fieldFloat(player, "positionY") / cellSize)
                    + directionY * forward - directionX * right;
            Class enemyType = Class.forName("ge.tbegvadze.toon3d.enemy.EnemyType");
            int depth = ((Number) fieldValue(world, "currentDepth")).intValue();
            List<?> enemies = (List<?>) manager.getClass().getMethod("getEnemies").invoke(manager);
            int before = enemies.size();
            // Spawning alerts by design (a summon arrives awake); a capture wants it asleep, so the listener is
            // detached for the call and the state put back to DORMANT before any frame sees it.
            Field listenerField = findField(manager.getClass(), "enemyVisualListener");
            listenerField.setAccessible(true);
            Object listener = listenerField.get(manager);
            listenerField.set(manager, null);
            manager.getClass().getMethod("spawnEnemy", enemyType, int.class, int.class, int.class)
                   .invoke(manager, Enum.valueOf(enemyType, typeName), column, row, depth);
            listenerField.set(manager, listener);
            if (enemies.size() == before) {
                System.out.println("[capture] spawn " + typeName + " refused at tile (" + column + "," + row + ")");
                return;
            }
            Object enemy = enemies.get(enemies.size() - 1);
            Class stateType = Class.forName("ge.tbegvadze.toon3d.enemy.EnemyState");
            findField(enemy.getClass(), "state").set(enemy, Enum.valueOf(stateType, "DORMANT"));
            System.out.println("[capture] spawned dormant " + typeName + " #" + (enemies.size() - 1)
                    + " at tile (" + column + "," + row + ")");
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    private Object enemyAt(int index) throws ReflectiveOperationException {
        Object manager = fieldValue(worldObject(), "enemyManager");
        List<?> enemies = (List<?>) manager.getClass().getMethod("getEnemies").invoke(manager);
        int resolved = index < 0 ? enemies.size() + index : index;
        if (resolved < 0 || resolved >= enemies.size()) {
            System.out.println("[capture] no enemy at index " + index + " (" + enemies.size() + " alive)");
            return null;
        }
        return enemies.get(resolved);
    }

    /** {@code alert:INDEX} — Enemy.alert() plus the ALERTED visual moment perception would have reported. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void alertEnemy(int index) {
        try {
            Object enemy = enemyAt(index);
            if (enemy == null) return;
            enemy.getClass().getMethod("alert").invoke(enemy);
            Object manager = fieldValue(worldObject(), "enemyManager");
            Class momentType = Class.forName("ge.tbegvadze.toon3d.enemy.EnemyVisualMoment");
            java.lang.reflect.Method fire = manager.getClass().getDeclaredMethod("fireVisual", enemy.getClass(), momentType);
            fire.setAccessible(true);
            fire.invoke(manager, enemy, Enum.valueOf(momentType, "ALERTED"));
            System.out.println("[capture] alerted enemy " + index);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /** {@code damage:INDEX:AMOUNT} / {@code kill:INDEX} — EnemyManager.applyDamageTo, the real player-hit path. */
    private void damageEnemy(int index, int amount) {
        try {
            Object enemy = enemyAt(index);
            if (enemy == null) return;
            Object manager = fieldValue(worldObject(), "enemyManager");
            int healthBefore = findField(enemy.getClass(), "health").getInt(enemy);
            manager.getClass().getMethod("applyDamageTo", Object.class, int.class).invoke(manager, enemy, amount);
            int healthAfter = findField(enemy.getClass(), "health").getInt(enemy);
            int maxHealth = findField(enemy.getClass(), "maxHealth").getInt(enemy);
            System.out.println("[capture] damaged enemy " + index + ": " + healthBefore + " -> " + healthAfter
                    + " of " + maxHealth);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    /** {@code clip:INDEX} — which clip the animated layer is showing for that enemy. */
    private void printEnemyClip(int index) {
        try {
            Object enemy = enemyAt(index);
            if (enemy == null) return;
            Object layer = fieldValue(worldObject(), "enemyVisualLayer");
            Object animator = layer.getClass().getMethod("animatorFor", enemy.getClass()).invoke(layer, enemy);
            Object state = findField(enemy.getClass(), "state").get(enemy);
            Object clip = animator == null ? "LEGACY" : animator.getClass()
                    .getMethod("currentClipId", enemy.getClass()).invoke(animator, enemy);
            System.out.println("[capture] enemy " + index + " state=" + state + " clip=" + clip + " at " + clockSeconds + "s");
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    private Object worldObject() throws ReflectiveOperationException {
        Field worldField = Main.class.getDeclaredField("world");
        worldField.setAccessible(true);
        return worldField.get(main);
    }

    private static Object fieldValue(Object target, String name) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static float fieldFloat(Object target, String name) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.getFloat(target);
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
