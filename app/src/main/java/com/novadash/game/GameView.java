package com.novadash.game;

import android.content.Context;
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

    private enum State { MENU, PLAYING, PAUSED, GAME_OVER }
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

    // ══════════════════════════════════════════════════════════════════════════
    // Construction / Surface lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
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
        player = new Player(screenW, screenH);
        meteors.clear();
        powerUps.clear();
        particles.clear();

        score            = 0;
        lives            = 3;
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
                        gameState = State.GAME_OVER;
                        if (score > highScore) highScore = (int) score;
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
        switch (pu.getType()) {
            case SHIELD:
                shieldTimer = POWERUP_DURATION;
                break;
            case SLOW_TIME:
                slowTimer = POWERUP_DURATION;
                break;
            case EXTRA_LIFE:
                lives = Math.min(lives + 1, 5);
                break;
            case SCORE_BOOST:
                scoreBoostTimer = POWERUP_DURATION;
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

        // Lives (right) — up to 5 hearts
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

        drawButton(canvas, cx, cy + screenH * 0.27f, "TAP TO PLAY");
    }

    private void drawGameOverOverlay(Canvas canvas) {
        canvas.drawRect(0, 0, screenW, screenH, overlayPaint);

        float cx = screenW / 2f;
        float cy = screenH / 2f;

        float maxW = screenW * 0.92f;

        drawTextFitted(canvas, "GAME OVER", cx, cy - screenH * 0.12f, gameOverPaint, maxW);
        drawTextFitted(canvas, "Score: " + score, cx, cy - screenH * 0.01f, scorePaint, maxW);

        if (score >= highScore && highScore > 0) {
            Paint newBest = new Paint(scorePaint);
            newBest.setColor(Color.rgb(255, 200, 50));
            newBest.setTextSize(screenH * 0.038f);
            drawTextFitted(canvas, "NEW BEST!", cx, cy + screenH * 0.06f, newBest, maxW);
        } else if (highScore > 0) {
            drawTextFitted(canvas, "Best: " + highScore, cx, cy + screenH * 0.06f, bestPaint, maxW);
        }

        drawButton(canvas, cx, cy + screenH * 0.18f, "PLAY AGAIN");
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
        float btnW = screenW * 0.42f;
        float btnH = screenH * 0.075f;
        float cornerRadius = screenH * 0.015f;
        RectF rect = new RectF(cx - btnW / 2, cy - btnH / 2, cx + btnW / 2, cy + btnH / 2);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, buttonPaint);
        drawTextFitted(canvas, label, cx, cy + buttonTextPaint.getTextSize() * 0.36f,
                buttonTextPaint, btnW * 0.9f);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Input
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float ex = event.getX();

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
                if (gameState == State.MENU || gameState == State.GAME_OVER) {
                    startGame();
                } else if (gameState == State.PAUSED) {
                    gameState = State.PLAYING;
                }
                break;
        }
        return true;
    }
}
