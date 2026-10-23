package ge.tbegvadze.toon3d.render.propvisual;

import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which environment objects are drawn from a generated PROP SHEET (generated-prop-art series, rule PS5).
 *
 * <p>Two kinds of row: an environment SPRITE ID (a tileset prop - its symbol is chosen per level by the palette,
 * so the binding follows the sprite, never the symbol) and a FIXED prop CHAR (a gameplay prop with a permanent
 * picture, like the exit portal). Migrating a prop is ONE row in {@link #bootstrap()} - never a switch - plus its
 * sheet files from {@code props.py}. A migrated prop has no other picture (PS6).
 *
 * <p>Headless (no LibGDX), static, idempotent bootstrap - the same idiom as the other render registries.
 */
public final class GeneratedPropRegistry {

    private static final Map<String, String>    SHEET_BY_SPRITE_ID = new HashMap<>();
    private static final Map<Character, String> SHEET_BY_FIXED_CHAR = new HashMap<>();
    private static final Set<String>            SHEET_IDS = new LinkedHashSet<>();
    private static boolean bootstrapped;

    private GeneratedPropRegistry() {
    }

    /** THE CATALOG: one row per migrated prop. Idempotent. */
    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        // generated-prop-art order 1
        registerSprite("prop_terminal", "terminal");
        registerSprite("prop_biopod",   "bio_pod");
        registerFixedChar(RenderConstants.STAIRS_DOWN_CHAR, "portal");
        bootstrapped = true;
    }

    private static void registerSprite(String spriteId, String sheetId) {
        if (SHEET_BY_SPRITE_ID.put(spriteId, sheetId) != null) {
            throw new IllegalStateException("prop sprite registered twice: " + spriteId);
        }
        SHEET_IDS.add(sheetId);
    }

    private static void registerFixedChar(char propChar, String sheetId) {
        if (SHEET_BY_FIXED_CHAR.put(propChar, sheetId) != null) {
            throw new IllegalStateException("fixed prop char registered twice: " + propChar);
        }
        SHEET_IDS.add(sheetId);
    }

    /** The sheet id drawing an environment sprite, or null when the sprite is not migrated. */
    public static String sheetForSprite(String spriteId) {
        bootstrap();
        return spriteId == null ? null : SHEET_BY_SPRITE_ID.get(spriteId);
    }

    /** The sheet id drawing a FIXED prop char, or null. */
    public static String sheetForFixedChar(char propChar) {
        bootstrap();
        return SHEET_BY_FIXED_CHAR.get(propChar);
    }

    /** True when the environment sprite is drawn from a generated sheet (and so needs no Pixmap generator). */
    public static boolean isGeneratedSprite(String spriteId) {
        return sheetForSprite(spriteId) != null;
    }

    /** Every registered sheet id, in registration order. */
    public static Set<String> sheetIds() {
        bootstrap();
        return Collections.unmodifiableSet(SHEET_IDS);
    }
}
