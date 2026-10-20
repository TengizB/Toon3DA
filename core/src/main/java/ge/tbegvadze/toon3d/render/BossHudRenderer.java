package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.CombatPalette;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;

/**
 * Renders the boss encounter HUD overlay:
 *   1. Intro letterbox — black bar slides in from the top for the first 40% of BOSS_INTRO_DURATION_SECONDS.
 *   2. Boss name/epithet banners — shown inside the letterbox during the intro; separately on phase 2.
 *   3. Full-screen HP bar — runs just below the letterbox: a UiKit panel + brackets around a segmented
 *      CombatPalette.DAMAGE meter (visual-identity-order-4 R12). The boss accent stays on the intro name
 *      and the hot state label.
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
        this.nameFont      = GameFonts.newFont(FontFace.HEAVY);
        this.epithetFont   = GameFonts.newFont(FontFace.REGULAR);
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
            UiKit.text(batch, nameFont, currentBoss.bossName, nameX, nameY,
                    currentBoss.accentRed, currentBoss.accentGreen, currentBoss.accentBlue, textAlpha);
            nameFont.getData().setScale(ArtDirection.TYPE_CAPTION); // restore the base step

            epithetFont.getData().setScale(HudConstants.BOSS_INTRO_EPITHET_FONT_SCALE);
            glyphLayout.setText(epithetFont, currentBoss.bossEpithet);
            float epithetX = (WORLD_WIDTH - glyphLayout.width) / 2f;
            float epithetY = nameY - nameHeight - HudConstants.BOSS_INTRO_EPITHET_Y_GAP;
            UiKit.text(batch, epithetFont, currentBoss.bossEpithet, epithetX, epithetY,
                    ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                    ArtDirection.UI_TEXT_SECONDARY_BLUE, textAlpha);
            epithetFont.getData().setScale(ArtDirection.TYPE_CAPTION); // restore the base step

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

        // R12: a UiKit panel + brackets framing a BOSS_HEALTH_SEGMENTS-cell meter in CombatPalette.DAMAGE (blended
        // toward the heal flash on a repair turn), with the phase threshold ticked in SUIT_AMBER.
        float fillRed   = GameMath.lerp(CombatPalette.DAMAGE_RED,   HudConstants.BOSS_HEAL_FLASH_R, healFlash);
        float fillGreen = GameMath.lerp(CombatPalette.DAMAGE_GREEN, HudConstants.BOSS_HEAL_FLASH_G, healFlash);
        float fillBlue  = GameMath.lerp(CombatPalette.DAMAGE_BLUE,  HudConstants.BOSS_HEAL_FLASH_B, healFlash);
        float padding = HudConstants.BOSS_HP_PANEL_PADDING;
        float panelX = HP_BAR_X - padding, panelY = HP_BAR_Y - padding;
        float panelWidth = HP_BAR_W + 2f * padding, panelHeight = HudConstants.BOSS_HP_BAR_HEIGHT + 2f * padding;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapeRenderer, panelX, panelY, panelWidth, panelHeight,
                ArtDirection.UI_PANEL_RED, ArtDirection.UI_PANEL_GREEN, ArtDirection.UI_PANEL_BLUE,
                ArtDirection.UI_PANEL_ALPHA_HUD,
                ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE);
        UiKit.brackets(shapeRenderer, panelX, panelY, panelWidth, panelHeight,
                ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN, ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);
        UiKit.meter(shapeRenderer, HP_BAR_X, HP_BAR_Y, HP_BAR_W, HudConstants.BOSS_HP_BAR_HEIGHT,
                fillFraction, ArtDirection.BOSS_HEALTH_SEGMENTS,
                fillRed, fillGreen, fillBlue,
                ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE);

        // Phase-2 threshold tick, standing proud of the meter by the panel padding
        float markerX = HP_BAR_X + HP_BAR_W * Constants.BOSS_PHASE2_HP_THRESHOLD;
        shapeRenderer.setColor(ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, 1f);
        shapeRenderer.rect(markerX - HudConstants.BOSS_PHASE_TICK_WIDTH / 2f, panelY,
                HudConstants.BOSS_PHASE_TICK_WIDTH, panelHeight);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Boss name label to the left of the HP bar
        batch.begin();
        nameFont.getData().setScale(HudConstants.BOSS_HP_NAME_FONT_SCALE);
        glyphLayout.setText(nameFont, currentBoss.bossName);
        float nameLabelY = HP_BAR_Y + HudConstants.BOSS_HP_BAR_HEIGHT * 0.5f
                           + glyphLayout.height * 0.5f;
        UiKit.text(batch, nameFont, currentBoss.bossName, HP_BAR_X + HudConstants.BOSS_HP_NAME_INSET_X, nameLabelY,
                ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
        nameFont.getData().setScale(ArtDirection.TYPE_CAPTION); // restore the base step

        // State label (ORDER 8) — the current tactical beat under the boss name. Hot (enraged) states
        // borrow the boss accent; the rest use a dim neutral so the label never competes with the name.
        BossVisualState visualState = currentBoss.visualState;
        if (visualState != null && !visualState.label.isEmpty()) {
            epithetFont.getData().setScale(HudConstants.BOSS_STATE_LABEL_FONT_SCALE);
            glyphLayout.setText(epithetFont, visualState.label);
            float stateLabelY = HP_BAR_Y - HudConstants.BOSS_HP_PANEL_PADDING - HudConstants.BOSS_STATE_LABEL_Y_GAP;
            if (visualState.hot) {
                UiKit.text(batch, epithetFont, visualState.label, HP_BAR_X, stateLabelY,
                        currentBoss.accentRed, currentBoss.accentGreen, currentBoss.accentBlue, 1f);
            } else {
                UiKit.text(batch, epithetFont, visualState.label, HP_BAR_X, stateLabelY,
                        HudConstants.BOSS_STATE_LABEL_R, HudConstants.BOSS_STATE_LABEL_G,
                        HudConstants.BOSS_STATE_LABEL_B, 1f);
            }
            epithetFont.getData().setScale(ArtDirection.TYPE_CAPTION); // restore the base step
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
        UiKit.text(batch, epithetFont, bannerText, bannerX, bannerY,
                ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE, alpha);
        epithetFont.getData().setScale(ArtDirection.TYPE_CAPTION); // restore the base step
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
