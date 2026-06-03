package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;

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

    private static final Color SHADOW_COLOR = new Color(0f, 0f, 0f, 1f);
    private static final Color TEXT_COLOR   = new Color(1f, 1f, 1f, 1f);

    private final EventTextSystem eventTextSystem;
    private final SpriteBatch     batch;
    private final BitmapFont      font;
    private final GlyphLayout     layout;

    public EventTextRenderer(EventTextSystem eventTextSystem) {
        this.eventTextSystem = eventTextSystem;
        this.batch           = new SpriteBatch();
        this.font            = new BitmapFont();
        this.font.getData().setScale(1.4f);
        this.font.getData().markupEnabled = false;
        this.layout          = new GlyphLayout();
    }

    public void render(OrthographicCamera camera) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        int stackOffset = 0;
        float lifeSeconds = Constants.EVENT_TEXT_LIFE_SECONDS;

        for (int slotIndex = 0; slotIndex < eventTextSystem.getPoolSize(); slotIndex++) {
            String text = eventTextSystem.getText(slotIndex);
            if (text == null) continue;

            float age      = eventTextSystem.getAge(slotIndex);
            float fraction = age / lifeSeconds;
            float alpha    = 1f - fraction;
            float riseY    = fraction * Constants.EVENT_TEXT_RISE_PIXELS;
            float baseY    = Constants.EVENT_TEXT_ANCHOR_Y + stackOffset * Constants.EVENT_TEXT_LINE_STEP + riseY;

            layout.setText(font, text);
            float textX = (Constants.WORLD_WIDTH - layout.width) / 2f;

            SHADOW_COLOR.a = alpha * 0.7f;
            font.setColor(SHADOW_COLOR);
            font.draw(batch, text, textX + 1f, baseY - 1f);

            TEXT_COLOR.a = alpha;
            font.setColor(TEXT_COLOR);
            font.draw(batch, text, textX, baseY);

            stackOffset++;
        }

        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
