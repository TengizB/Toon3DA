package ge.tbegvadze.toon3d.render.propvisual;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * The loaded generated prop sheets of one floor (generated-prop-art order 1). Built and disposed by the
 * {@code PropRenderer} that owns it (rule R6). Every registered sheet is loaded once at construction; a sheet
 * that fails to load is logged and simply absent, so its prop is not drawn (rule R5) - never a crash.
 */
public final class GeneratedPropSheets implements Disposable {

    private static final String LOG_TAG = "GeneratedPropSheets";

    private final Map<String, GeneratedPropSheet> sheetsById = new HashMap<>();

    public GeneratedPropSheets() {
        FileHandle directory = Gdx.files.internal(RenderConstants.PROP_SHEET_DIRECTORY);
        for (String sheetId : GeneratedPropRegistry.sheetIds()) {
            try {
                sheetsById.put(sheetId, GeneratedPropSheet.load(directory, sheetId));
            } catch (RuntimeException failure) {
                Gdx.app.error(LOG_TAG, "prop sheet '" + sheetId + "' failed to load; that prop is not drawn",
                        failure);
            }
        }
    }

    /** The sheet drawing this environment sprite, or null (not migrated, or failed to load). */
    public GeneratedPropSheet sheetForSprite(String spriteId) {
        String sheetId = GeneratedPropRegistry.sheetForSprite(spriteId);
        return sheetId == null ? null : sheetsById.get(sheetId);
    }

    /** The sheet drawing this FIXED prop char, or null. */
    public GeneratedPropSheet sheetForFixedChar(char propChar) {
        String sheetId = GeneratedPropRegistry.sheetForFixedChar(propChar);
        return sheetId == null ? null : sheetsById.get(sheetId);
    }

    @Override
    public void dispose() {
        for (GeneratedPropSheet sheet : sheetsById.values()) {
            sheet.dispose();
        }
        sheetsById.clear();
    }
}
