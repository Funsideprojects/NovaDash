package com.novadash.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Random;

/**
 * A locked treasure chest that drifts down from the top of the screen during a round.
 *
 * Opening requires a key (bought from the shop). Reward on opening is either a
 * random amount of coins (10–100) or a random power-up type.
 */
public class Chest {

    public enum RewardType { COINS, POWER_UP }

    private float x, y;
    private final float w, h;
    private final float speedY;

    private boolean active = true;

    private final RewardType rewardType;
    private final int        coinReward;
    private final PowerUp.Type powerUpReward;

    // Drawing paints
    private final Paint bodyPaint;
    private final Paint lidPaint;
    private final Paint rimPaint;
    private final Paint lockBodyPaint;
    private final Paint lockShacklePaint;
    private final Paint glowPaint;

    private static final Random RNG = new Random();

    public Chest(int screenWidth, int screenHeight) {
        w = screenWidth * 0.11f;
        h = w * 0.80f;
        x = w + RNG.nextFloat() * (screenWidth - 2 * w);
        y = -h;
        speedY = screenHeight * 0.0018f;

        // Determine reward at spawn time
        if (RNG.nextFloat() < 0.5f) {
            rewardType    = RewardType.COINS;
            coinReward    = 10 + RNG.nextInt(91); // 10–100 coins (inclusive)
            powerUpReward = null;
        } else {
            rewardType    = RewardType.POWER_UP;
            coinReward    = 0;
            PowerUp.Type[] types = PowerUp.Type.values();
            powerUpReward = types[RNG.nextInt(types.length)];
        }

        // Brown chest body
        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.rgb(155, 85, 25));
        bodyPaint.setStyle(Paint.Style.FILL);

        // Darker lid
        lidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lidPaint.setColor(Color.rgb(110, 58, 15));
        lidPaint.setStyle(Paint.Style.FILL);

        // Gold rim / bands
        rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rimPaint.setColor(Color.rgb(215, 170, 35));
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(w * 0.045f);

        // Gold lock body
        lockBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lockBodyPaint.setColor(Color.rgb(215, 170, 35));
        lockBodyPaint.setStyle(Paint.Style.FILL);

        // Darker gold shackle ring
        lockShacklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lockShacklePaint.setColor(Color.rgb(160, 120, 20));
        lockShacklePaint.setStyle(Paint.Style.STROKE);
        lockShacklePaint.setStrokeWidth(w * 0.04f);

        // Subtle amber glow so the chest stands out from the dark background
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(Color.rgb(215, 150, 30));
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAlpha(55);
    }

    /** Move the chest down the screen; deactivate when it exits the bottom. */
    public void update(int screenHeight) {
        y += speedY;
        if (y - h / 2f > screenHeight) {
            active = false;
        }
    }

    public void draw(Canvas canvas) {
        float left   = x - w / 2f;
        float right  = x + w / 2f;
        float top    = y - h / 2f;
        float bottom = y + h / 2f;
        float cornerR = w * 0.1f;
        float lidH    = h * 0.36f;                // height of the lid portion
        float divY    = top + lidH;               // y-coordinate of lid/body divide

        // Outer glow halo
        canvas.drawRoundRect(new RectF(left - w * 0.12f, top - h * 0.12f,
                right + w * 0.12f, bottom + h * 0.12f),
                cornerR * 2f, cornerR * 2f, glowPaint);

        // ── Body (lower portion) ─────────────────────────────────────────────
        canvas.drawRoundRect(new RectF(left, divY, right, bottom),
                cornerR, cornerR, bodyPaint);

        // ── Lid (upper portion) ──────────────────────────────────────────────
        canvas.drawRoundRect(new RectF(left, top, right, divY + cornerR),
                cornerR, cornerR, lidPaint);

        // ── Gold rim outline ─────────────────────────────────────────────────
        canvas.drawRoundRect(new RectF(left, top, right, bottom),
                cornerR, cornerR, rimPaint);

        // ── Horizontal divider band ──────────────────────────────────────────
        canvas.drawLine(left + cornerR, divY, right - cornerR, divY, rimPaint);

        // ── Vertical centre band ─────────────────────────────────────────────
        canvas.drawLine(x, divY + rimPaint.getStrokeWidth(),
                x, bottom - cornerR, rimPaint);
        canvas.drawLine(x, top + cornerR,
                x, divY - rimPaint.getStrokeWidth(), rimPaint);

        // ── Lock (centred on divider, slightly below) ────────────────────────
        float lockCy    = divY + (bottom - divY) * 0.38f;
        float lockW     = w * 0.20f;
        float lockH     = h * 0.22f;
        float lockLeft  = x - lockW / 2f;
        float lockRight = x + lockW / 2f;
        float lockTop   = lockCy - lockH / 2f;
        float lockBot   = lockCy + lockH / 2f;

        // Lock body (rounded rectangle)
        canvas.drawRoundRect(new RectF(lockLeft, lockTop, lockRight, lockBot),
                lockW * 0.25f, lockW * 0.25f, lockBodyPaint);

        // Lock shackle (semi-circle above lock body)
        float shackleR = lockW * 0.38f;
        canvas.drawArc(
                new RectF(x - shackleR, lockTop - shackleR * 1.4f,
                        x + shackleR, lockTop + shackleR * 0.2f),
                180f, 180f, false, lockShacklePaint);
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public boolean isActive()  { return active; }
    public void    deactivate(){ active = false; }
    public float   getX()      { return x; }
    public float   getY()      { return y; }
    /** Collision radius – roughly half the diagonal of the chest rectangle. */
    public float   getRadius() { return Math.max(w, h) * 0.52f; }

    public RewardType  getRewardType()    { return rewardType; }
    public int         getCoinReward()    { return coinReward; }
    public PowerUp.Type getPowerUpReward(){ return powerUpReward; }
}
