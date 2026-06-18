package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * Draws screen-space rising event text entries from EventTextSystem.
 * Each active entry is centred horizontally, rises by EVENT_TEXT_RISE_PIXELS over its lifetime,
 * and fades from fully opaque to transparent. A one-pixel dark shadow improves readability
 * against any background.
 *
 * Stacking: active entries are drawn at vertically offset positions so concurrent texts don't
 * overlap — the oldest entry sits highest, new arrivals start at the anchor Y.
 */
public final class EventTextRenderer implements Disposable {

    // Base palette — never mutated; per-frame alpha is written into temporaryColor only.
    private static final Color BASE_SHADOW = new Color(0f,    0f,    0f,    1f);
    private static final Color BASE_WHITE  = new Color(1f,    1f,    1f,    1f);
    private static final Color BASE_GREEN  = new Color(0.25f, 1f,    0.25f, 1f);
    private static final Color BASE_RED    = new Color(1f,    0.18f, 0.18f, 1f);
    private static final Color BASE_GREY   = new Color(0.8f,  0.8f,  0.8f,  1f);
    private static final Color BASE_GOLD   = new Color(1f,    0.69f, 0.125f, 1f);

    private final EventTextSystem eventTextSystem;
    private final SpriteBatch     batch;
    private final BitmapFont      font;
    private final GlyphLayout     layout;
    // Reusable scratch color — receives a base color copy then has alpha set each draw call.
    private final Color           temporaryColor = new Color();

    public EventTextRenderer(EventTextSystem eventTextSystem) {
        this.eventTextSystem = eventTextSystem;
        this.batch           = new SpriteBatch();
        this.font            = new BitmapFont();
        this.font.getData().setScale(1.8f);
        this.font.getData().markupEnabled = false;
        this.layout          = new GlyphLayout();
    }

    public void render(OrthographicCamera camera) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        int stackOffset = 0;
        float lifeSeconds = EffectConstants.EVENT_TEXT_LIFE_SECONDS;

        for (int slotIndex = 0; slotIndex < eventTextSystem.getPoolSize(); slotIndex++) {
            String text = eventTextSystem.getText(slotIndex);
            if (text == null) continue;

            float age      = eventTextSystem.getAge(slotIndex);
            float fraction = age / lifeSeconds;
            float alpha    = 1f - fraction;
            float riseY    = fraction * EffectConstants.EVENT_TEXT_RISE_PIXELS;
            float baseY    = EffectConstants.EVENT_TEXT_ANCHOR_Y + stackOffset * EffectConstants.EVENT_TEXT_LINE_STEP + riseY;

            layout.setText(font, text);
            float textX = (Constants.WORLD_WIDTH - layout.width) / 2f;

            temporaryColor.set(BASE_SHADOW);
            temporaryColor.a = alpha * 0.7f;
            font.setColor(temporaryColor);
            font.draw(batch, text, textX + 1f, baseY - 1f);

            temporaryColor.set(resolveBaseColor(eventTextSystem.getColorType(slotIndex)));
            temporaryColor.a = alpha;
            font.setColor(temporaryColor);
            font.draw(batch, text, textX, baseY);

            stackOffset++;
        }

        batch.end();
    }

    private static Color resolveBaseColor(byte colorType) {
        switch (colorType) {
            case EventTextSystem.COLOR_GREEN: return BASE_GREEN;
            case EventTextSystem.COLOR_RED:   return BASE_RED;
            case EventTextSystem.COLOR_GREY:  return BASE_GREY;
            case EventTextSystem.COLOR_GOLD:  return BASE_GOLD;
            default:                          return BASE_WHITE;
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
