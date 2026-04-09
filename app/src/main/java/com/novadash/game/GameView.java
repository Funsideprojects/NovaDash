package com.novadash.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Full-screen SurfaceView that owns the game loop, all game objects, and all rendering.
 *
 * Game overview
 * ─────────────
 *  • The player steers a spaceship left/right by dragging a finger.
 *  • Meteors fall from the top and must be avoided.
 *  • Power-ups also fall and should be collected:
 *      [S]  Shield     – invincibility bubble (5 s)
 *      [T]  Slow Time  – halves meteor speed  (5 s)
 *      [♥]  Extra Life – adds one life (max 5)
 *      [2x] Score Boost – doubles score gain  (5 s)
 *  • The game speeds up every 10 seconds.
 *  • Three lives; losing all ends the game.
 *  • A shop lets players spend coins on consumables (Revive, Key) and
 *    permanent ship upgrades (Speed, Resistance, Duration).
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    // ── Constants ──────────────────────────────────────────────────────────────

    private static final int STAR_COUNT = 140;
    /** Duration (frames @ 60 fps) for timed power-ups. */
    private static final int POWERUP_DURATION = 300; // 5 seconds
    /** Frames of invincibility after being hit. */
    private static final int HIT_INVINCIBLE = 120;   // 2 seconds
    /** Frames between automatic difficulty increases. */
    private static final int DIFFICULTY_INTERVAL = 600; // 10 seconds

    // ── Game state ─────────────────────────────────────────────────────────────

    private enum State { MENU, PLAYING, PAUSED, GAME_OVER, SHOP }
    private volatile State gameState = State.MENU;

    // ── Screen ─────────────────────────────────────────────────────────────────

    private int screenW, screenH;
    private boolean sizeReady = false;

    // ── Threading ──────────────────────────────────────────────────────────────

    private GameThread gameThread;

    // ── Background stars ───────────────────────────────────────────────────────

    private float[] starX, starY, starR;
    private int[] starAlpha;
    private final Random rng = new Random();

    // ── Game objects ───────────────────────────────────────────────────────────

    private Player player;
    private final List<Meteor>   meteors   = new ArrayList<>();
    private final List<PowerUp>  powerUps  = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();

    // ── Scoring / lives ────────────────────────────────────────────────────────

    private long score;
    private int  lives;
    private int  highScore;

    // ── Shop / progression ─────────────────────────────────────────────────────

    /** Persistent coin balance. */
    private int coins;
    /** Coins earned in the current run (shown on game over). */
    private int coinsThisRun;
    /** 0–5: increases ship movement responsiveness. */
    private int speedUpgradeLevel;
    /** 0–5: adds one extra starting life per level. */
    private int resistanceUpgradeLevel;
    /** 0–5: extends power-up durations by 20 % per level. */
    private int durationUpgradeLevel;
    /** Consumable revives in inventory (max 5). */
    private int reviveCount;
    /** Consumable keys in inventory (max 10). */
    private int keyCount;
    /** State to return to when leaving the shop. */
    private State shopReturnState = State.MENU;

    // ── Shop UI ────────────────────────────────────────────────────────────────

    private static final int SHOP_REVIVE     = 0;
    private static final int SHOP_KEY        = 1;
    private static final int SHOP_SPEED      = 2;
    private static final int SHOP_RESISTANCE = 3;
    private static final int SHOP_DURATION   = 4;
    private static final int SHOP_ITEM_COUNT = 5;

    private final RectF[] shopItemRects = new RectF[SHOP_ITEM_COUNT];

    // ── Button hit-rects (updated every draw, read by touch handler) ───────────

    private final RectF playAgainBtnRect = new RectF();
    private final RectF shopBtnRect      = new RectF();
    private final RectF reviveBtnRect    = new RectF();
    private final RectF backBtnRect      = new RectF();
    private final RectF playBtnRect      = new RectF();
    /** HUD button to use a key during gameplay. */
    private final RectF keyBtnRect       = new RectF();

    // ── Power-up timers (in frames) ────────────────────────────────────────────

    private int shieldTimer;
    private int slowTimer;
    private int scoreBoostTimer;

    // ── Difficulty / spawn ─────────────────────────────────────────────────────

    private float difficulty       = 1.0f;
    private int   frameCount;
    private int   meteorInterval   = 60;   // frames between meteor spawns
    private static final int POWERUP_INTERVAL = 240;

    // ── Post-hit invincibility ─────────────────────────────────────────────────

    private int invincibleFrames;

    // ── Touch ──────────────────────────────────────────────────────────────────

    private float touchX = -1;

    // ── Paints ─────────────────────────────────────────────────────────────────

    private Paint bgPaint;
    private Paint starPaint;
    private Paint titlePaint;
    private Paint subtitlePaint;
    private Paint legendPaint;
    private Paint gameOverPaint;
    private Paint scorePaint;
    private Paint bestPaint;
    private Paint buttonPaint;
    private Paint buttonTextPaint;
    private Paint hudPaint;
    private Paint hudShadowPaint;
    private Paint livesPaint;
    private Paint puHudPaint;
    private Paint overlayPaint;

    // ── Shop-specific paints ───────────────────────────────────────────────────

    private Paint shopItemBgPaint;
    private Paint shopItemNamePaint;
    private Paint shopItemDescPaint;
    private Paint shopCostPaint;
    private Paint shopMaxPaint;
    private Paint shopIconPaint;

    // ══════════════════════════════════════════════════════════════════════════
    // Construction / Surface lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        loadProgress();
        for (int i = 0; i < SHOP_ITEM_COUNT; i++) {
            shopItemRects[i] = new RectF();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        gameThread = new GameThread(holder, this);
        gameThread.setRunning(true);
        gameThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        screenW = width;
        screenH = height;
        if (!sizeReady) {
            initPaints();
            initStars();
            sizeReady = true;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopGameThread();
    }

    private void stopGameThread() {
        if (gameThread == null) return;
        gameThread.setRunning(false);
        boolean retry = true;
        while (retry) {
            try {
                gameThread.join();
                retry = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Called by MainActivity
    // ══════════════════════════════════════════════════════════════════════════

    public void onGamePause() {
        if (gameState == State.PLAYING) {
            gameState = State.PAUSED;
        }
    }

    public void onGameResume() {
        // Thread restart is handled by the surface lifecycle; nothing to do here.
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Initialisation helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void initPaints() {
        bgPaint = new Paint();
        bgPaint.setColor(Color.rgb(4, 4, 18));

        starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setColor(Color.WHITE);

        float titleSize = screenH * 0.082f;
        titlePaint = makePaint(Color.rgb(90, 200, 255), titleSize, Paint.Align.CENTER, true);

        subtitlePaint = makePaint(Color.rgb(190, 225, 255), screenH * 0.036f,
                Paint.Align.CENTER, false);

        legendPaint = makePaint(Color.rgb(160, 200, 255), screenH * 0.026f,
                Paint.Align.CENTER, false);

        gameOverPaint = makePaint(Color.rgb(255, 70, 70), screenH * 0.074f,
                Paint.Align.CENTER, true);

        scorePaint = makePaint(Color.rgb(255, 220, 80), screenH * 0.052f,
                Paint.Align.CENTER, true);

        bestPaint = makePaint(Color.rgb(200, 220, 255), screenH * 0.034f,
                Paint.Align.CENTER, false);

        buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonPaint.setColor(Color.rgb(40, 130, 255));
        buttonPaint.setStyle(Paint.Style.FILL);

        buttonTextPaint = makePaint(Color.WHITE, screenH * 0.042f, Paint.Align.CENTER, true);

        hudPaint = makePaint(Color.WHITE, screenH * 0.036f, Paint.Align.LEFT, true);

        hudShadowPaint = makePaint(Color.BLACK, screenH * 0.036f, Paint.Align.LEFT, true);
        hudShadowPaint.setAlpha(160);

        livesPaint = makePaint(Color.rgb(255, 70, 100), screenH * 0.036f,
                Paint.Align.RIGHT, true);

        puHudPaint = makePaint(Color.WHITE, screenH * 0.026f, Paint.Align.LEFT, true);

        overlayPaint = new Paint();
        overlayPaint.setColor(Color.argb(160, 0, 0, 10));

        // Shop paints
        shopItemBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shopItemBgPaint.setStyle(Paint.Style.FILL);

        shopItemNamePaint = makePaint(Color.WHITE, screenH * 0.032f, Paint.Align.LEFT, true);
        shopItemDescPaint = makePaint(Color.rgb(180, 200, 230), screenH * 0.022f, Paint.Align.LEFT, false);
        shopCostPaint     = makePaint(Color.rgb(255, 220, 60), screenH * 0.028f, Paint.Align.RIGHT, true);
        shopMaxPaint      = makePaint(Color.rgb(100, 255, 120), screenH * 0.028f, Paint.Align.RIGHT, true);
        shopIconPaint     = makePaint(Color.WHITE, screenH * 0.040f, Paint.Align.CENTER, true);
    }

    private static Paint makePaint(int color, float textSize, Paint.Align align, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(textSize);
        p.setTextAlign(align);
        p.setFakeBoldText(bold);
        return p;
    }

    private void initStars() {
        starX     = new float[STAR_COUNT];
        starY     = new float[STAR_COUNT];
        starR     = new float[STAR_COUNT];
        starAlpha = new int[STAR_COUNT];
        float baseStarRadius  = screenW * 0.0005f;
        float starRadiusRange = screenW * 0.002f;
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i]     = rng.nextFloat() * screenW;
            starY[i]     = rng.nextFloat() * screenH;
            starR[i]     = baseStarRadius + rng.nextFloat() * starRadiusRange;
            starAlpha[i] = 80 + rng.nextInt(175);
        }
    }

    private void startGame() {
        player = new Player(screenW, screenH, speedUpgradeLevel);
        meteors.clear();
        powerUps.clear();
        particles.clear();

        score            = 0;
        coinsThisRun     = 0;
        lives            = 3 + resistanceUpgradeLevel;
        frameCount       = 0;
        difficulty       = 1.0f;
        meteorInterval   = 60;
        shieldTimer      = 0;
        slowTimer        = 0;
        scoreBoostTimer  = 0;
        invincibleFrames = 0;
        touchX           = screenW / 2f;

        gameState = State.PLAYING;
    }

    /** Duration (frames) for timed power-ups, extended by the duration upgrade level. */
    private int powerUpDuration() {
        return (int)(POWERUP_DURATION * (1.0f + durationUpgradeLevel * 0.2f));
    }

    private static final String PREFS_NAME       = "novadash";
    private static final String PREF_HIGH_SCORE  = "high_score";
    private static final String PREF_COINS       = "coins";
    private static final String PREF_SPEED_LVL   = "speed_level";
    private static final String PREF_RESIST_LVL  = "resistance_level";
    private static final String PREF_DURATION_LVL= "duration_level";
    private static final String PREF_REVIVES     = "revives";
    private static final String PREF_KEYS        = "keys";

    private void loadProgress() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        highScore            = prefs.getInt(PREF_HIGH_SCORE, 0);
        coins                = prefs.getInt(PREF_COINS, 0);
        speedUpgradeLevel    = prefs.getInt(PREF_SPEED_LVL, 0);
        resistanceUpgradeLevel = prefs.getInt(PREF_RESIST_LVL, 0);
        durationUpgradeLevel = prefs.getInt(PREF_DURATION_LVL, 0);
        reviveCount          = prefs.getInt(PREF_REVIVES, 0);
        keyCount             = prefs.getInt(PREF_KEYS, 0);
    }

    private void saveProgress() {
        SharedPreferences.Editor ed = getContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        ed.putInt(PREF_HIGH_SCORE,   highScore);
        ed.putInt(PREF_COINS,        coins);
        ed.putInt(PREF_SPEED_LVL,    speedUpgradeLevel);
        ed.putInt(PREF_RESIST_LVL,   resistanceUpgradeLevel);
        ed.putInt(PREF_DURATION_LVL, durationUpgradeLevel);
        ed.putInt(PREF_REVIVES,      reviveCount);
        ed.putInt(PREF_KEYS,         keyCount);
        ed.apply();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Game loop — called from GameThread
    // ══════════════════════════════════════════════════════════════════════════

    /** Updates game logic. Called from the game thread before rendering. */
    public void update() {
        if (!sizeReady) return;

        // Twinkle stars regardless of game state
        for (int i = 0; i < STAR_COUNT; i++) {
            if (rng.nextInt(40) == 0) {
                starAlpha[i] = 80 + rng.nextInt(175);
            }
        }

        if (gameState != State.PLAYING) return;

        frameCount++;

        // ── Score ────────────────────────────────────────────────────────────
        score += (scoreBoostTimer > 0) ? 2 : 1;

        // ── Difficulty ramp-up every 10 s ────────────────────────────────────
        if (frameCount % DIFFICULTY_INTERVAL == 0) {
            difficulty     += 0.15f;
            meteorInterval  = Math.max(18, (int) (60f / difficulty));
        }

        // ── Move player to touch position ────────────────────────────────────
        if (touchX >= 0) {
            player.setTargetX(touchX, screenW);
        }
        player.update();

        // ── Spawn meteors ────────────────────────────────────────────────────
        if (frameCount % meteorInterval == 0) {
            meteors.add(new Meteor(screenW, screenH, difficulty));
            // Extra meteor at higher difficulties
            if (difficulty >= 2.0f && rng.nextFloat() < 0.35f) {
                meteors.add(new Meteor(screenW, screenH, difficulty));
            }
        }

        // ── Spawn power-ups ──────────────────────────────────────────────────
        if (frameCount % POWERUP_INTERVAL == 0) {
            powerUps.add(new PowerUp(screenW, screenH));
        }

        float slowFactor = (slowTimer > 0) ? 0.4f : 1.0f;

        // ── Update & check meteors ───────────────────────────────────────────
        Iterator<Meteor> mi = meteors.iterator();
        while (mi.hasNext()) {
            Meteor m = mi.next();
            m.update(slowFactor);

            if (!m.isActive()) {
                mi.remove();
                continue;
            }

            if (player.isShielded()) {
                // Shield deflects the meteor
                if (player.collidesWith(m.getX(), m.getY(), m.getRadius())) {
                    spawnParticles(m.getX(), m.getY(), Color.rgb(80, 160, 255), 10);
                    m.deactivate();
                    mi.remove();
                    score += 15;
                }
            } else if (invincibleFrames <= 0) {
                if (player.collidesWith(m.getX(), m.getY(), m.getRadius())) {
                    spawnParticles(m.getX(), m.getY(), Color.rgb(255, 130, 40), 14);
                    m.deactivate();
                    mi.remove();
                    lives--;
                    invincibleFrames = HIT_INVINCIBLE;
                    if (lives <= 0) {
                        coinsThisRun = (int)(score / 50);
                        coins += coinsThisRun;
                        if (score > highScore) highScore = (int) score;
                        saveProgress();
                        gameState = State.GAME_OVER;
                    }
                }
            }
        }

        // ── Update & collect power-ups ───────────────────────────────────────
        Iterator<PowerUp> pi = powerUps.iterator();
        while (pi.hasNext()) {
            PowerUp pu = pi.next();
            pu.update(screenH);

            if (!pu.isActive()) {
                pi.remove();
                continue;
            }

            if (player.collidesWith(pu.getX(), pu.getY(), pu.getRadius())) {
                applyPowerUp(pu);
                spawnParticles(pu.getX(), pu.getY(), pu.getColor(), 12);
                score += 50;
                pi.remove();
            }
        }

        // ── Tick power-up timers ─────────────────────────────────────────────
        if (shieldTimer > 0) {
            shieldTimer--;
            player.setShielded(true);
        } else {
            player.setShielded(false);
        }
        if (slowTimer      > 0) slowTimer--;
        if (scoreBoostTimer > 0) scoreBoostTimer--;
        if (invincibleFrames > 0) invincibleFrames--;

        // ── Particles ────────────────────────────────────────────────────────
        Iterator<Particle> parti = particles.iterator();
        while (parti.hasNext()) {
            Particle p = parti.next();
            p.update();
            if (!p.isActive()) parti.remove();
        }
    }

    private void applyPowerUp(PowerUp pu) {
        int duration = powerUpDuration();
        switch (pu.getType()) {
            case SHIELD:
                shieldTimer = duration;
                break;
            case SLOW_TIME:
                slowTimer = duration;
                break;
            case EXTRA_LIFE:
                lives = Math.min(lives + 1, 5 + resistanceUpgradeLevel);
                break;
            case SCORE_BOOST:
                scoreBoostTimer = duration;
                break;
        }
    }

    private void spawnParticles(float x, float y, int color, int count) {
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(x, y, color, screenW, screenH));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Rendering — called from GameThread
    // ══════════════════════════════════════════════════════════════════════════

    /** Full frame render. Called from the game thread with a locked SurfaceHolder canvas. */
    public void render(Canvas canvas) {
        if (!sizeReady || canvas == null) return;

        // Deep-space background
        canvas.drawRect(0, 0, screenW, screenH, bgPaint);

        // Stars
        for (int i = 0; i < STAR_COUNT; i++) {
            starPaint.setAlpha(starAlpha[i]);
            canvas.drawCircle(starX[i], starY[i], starR[i], starPaint);
        }

        // Particles (always visible)
        for (Particle p : particles) p.draw(canvas);

        switch (gameState) {
            case MENU:
                drawMenu(canvas);
                break;
            case PLAYING:
                drawGameObjects(canvas);
                drawHUD(canvas);
                break;
            case PAUSED:
                drawGameObjects(canvas);
                drawHUD(canvas);
                drawPauseOverlay(canvas);
                break;
            case GAME_OVER:
                drawGameObjects(canvas);
                drawHUD(canvas);
                drawGameOverOverlay(canvas);
                break;
            case SHOP:
                drawShop(canvas);
                break;
        }
    }

    private void drawGameObjects(Canvas canvas) {
        for (Meteor  m  : meteors)  m.draw(canvas);
        for (PowerUp pu : powerUps) pu.draw(canvas);

        // Player flickers during post-hit invincibility
        boolean showPlayer = (invincibleFrames <= 0) || (invincibleFrames % 10 < 6);
        if (showPlayer) player.draw(canvas);
    }

    private void drawHUD(Canvas canvas) {
        float pad  = screenW * 0.03f;
        float topY = screenH * 0.055f;

        // Score (left)
        String scoreStr = "SCORE: " + score;
        float shadowOff = screenH * 0.001f;
        hudShadowPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(scoreStr, pad + shadowOff, topY + shadowOff, hudShadowPaint);
        canvas.drawText(scoreStr, pad, topY, hudPaint);

        // Lives (right) — up to (5 + resistanceLevel) hearts
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < lives; i++) {
            if (i > 0) hearts.append(' ');
            hearts.append('\u2665');
        }
        canvas.drawText(hearts.toString(), screenW - pad, topY, livesPaint);

        // Active power-up indicators
        float puY = topY + screenH * 0.045f;
        if (shieldTimer > 0) {
            puHudPaint.setColor(Color.rgb(80, 150, 255));
            canvas.drawText("SHIELD  " + secondsLeft(shieldTimer) + "s", pad, puY, puHudPaint);
            puY += screenH * 0.034f;
        }
        if (slowTimer > 0) {
            puHudPaint.setColor(Color.rgb(255, 215, 0));
            canvas.drawText("SLOW    " + secondsLeft(slowTimer) + "s", pad, puY, puHudPaint);
            puY += screenH * 0.034f;
        }
        if (scoreBoostTimer > 0) {
            puHudPaint.setColor(Color.rgb(60, 240, 100));
            canvas.drawText("2x SCORE " + secondsLeft(scoreBoostTimer) + "s", pad, puY, puHudPaint);
        }

        // Consumable inventory — top-right, below hearts
        float invY = topY + screenH * 0.045f;
        puHudPaint.setTextAlign(Paint.Align.RIGHT);
        if (reviveCount > 0) {
            puHudPaint.setColor(Color.rgb(255, 80, 110));
            canvas.drawText("\u2665 " + reviveCount, screenW - pad, invY, puHudPaint);
            invY += screenH * 0.034f;
        }
        if (keyCount > 0) {
            // Draw KEY button (tappable)
            float btnH = screenH * 0.055f;
            float btnW = screenW * 0.24f;
            float btnX = screenW - pad - btnW;
            float btnCY = invY + btnH / 2f;
            keyBtnRect.set(btnX, invY, btnX + btnW, invY + btnH);
            Paint keyBg = new Paint(Paint.ANTI_ALIAS_FLAG);
            keyBg.setColor(Color.argb(200, 180, 130, 20));
            keyBg.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(keyBtnRect, btnH * 0.25f, btnH * 0.25f, keyBg);
            puHudPaint.setColor(Color.rgb(255, 215, 60));
            puHudPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("\uD83D\uDD11 KEY x" + keyCount,
                    btnX + btnW / 2f, btnCY + puHudPaint.getTextSize() * 0.36f, puHudPaint);
            puHudPaint.setTextAlign(Paint.Align.RIGHT);
        } else {
            keyBtnRect.setEmpty();
        }
        puHudPaint.setTextAlign(Paint.Align.LEFT);
    }

    private static int secondsLeft(int frames) {
        return (frames / 60) + 1;
    }

    private void drawMenu(Canvas canvas) {
        float cx = screenW / 2f;
        float cy = screenH / 2f;

        float maxW = screenW * 0.92f;

        drawTextFitted(canvas, "NOVA DASH", cx, cy - screenH * 0.18f, titlePaint, maxW);

        subtitlePaint.setColor(Color.rgb(190, 225, 255));
        drawTextFitted(canvas, "Dodge meteors. Collect power-ups.",
                cx, cy - screenH * 0.09f, subtitlePaint, maxW);
        drawTextFitted(canvas, "Drag your finger to steer the ship.",
                cx, cy - screenH * 0.04f, subtitlePaint, maxW);

        drawTextFitted(canvas, "[S] Shield   [T] Slow Time",
                cx, cy + screenH * 0.03f, legendPaint, maxW);
        drawTextFitted(canvas, "[\u2665] Extra Life   [2x] Score Boost",
                cx, cy + screenH * 0.09f, legendPaint, maxW);

        if (highScore > 0) {
            bestPaint.setColor(Color.rgb(255, 220, 80));
            drawTextFitted(canvas, "BEST: " + highScore, cx, cy + screenH * 0.16f, bestPaint, maxW);
        }

        // Coin balance
        bestPaint.setColor(Color.rgb(255, 215, 60));
        drawTextFitted(canvas, "\u2605 " + coins + " coins", cx, cy + screenH * 0.22f, bestPaint, maxW);

        drawButton(canvas, cx - screenW * 0.24f, cy + screenH * 0.31f, "PLAY", playBtnRect);
        drawButton(canvas, cx + screenW * 0.24f, cy + screenH * 0.31f, "SHOP", shopBtnRect);
    }

    private void drawGameOverOverlay(Canvas canvas) {
        canvas.drawRect(0, 0, screenW, screenH, overlayPaint);

        float cx = screenW / 2f;
        float cy = screenH / 2f;

        float maxW = screenW * 0.92f;

        drawTextFitted(canvas, "GAME OVER", cx, cy - screenH * 0.14f, gameOverPaint, maxW);
        drawTextFitted(canvas, "Score: " + score, cx, cy - screenH * 0.03f, scorePaint, maxW);

        // Coins earned this run
        bestPaint.setColor(Color.rgb(255, 215, 60));
        drawTextFitted(canvas, "+" + coinsThisRun + " coins  (\u2605 " + coins + " total)",
                cx, cy + screenH * 0.04f, bestPaint, maxW);

        if (score >= highScore && highScore > 0) {
            Paint newBest = new Paint(scorePaint);
            newBest.setColor(Color.rgb(255, 200, 50));
            newBest.setTextSize(screenH * 0.038f);
            drawTextFitted(canvas, "NEW BEST!", cx, cy + screenH * 0.10f, newBest, maxW);
        } else if (highScore > 0) {
            drawTextFitted(canvas, "Best: " + highScore, cx, cy + screenH * 0.10f, bestPaint, maxW);
        }

        float btnY = cy + screenH * 0.20f;
        if (reviveCount > 0) {
            // Revive button (green)
            Paint revivePaint = new Paint(buttonPaint);
            revivePaint.setColor(Color.rgb(30, 160, 60));
            drawButtonColored(canvas, cx, btnY, "REVIVE (" + reviveCount + ")", revivePaint, reviveBtnRect);
            btnY += screenH * 0.105f;
        } else {
            reviveBtnRect.setEmpty();
        }

        drawButton(canvas, cx - screenW * 0.24f, btnY, "PLAY AGAIN", playAgainBtnRect);
        drawButton(canvas, cx + screenW * 0.24f, btnY, "SHOP", shopBtnRect);
    }

    private void drawPauseOverlay(Canvas canvas) {
        canvas.drawRect(0, 0, screenW, screenH, overlayPaint);
        float cx = screenW / 2f;
        float cy = screenH / 2f;
        float maxW = screenW * 0.92f;
        drawTextFitted(canvas, "PAUSED", cx, cy - screenH * 0.04f, titlePaint, maxW);
        drawTextFitted(canvas, "Tap to resume", cx, cy + screenH * 0.05f, subtitlePaint, maxW);
    }

    /**
     * Draws text centred at (cx, y) exactly like {@code canvas.drawText}, but automatically
     * reduces the paint's text size so the string never exceeds {@code maxWidth}.
     * The original text size is restored after drawing.
     */
    private void drawTextFitted(Canvas canvas, String text, float cx, float y,
                                Paint paint, float maxWidth) {
        float original = paint.getTextSize();
        float measured = paint.measureText(text);
        if (measured > maxWidth) {
            paint.setTextSize(original * maxWidth / measured);
        }
        canvas.drawText(text, cx, y, paint);
        paint.setTextSize(original);
    }

    private void drawButton(Canvas canvas, float cx, float cy, String label) {
        drawButton(canvas, cx, cy, label, null);
    }

    private void drawButton(Canvas canvas, float cx, float cy, String label, RectF outRect) {
        drawButtonColored(canvas, cx, cy, label, buttonPaint, outRect);
    }

    private void drawButtonColored(Canvas canvas, float cx, float cy, String label,
                                   Paint bgPaint2, RectF outRect) {
        float btnW = screenW * 0.42f;
        float btnH = screenH * 0.075f;
        float cornerRadius = screenH * 0.015f;
        RectF rect = new RectF(cx - btnW / 2, cy - btnH / 2, cx + btnW / 2, cy + btnH / 2);
        if (outRect != null) outRect.set(rect);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint2);
        drawTextFitted(canvas, label, cx, cy + buttonTextPaint.getTextSize() * 0.36f,
                buttonTextPaint, btnW * 0.9f);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Shop
    // ══════════════════════════════════════════════════════════════════════════

    /** Icon characters for each shop item. */
    private static final String[] SHOP_ICONS = { "\u2665", "\uD83D\uDD11", "\u26A1", "\u2764", "\u23F1" };
    /** Display names for each shop item. */
    private static final String[] SHOP_NAMES = { "Revive", "Key", "Speed", "Resistance", "Duration" };
    /** Short descriptions for each shop item. */
    private static final String[] SHOP_DESC  = {
        "Continue with 1 life",
        "Spawn a power-up",
        "Snappier ship control",
        "+1 starting life",
        "Longer power-ups"
    };
    /** Background accent colors per shop item. */
    private static final int[] SHOP_COLORS = {
        Color.rgb(160, 30,  70),   // red   – revive
        Color.rgb(140, 110,  0),   // gold  – key
        Color.rgb(20,  100, 200),  // blue  – speed
        Color.rgb(20,  140,  60),  // green – resistance
        Color.rgb(120,  30, 180)   // purple– duration
    };

    private int shopItemCurrentLevel(int id) {
        switch (id) {
            case SHOP_REVIVE:     return reviveCount;
            case SHOP_KEY:        return keyCount;
            case SHOP_SPEED:      return speedUpgradeLevel;
            case SHOP_RESISTANCE: return resistanceUpgradeLevel;
            case SHOP_DURATION:   return durationUpgradeLevel;
        }
        return 0;
    }

    private int shopItemMaxLevel(int id) {
        switch (id) {
            case SHOP_REVIVE: return 5;
            case SHOP_KEY:    return 10;
            default:          return 5;  // upgrades 0-5
        }
    }

    private int shopItemCost(int id) {
        int level = shopItemCurrentLevel(id);
        switch (id) {
            case SHOP_REVIVE:     return 50;
            case SHOP_KEY:        return 30;
            case SHOP_SPEED:      return 100 * (level + 1);
            case SHOP_RESISTANCE: return 100 * (level + 1);
            case SHOP_DURATION:   return  80 * (level + 1);
        }
        return 0;
    }

    private boolean shopItemIsMax(int id) {
        return shopItemCurrentLevel(id) >= shopItemMaxLevel(id);
    }

    /** Attempt to purchase item; returns true if successful. */
    private boolean buyShopItem(int id) {
        if (shopItemIsMax(id)) return false;
        int cost = shopItemCost(id);
        if (coins < cost) return false;
        coins -= cost;
        switch (id) {
            case SHOP_REVIVE:     reviveCount          = Math.min(reviveCount + 1, 5); break;
            case SHOP_KEY:        keyCount             = Math.min(keyCount + 1, 10);   break;
            case SHOP_SPEED:      speedUpgradeLevel++; break;
            case SHOP_RESISTANCE: resistanceUpgradeLevel++; break;
            case SHOP_DURATION:   durationUpgradeLevel++;   break;
        }
        saveProgress();
        return true;
    }

    private void drawShop(Canvas canvas) {
        float cx  = screenW / 2f;
        float pad = screenW * 0.04f;
        float maxW = screenW * 0.92f;

        // Title
        drawTextFitted(canvas, "SHOP", cx, screenH * 0.09f, titlePaint, maxW);

        // Coin balance
        scorePaint.setColor(Color.rgb(255, 215, 60));
        drawTextFitted(canvas, "\u2605 " + coins + " coins", cx, screenH * 0.155f, scorePaint, maxW);
        scorePaint.setColor(Color.rgb(255, 220, 80)); // restore

        // Item list
        float itemH  = screenH * 0.108f;
        float gap    = screenH * 0.016f;
        float startY = screenH * 0.195f;
        float itemW  = screenW - 2 * pad;

        for (int i = 0; i < SHOP_ITEM_COUNT; i++) {
            float y = startY + i * (itemH + gap);
            drawShopItem(canvas, pad, y, itemW, itemH, i);
        }

        // Back button
        drawButton(canvas, cx, screenH * 0.935f, "BACK", backBtnRect);
    }

    private void drawShopItem(Canvas canvas, float left, float top, float w, float h, int id) {
        // Background
        RectF bg = shopItemRects[id];
        bg.set(left, top, left + w, top + h);
        shopItemBgPaint.setColor(SHOP_COLORS[id]);
        shopItemBgPaint.setAlpha(180);
        canvas.drawRoundRect(bg, h * 0.15f, h * 0.15f, shopItemBgPaint);

        // Icon circle
        float iconR = h * 0.36f;
        float iconX = left + iconR + h * 0.08f;
        float iconCY = top + h * 0.5f;
        Paint iconCircle = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconCircle.setColor(Color.argb(120, 255, 255, 255));
        iconCircle.setStyle(Paint.Style.FILL);
        canvas.drawCircle(iconX, iconCY, iconR, iconCircle);
        canvas.drawText(SHOP_ICONS[id], iconX, iconCY + shopIconPaint.getTextSize() * 0.36f, shopIconPaint);

        // Name + description
        float textX = iconX + iconR + h * 0.12f;
        canvas.drawText(SHOP_NAMES[id], textX, top + h * 0.40f, shopItemNamePaint);
        canvas.drawText(SHOP_DESC[id],  textX, top + h * 0.70f, shopItemDescPaint);

        // Level / count and cost on the right
        boolean isMax      = shopItemIsMax(id);
        int currentLevel   = shopItemCurrentLevel(id);
        int maxLevel       = shopItemMaxLevel(id);
        String levelStr    = currentLevel + " / " + maxLevel;
        float rightX       = left + w - h * 0.12f;

        shopItemNamePaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(levelStr, rightX, top + h * 0.40f, shopItemNamePaint);
        shopItemNamePaint.setTextAlign(Paint.Align.LEFT);

        if (isMax) {
            shopMaxPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("MAX", rightX, top + h * 0.72f, shopMaxPaint);
            shopMaxPaint.setTextAlign(Paint.Align.LEFT);
        } else {
            int cost = shopItemCost(id);
            String costStr = "\u2605 " + cost;
            shopCostPaint.setTextAlign(Paint.Align.RIGHT);
            shopCostPaint.setColor(coins >= cost ? Color.rgb(255, 220, 60) : Color.rgb(200, 80, 80));
            canvas.drawText(costStr, rightX, top + h * 0.72f, shopCostPaint);
            shopCostPaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Input
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float ex = event.getX();
        float ey = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (gameState == State.PLAYING) {
                    touchX = ex;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (gameState == State.MENU) {
                    if (shopBtnRect.contains(ex, ey)) {
                        shopReturnState = State.MENU;
                        gameState = State.SHOP;
                    } else {
                        // Tapping anywhere else (including PLAY button) starts the game
                        startGame();
                    }
                } else if (gameState == State.GAME_OVER) {
                    if (!reviveBtnRect.isEmpty() && reviveBtnRect.contains(ex, ey)) {
                        // Use a revive
                        reviveCount--;
                        saveProgress();
                        lives = 1;
                        invincibleFrames = HIT_INVINCIBLE * 2;
                        gameState = State.PLAYING;
                    } else if (shopBtnRect.contains(ex, ey)) {
                        shopReturnState = State.GAME_OVER;
                        gameState = State.SHOP;
                    } else {
                        startGame();
                    }
                } else if (gameState == State.PAUSED) {
                    gameState = State.PLAYING;
                } else if (gameState == State.PLAYING) {
                    // Use a key (spawns a guaranteed power-up) if tapping KEY button
                    if (keyCount > 0 && !keyBtnRect.isEmpty() && keyBtnRect.contains(ex, ey)) {
                        keyCount--;
                        powerUps.add(new PowerUp(screenW, screenH));
                        saveProgress();
                    }
                } else if (gameState == State.SHOP) {
                    if (backBtnRect.contains(ex, ey)) {
                        gameState = shopReturnState;
                    } else {
                        // Check if any shop item was tapped
                        for (int i = 0; i < SHOP_ITEM_COUNT; i++) {
                            if (shopItemRects[i].contains(ex, ey)) {
                                buyShopItem(i);
                                break;
                            }
                        }
                    }
                }
                break;
        }
        return true;
    }
}
