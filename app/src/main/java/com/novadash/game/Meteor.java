package com.novadash.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.Random;

/**
 * A falling meteor obstacle. Size, speed and lateral drift are randomised on
 * creation; difficulty scales both speed and spawn frequency externally.
 */
public class Meteor {

    private float x, y;
    private final float radius;
    private float speedX;
    private final float speedY;
    private float rotation;
    private final float rotationSpeed;

    private final Paint rockPaint;
    private final Paint craterPaint;
    private final float[] craterOffX;
    private final float[] craterOffY;
    private final float[] craterR;

    private boolean active = true;

    private final int screenWidth;
    private final int screenHeight;

    private static final Random RNG = new Random();

    public Meteor(int screenWidth, int screenHeight, float difficulty) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        // Scale size to screen density
        float minR = screenWidth * 0.025f;
        float maxR = screenWidth * 0.065f;
        radius = minR + RNG.nextFloat() * (maxR - minR);

        x = radius + RNG.nextFloat() * (screenWidth - 2 * radius);
        y = -radius;

        // Base fall speed scaled to screen height so it feels the same on all densities
        float baseFall = (screenHeight * 0.003f) + RNG.nextFloat() * (screenHeight * 0.003f);
        speedY = baseFall * difficulty;

        // Slight horizontal drift
        speedX = (RNG.nextFloat() - 0.5f) * screenWidth * 0.003f * difficulty;

        rotationSpeed = (RNG.nextFloat() - 0.5f) * 4f;

        int brightness = 110 + RNG.nextInt(80);
        rockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rockPaint.setColor(Color.rgb(brightness, brightness - 15, brightness - 30));
        rockPaint.setStyle(Paint.Style.FILL);

        craterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        craterPaint.setColor(Color.rgb(brightness - 35, brightness - 45, brightness - 55));
        craterPaint.setStyle(Paint.Style.FILL);

        // 3 random craters
        int numCraters = 3;
        craterOffX = new float[numCraters];
        craterOffY = new float[numCraters];
        craterR = new float[numCraters];
        for (int i = 0; i < numCraters; i++) {
            float angle = RNG.nextFloat() * (float) (Math.PI * 2);
            float dist = RNG.nextFloat() * radius * 0.5f;
            craterOffX[i] = (float) Math.cos(angle) * dist;
            craterOffY[i] = (float) Math.sin(angle) * dist;
            craterR[i] = radius * (0.12f + RNG.nextFloat() * 0.12f);
        }
    }

    /**
     * @param speedFactor 1.0 for normal; < 1.0 when Slow Time power-up is active.
     */
    public void update(float speedFactor) {
        x += speedX * speedFactor;
        y += speedY * speedFactor;
        rotation += rotationSpeed * speedFactor;

        // Bounce off side walls
        if (x - radius < 0) {
            x = radius;
            speedX = Math.abs(speedX);
        } else if (x + radius > screenWidth) {
            x = screenWidth - radius;
            speedX = -Math.abs(speedX);
        }

        if (y - radius > screenHeight) {
            active = false;
        }
    }

    public void draw(Canvas canvas) {
        canvas.save();
        canvas.rotate(rotation, x, y);
        canvas.drawCircle(x, y, radius, rockPaint);
        for (int i = 0; i < craterOffX.length; i++) {
            canvas.drawCircle(x + craterOffX[i], y + craterOffY[i], craterR[i], craterPaint);
        }
        canvas.restore();
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public boolean isActive() { return active; }
    public void deactivate() { active = false; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getRadius() { return radius; }
}
