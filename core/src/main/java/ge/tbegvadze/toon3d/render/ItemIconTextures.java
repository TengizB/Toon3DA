package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.item.ItemType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ground-pickup sprites reused as inventory icons for medkits and ammo, so the
 * inventory menu shows the same billboard art the player sees on the level
 * floor instead of a plain ASCII glyph. Built once and owned by
 * InventoryOverlayRenderer; shared by ItemGridPanel and ItemWindow.
 */
final class ItemIconTextures implements Disposable {

    private final Map<ItemType, Texture> textures = new EnumMap<>(ItemType.class);

    ItemIconTextures() {
        textures.put(ItemType.MEDKIT_SMALL, PropRenderer.generateStimTexture());
        textures.put(ItemType.MEDKIT_LARGE, PropRenderer.generateFieldMedkitTexture());
        textures.put(ItemType.AMMO_BULLETS, PropRenderer.generateBulletClipTexture());
        textures.put(ItemType.AMMO_SHELLS,  PropRenderer.generateShotgunShellsTexture());
        textures.put(ItemType.AMMO_CELLS,   PropRenderer.generatePlasmaCellTexture());
        textures.put(ItemType.AMMO_ROCKETS, PropRenderer.generateRocketTexture());
        textures.put(ItemType.AMMO_SLUGS,   PropRenderer.generateRailSlugsTexture());
    }

    /** Returns the ground-pickup texture for the given item type, or null if it has none. */
    Texture get(ItemType itemType) {
        return textures.get(itemType);
    }

    @Override
    public void dispose() {
        for (Texture texture : textures.values()) {
            texture.dispose();
        }
    }
}
