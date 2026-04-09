package com.novadash.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.Random;

/**
 * A collectible power-up that drifts down from the top of the screen.
 *
 * Types
 * ─────
 *  SHIELD      – temporary invincibility bubble (blue)
 *  SLOW_TIME   – halves meteor speed for a few seconds (yellow)
 *  EXTRA_LIFE  – restores one life (red/heart)
 *  SCORE_BOOST – doubles score gain for a few seconds (green)
 *  SKJOLD      – extended shield (Norwegian for "shield"; purple; 7.5 s)
 *  LASER       – continuous laser beam destroys meteors (red; 5 s)
 *  PISTOL      – auto-fires bullets that destroy meteors (orange; 5 s)
 *  BOOST       – instantly clears all meteors + 3 s invincibility (cyan)
 *  COINS       – immediate +500 score bonus (gold)
 */
public class PowerUp {

    public enum Type { SHIELD, SLOW_TIME, EXTRA_LIFE, SCORE_BOOST,
                       SKJOLD, LASER, PISTOL, BOOST, COINS }

    private float x, y;
    private final float radius;
    private final float speedY;
    private float rotation;

    private final Type type;
    private final int baseColor;
    private final int lightColor;
    private final String icon;

    private final Paint outerPaint;
    private final Paint innerPaint;
    private final Paint glowPaint;
    private final Paint iconPaint;

    private boolean active = true;

    private static final Random RNG = new Random();

    public PowerUp(int screenWidth, int screenHeight) {
        radius = screenWidth * 0.045f;
        x = radius + RNG.nextFloat() * (screenWidth - 2 * radius);
        y = -radius;
        speedY = screenHeight * 0.0025f;

        Type[] values = Type.values();
        type = values[RNG.nextInt(values.length)];

        switch (type) {
            case SHIELD:
                baseColor = Color.rgb(40, 100, 255);
                lightColor = Color.rgb(100, 160, 255);
                icon = "S";
                break;
            case SLOW_TIME:
                baseColor = Color.rgb(220, 180, 0);
                lightColor = Color.rgb(255, 230, 80);
                icon = "T";
                break;
            case EXTRA_LIFE:
                baseColor = Color.rgb(230, 40, 90);
                lightColor = Color.rgb(255, 100, 140);
                icon = "\u2665"; // ♥
                break;
            case SCORE_BOOST:
            default:
                baseColor = Color.rgb(30, 200, 80);
                lightColor = Color.rgb(80, 255, 130);
                icon = "2x";
                break;
            case SKJOLD:
                baseColor = Color.rgb(130, 20, 210);
                lightColor = Color.rgb(190, 90, 255);
                icon = "SK";
                break;
            case LASER:
                baseColor = Color.rgb(210, 20, 20);
                lightColor = Color.rgb(255, 80, 60);
                icon = "L";
                break;
            case PISTOL:
                baseColor = Color.rgb(200, 80, 0);
                lightColor = Color.rgb(255, 140, 30);
                icon = "P";
                break;
            case BOOST:
                baseColor = Color.rgb(0, 170, 200);
                lightColor = Color.rgb(40, 215, 255);
                icon = "\u2192"; // →
                break;
            case COINS:
                baseColor = Color.rgb(185, 130, 0);
                lightColor = Color.rgb(235, 180, 30);
                icon = "\u00a2"; // ¢
                break;
        }

        outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPaint.setColor(baseColor);
        outerPaint.setStyle(Paint.Style.FILL);

        innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setColor(lightColor);
        innerPaint.setStyle(Paint.Style.FILL);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(baseColor);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAlpha(70);

        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setColor(Color.WHITE);
        iconPaint.setFakeBoldText(true);
        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setTextSize(radius * 0.9f);
    }

    /**
     * Called each frame so the view can pass the screen height for off-screen detection.
     */
    public void update(int screenHeight) {
        y += speedY;
        rotation = (rotation + 2f) % 360f;
        if (y - radius > screenHeight) {
            active = false;
        }
    }

    public void draw(Canvas canvas) {
        // Pulsing glow ring
        canvas.drawCircle(x, y, radius * 1.45f, glowPaint);
        // Outer disc
        canvas.drawCircle(x, y, radius, outerPaint);
        // Inner highlight
        canvas.drawCircle(x, y, radius * 0.62f, innerPaint);
        // Icon label (drawn without rotation so it stays readable)
        canvas.drawText(icon, x, y + radius * 0.35f, iconPaint);
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public boolean isActive() { return active; }
    public void deactivate() { active = false; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getRadius() { return radius; }
    public Type getType() { return type; }

    /** Returns the base tint colour for particle effects on collection. */
    public int getColor() { return baseColor; }
}
