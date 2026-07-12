package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.boss.Boss;
import ge.tbegvadze.toon3d.entity.boss.BossVisualState;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;

/**
 * Renders the boss encounter HUD overlay:
 *   1. Intro letterbox — black bar slides in from the top for the first 40% of BOSS_INTRO_DURATION_SECONDS.
 *   2. Boss name/epithet banners — shown inside the letterbox during the intro; separately on phase 2.
 *   3. Full-screen HP bar — runs just below the letterbox, tinted with the boss accent color.
 *
 * Call update(deltaTime) every frame while a boss encounter is active.
 * Call showIntro() when the boss awakens; showPhaseBanner(text) on phase transition.
 * Set the boss reference via setBoss() before the first render call.
 *
 * All allocations happen in the constructor; render() is allocation-free.
 */
public final class BossHudRenderer implements Renderable, Disposable {

    private static final float WORLD_WIDTH  = Constants.WORLD_WIDTH;
    private static final float WORLD_HEIGHT = Constants.WORLD_HEIGHT;

    private static final float SLIDE_DURATION =
            Constants.BOSS_INTRO_DURATION_SECONDS * HudConstants.BOSS_INTRO_SLIDE_FRACTION;

    // HP bar anchor — sits just below the letterbox bottom edge
    private static final float HP_BAR_TOP = WORLD_HEIGHT - HudConstants.BOSS_LETTERBOX_HEIGHT;
    private static final float HP_BAR_Y   = HP_BAR_TOP - HudConstants.BOSS_HP_BAR_HEIGHT - HudConstants.BOSS_HP_BAR_ABOVE_GAP;
    private static final float HP_BAR_X   = HudConstants.BOSS_HP_BAR_MARGIN;
    private static final float HP_BAR_W   = WORLD_WIDTH - HudConstants.BOSS_HP_BAR_MARGIN * 2f;

    private final SpriteBatch   batch;
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont    nameFont;
    private final BitmapFont    epithetFont;
    private final GlyphLayout   glyphLayout;

    private Boss    boss          = null;
    private boolean active        = false;
    private boolean introPlaying  = false;
    private float   introTimer    = 0f;
    private boolean bannerPlaying = false;
    private float   bannerTimer   = 0f;
    private String  bannerText    = "";

    // Heal tick-up flash (ORDER 8) — previousHealth detects an HP RISE (a repair turn); the timer decays
    // the green flash over the fill so the heal is impossible to miss.
    private int   previousHealth  = 0;
    private float healFlashTimer  = 0f;

    public BossHudRenderer() {
        this.batch         = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.nameFont      = new BitmapFont();
        this.epithetFont   = new BitmapFont();
        this.glyphLayout   = new GlyphLayout();
    }

    /** Sets the active boss; must be called before the first render. */
    public void setBoss(Boss boss) {
        this.boss           = boss;
        this.active         = boss != null;
        this.previousHealth = boss != null ? boss.health : 0;
        this.healFlashTimer = 0f;
    }

    /** Starts the intro letterbox + name banner sequence. */
    public void showIntro() {
        introPlaying = true;
        introTimer   = 0f;
        if (boss != null) showBanner(boss.bossName);
    }

    /** Shows a phase-transition or named event banner for BOSS_BANNER_DURATION_SECONDS. */
    public void showBanner(String text) {
        bannerText    = text;
        bannerPlaying = true;
        bannerTimer   = 0f;
    }

    /** Hides all overlays (call after boss dies and rewards are collected). */
    public void hide() {
        active        = false;
        introPlaying  = false;
        bannerPlaying = false;
        boss          = null;
    }

    public void update(float deltaTime) {
        if (!active) return;
        // Heal tick-up detection (ORDER 8): any RISE in boss HP is a repair turn — arm the green flash.
        if (boss != null) {
            if (boss.health > previousHealth) {
                healFlashTimer = HudConstants.BOSS_HEAL_FLASH_DURATION_SECONDS;
            }
            previousHealth = boss.health;
        }
        if (healFlashTimer > 0f) {
            healFlashTimer = Math.max(0f, healFlashTimer - deltaTime);
        }
        if (introPlaying) {
            introTimer += deltaTime;
            if (introTimer >= Constants.BOSS_INTRO_DURATION_SECONDS) {
                introPlaying = false;
            }
        }
        if (bannerPlaying) {
            bannerTimer += deltaTime;
            if (bannerTimer >= HudConstants.BOSS_BANNER_DURATION_SECONDS) {
                bannerPlaying = false;
            }
        }
    }

    @Override
    public void render(OrthographicCamera camera) {
        Boss currentBoss = boss;
        if (!active || currentBoss == null) return;

        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        drawLetterbox(currentBoss);
        drawHpBar(currentBoss);
        if (bannerPlaying) drawBanner();
    }

    private void drawLetterbox(Boss currentBoss) {
        // Slide fraction: 0 → fully retracted, 1 → fully extended
        float slideProgress = introPlaying && introTimer < SLIDE_DURATION
                ? introTimer / SLIDE_DURATION
                : 1f;

        float letterboxH = HudConstants.BOSS_LETTERBOX_HEIGHT * slideProgress;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 1f);
        shapeRenderer.rect(0f, WORLD_HEIGHT - letterboxH, WORLD_WIDTH, letterboxH);
        shapeRenderer.end();

        // During the intro, draw name + epithet inside the letterbox once it's slid in enough
        if (introPlaying && slideProgress > 0.5f) {
            float textAlpha = Math.min(1f,
                    (slideProgress - 0.5f) / (1f - HudConstants.BOSS_INTRO_TEXT_RAMP_FRACTION));

            batch.begin();

            nameFont.getData().setScale(HudConstants.BOSS_INTRO_NAME_FONT_SCALE);
            glyphLayout.setText(nameFont, currentBoss.bossName);
            float nameWidth  = glyphLayout.width;
            float nameHeight = glyphLayout.height;
            float nameX = (WORLD_WIDTH - nameWidth) / 2f;
            float nameY = WORLD_HEIGHT - HudConstants.BOSS_LETTERBOX_HEIGHT / 2f
                          + nameHeight * 0.5f + HudConstants.BOSS_INTRO_NAME_Y_OFFSET;
            nameFont.setColor(currentBoss.accentRed, currentBoss.accentGreen,
                              currentBoss.accentBlue, textAlpha);
            nameFont.draw(batch, currentBoss.bossName, nameX, nameY);
            nameFont.getData().setScale(1f); // restore default scale

            epithetFont.getData().setScale(HudConstants.BOSS_INTRO_EPITHET_FONT_SCALE);
            glyphLayout.setText(epithetFont, currentBoss.bossEpithet);
            float epithetX = (WORLD_WIDTH - glyphLayout.width) / 2f;
            float epithetY = nameY - nameHeight - HudConstants.BOSS_INTRO_EPITHET_Y_GAP;
            epithetFont.setColor(0.8f, 0.8f, 0.8f, textAlpha);
            epithetFont.draw(batch, currentBoss.bossEpithet, epithetX, epithetY);
            epithetFont.getData().setScale(1f); // restore default scale

            batch.end();
        }
    }

    private void drawHpBar(Boss currentBoss) {
        float fillFraction = currentBoss.maxHealth > 0
                ? Math.max(0f, (float) currentBoss.health / currentBoss.maxHealth)
                : 0f;

        // Heal tick-up flash (ORDER 8): while the timer is live, blend the fill toward green so a repair
        // turn's rising bar is unmistakable — "it's healing, go stop it".
        float healFlash = healFlashTimer > 0f
                ? healFlashTimer / HudConstants.BOSS_HEAL_FLASH_DURATION_SECONDS
                : 0f;
        float fillRed   = GameMath.lerp(currentBoss.accentRed,   HudConstants.BOSS_HEAL_FLASH_R, healFlash);
        float fillGreen = GameMath.lerp(currentBoss.accentGreen, HudConstants.BOSS_HEAL_FLASH_G, healFlash);
        float fillBlue  = GameMath.lerp(currentBoss.accentBlue,  HudConstants.BOSS_HEAL_FLASH_B, healFlash);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Track background
        shapeRenderer.setColor(0.08f, 0.04f, 0.04f, 0.90f);
        shapeRenderer.rect(HP_BAR_X, HP_BAR_Y, HP_BAR_W, HudConstants.BOSS_HP_BAR_HEIGHT);

        // Fill tinted with boss accent colour (flashed green on a heal tick)
        shapeRenderer.setColor(fillRed, fillGreen, fillBlue, 1f);
        shapeRenderer.rect(HP_BAR_X, HP_BAR_Y, HP_BAR_W * fillFraction,
                           HudConstants.BOSS_HP_BAR_HEIGHT);

        // Phase-2 threshold marker at 50%
        float markerX = HP_BAR_X + HP_BAR_W * Constants.BOSS_PHASE2_HP_THRESHOLD;
        shapeRenderer.setColor(1f, 1f, 1f, 0.6f);
        shapeRenderer.rect(markerX - 1f, HP_BAR_Y, 2f, HudConstants.BOSS_HP_BAR_HEIGHT);

        // Top and bottom borders
        shapeRenderer.setColor(0f, 0f, 0f, 1f);
        shapeRenderer.rect(HP_BAR_X - 1f, HP_BAR_Y - 1f, HP_BAR_W + 2f, 1f);
        shapeRenderer.rect(HP_BAR_X - 1f, HP_BAR_Y + HudConstants.BOSS_HP_BAR_HEIGHT, HP_BAR_W + 2f, 1f);

        shapeRenderer.end();

        // Boss name label to the left of the HP bar
        batch.begin();
        nameFont.getData().setScale(HudConstants.BOSS_HP_NAME_FONT_SCALE);
        glyphLayout.setText(nameFont, currentBoss.bossName);
        float nameLabelY = HP_BAR_Y + HudConstants.BOSS_HP_BAR_HEIGHT * 0.5f
                           + glyphLayout.height * 0.5f;
        nameFont.setColor(currentBoss.accentRed, currentBoss.accentGreen,
                          currentBoss.accentBlue, 1f);
        nameFont.draw(batch, currentBoss.bossName, HP_BAR_X, nameLabelY);
        nameFont.getData().setScale(1f); // restore default scale

        // State label (ORDER 8) — the current tactical beat under the boss name. Hot (enraged) states
        // borrow the boss accent; the rest use a dim neutral so the label never competes with the name.
        BossVisualState visualState = currentBoss.visualState;
        if (visualState != null && !visualState.label.isEmpty()) {
            epithetFont.getData().setScale(HudConstants.BOSS_STATE_LABEL_FONT_SCALE);
            glyphLayout.setText(epithetFont, visualState.label);
            float stateLabelY = nameLabelY - glyphLayout.height - HudConstants.BOSS_STATE_LABEL_Y_GAP;
            if (visualState.hot) {
                epithetFont.setColor(currentBoss.accentRed, currentBoss.accentGreen,
                                     currentBoss.accentBlue, 1f);
            } else {
                epithetFont.setColor(HudConstants.BOSS_STATE_LABEL_R, HudConstants.BOSS_STATE_LABEL_G,
                                     HudConstants.BOSS_STATE_LABEL_B, 1f);
            }
            epithetFont.draw(batch, visualState.label, HP_BAR_X, stateLabelY);
            epithetFont.getData().setScale(1f); // restore default scale
        }

        batch.end();
    }

    private void drawBanner() {
        float fraction = bannerTimer / HudConstants.BOSS_BANNER_DURATION_SECONDS;
        float alpha;
        if (fraction < HudConstants.BOSS_BANNER_FADE_IN_FRACTION) {
            alpha = fraction / HudConstants.BOSS_BANNER_FADE_IN_FRACTION;
        } else if (fraction > HudConstants.BOSS_BANNER_FADE_OUT_START) {
            alpha = 1f - (fraction - HudConstants.BOSS_BANNER_FADE_OUT_START)
                    / (1f - HudConstants.BOSS_BANNER_FADE_OUT_START);
        } else {
            alpha = 1f;
        }

        batch.begin();
        epithetFont.getData().setScale(HudConstants.BOSS_BANNER_FONT_SCALE);
        glyphLayout.setText(epithetFont, bannerText);
        float bannerX = (WORLD_WIDTH - glyphLayout.width) / 2f;
        float bannerY = WORLD_HEIGHT * 0.55f;
        epithetFont.setColor(1f, 1f, 1f, alpha);
        epithetFont.draw(batch, bannerText, bannerX, bannerY);
        epithetFont.getData().setScale(1f); // restore default scale
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        nameFont.dispose();
        epithetFont.dispose();
    }

    public boolean isActive()       { return active; }
    public boolean isIntroPlaying() { return introPlaying; }

    private static Texture buildWhitePixelTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
