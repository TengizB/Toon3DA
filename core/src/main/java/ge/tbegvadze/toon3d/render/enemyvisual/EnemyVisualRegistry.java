package ge.tbegvadze.toon3d.render.enemyvisual;

import ge.tbegvadze.toon3d.enemy.EnemyType;

import java.util.EnumMap;

/**
 * {@link EnemyType} -> generated creature id (generated-enemy-art order 2). A type with a row is drawn
 * by the animated runtime; a type without one keeps the legacy draw path until order 9 deletes it.
 *
 * <p>Migrating a type is ONE {@link #register} row in {@link #bootstrap()} — never a switch. The
 * creature id names the files {@code <id>.clips.json}, {@code <id>.atlas.png} and {@code <id>.glow.png}
 * under {@code EnemyConstants.ENEMY_GENERATED_DIRECTORY}.
 */
public final class EnemyVisualRegistry {

    private static final EnumMap<EnemyType, String> CREATURE_IDS = new EnumMap<>(EnemyType.class);
    private static boolean bootstrapped = false;

    private EnemyVisualRegistry() {
    }

    /** Idempotent: the rows below, registered once per app session. */
    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        // Order 2: the two creatures order 1 rigged and animated (series AS1).
        register(EnemyType.ACID_DRONE,  "warden");
        register(EnemyType.SHELL_BRUTE, "harvest_tick");
        // Order 4: the insect family's MINI_ELITE, the first creature built from its brief through the whole pipeline.
        register(EnemyType.IRON_STALKER, "iron_stalker");
    }

    public static void register(EnemyType type, String creatureId) {
        if (CREATURE_IDS.containsKey(type)) {
            throw new IllegalStateException("enemy visual already registered for " + type);
        }
        CREATURE_IDS.put(type, creatureId);
    }

    /** The creature id drawing {@code type}, or null when the type is still on the legacy path. */
    public static String creatureIdFor(EnemyType type) {
        return CREATURE_IDS.get(type);
    }

    public static boolean isMigrated(EnemyType type) {
        return CREATURE_IDS.containsKey(type);
    }
}
