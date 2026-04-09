package com.novadash.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The player's spaceship. Drawn entirely with Canvas primitives — no image assets required.
 * The ship sits near the bottom of the screen and follows the player's horizontal touch position.
 */
public class Player {

    private float x;
    private float targetX;
    private final float y;
    private final float halfW;    // half the visual width  (used for clamping + hitbox)
    private final float halfH;    // half the visual height
    /** Lerp factor controlling how fast x closes toward targetX (0.4 base → 1.0 at level 5). */
    private final float lerpFactor;

    private final Paint bodyPaint;
    private final Paint wingPaint;
    private final Paint cockpitPaint;
    private final Paint enginePaint;
    private final Paint shieldPaint;

    private boolean shielded;
    private int engineFlicker;  // simple engine glow animation counter

    /**
     * @param speedLevel 0–5 ship speed upgrade level; higher → snappier response.
     */
    public Player(int screenWidth, int screenHeight, int speedLevel) {
        halfW = screenWidth * 0.055f;
        halfH = halfW * 1.6f;

        x = screenWidth / 2f;
        targetX = x;
        y = screenHeight - halfH - screenHeight * 0.06f;
        lerpFactor = Math.min(1.0f, 0.4f + speedLevel * 0.12f);

        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.rgb(80, 190, 255));
        bodyPaint.setStyle(Paint.Style.FILL);

        wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wingPaint.setColor(Color.rgb(40, 120, 200));
        wingPaint.setStyle(Paint.Style.FILL);

        cockpitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cockpitPaint.setColor(Color.rgb(180, 230, 255));
        cockpitPaint.setStyle(Paint.Style.FILL);

        enginePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        enginePaint.setStyle(Paint.Style.FILL);

        shieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shieldPaint.setColor(Color.argb(120, 80, 160, 255));
        shieldPaint.setStyle(Paint.Style.STROKE);
        shieldPaint.setStrokeWidth(halfW * 0.15f);
    }

    /**
     * Set the desired X position; the ship will lerp towards it each update.
     */
    public void setTargetX(float targetX, int screenWidth) {
        this.targetX = Math.max(halfW, Math.min(targetX, screenWidth - halfW));
    }

    public void update() {
        if (lerpFactor >= 1.0f) {
            x = targetX;
        } else {
            x += (targetX - x) * lerpFactor;
        }
        engineFlicker = (engineFlicker + 1) % 12;
    }

    public void draw(Canvas canvas) {
        // --- Engine exhaust (behind body) ---
        int flickerAlpha = 180 + (engineFlicker < 6 ? engineFlicker * 10 : (12 - engineFlicker) * 10);
        enginePaint.setColor(Color.argb(flickerAlpha, 255, 140, 30));
        canvas.drawOval(x - halfW * 0.3f, y + halfH * 0.55f,
                x + halfW * 0.3f, y + halfH * 1.1f, enginePaint);
        // inner bright core
        enginePaint.setColor(Color.argb(flickerAlpha, 255, 230, 120));
        canvas.drawOval(x - halfW * 0.15f, y + halfH * 0.6f,
                x + halfW * 0.15f, y + halfH * 0.9f, enginePaint);

        // --- Side wings ---
        Path leftWing = new Path();
        leftWing.moveTo(x - halfW * 0.2f, y + halfH * 0.1f);
        leftWing.lineTo(x - halfW * 1.4f, y + halfH * 0.8f);
        leftWing.lineTo(x - halfW * 0.6f, y + halfH * 0.8f);
        leftWing.close();
        canvas.drawPath(leftWing, wingPaint);

        Path rightWing = new Path();
        rightWing.moveTo(x + halfW * 0.2f, y + halfH * 0.1f);
        rightWing.lineTo(x + halfW * 1.4f, y + halfH * 0.8f);
        rightWing.lineTo(x + halfW * 0.6f, y + halfH * 0.8f);
        rightWing.close();
        canvas.drawPath(rightWing, wingPaint);

        // --- Main body (elongated diamond / rocket silhouette) ---
        Path body = new Path();
        body.moveTo(x, y - halfH);                // nose tip
        body.lineTo(x - halfW * 0.7f, y);         // mid-left
        body.lineTo(x - halfW * 0.5f, y + halfH); // bottom-left
        body.lineTo(x + halfW * 0.5f, y + halfH); // bottom-right
        body.lineTo(x + halfW * 0.7f, y);         // mid-right
        body.close();
        canvas.drawPath(body, bodyPaint);

        // --- Cockpit canopy ---
        canvas.drawOval(x - halfW * 0.35f, y - halfH * 0.5f,
                x + halfW * 0.35f, y + halfH * 0.05f, cockpitPaint);

        // --- Shield bubble ---
        if (shielded) {
            canvas.drawCircle(x, y, halfW * 1.7f, shieldPaint);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public float getX() { return x; }
    public float getY() { return y; }

    /** Effective collision radius (slightly tighter than visual for fairness). */
    public float getCollisionRadius() { return halfW * 0.9f; }

    public void setShielded(boolean shielded) { this.shielded = shielded; }
    public boolean isShielded() { return shielded; }

    /**
     * Returns true if the circle at (cx, cy) with the given radius overlaps the ship's hitbox.
     */
    public boolean collidesWith(float cx, float cy, float radius) {
        float dx = cx - x;
        float dy = cy - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < (getCollisionRadius() + radius);
    }
}
