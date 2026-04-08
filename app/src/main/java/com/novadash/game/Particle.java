package com.novadash.game;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/**
 * A single particle emitted during explosion effects.
 */
public class Particle {

    private float x, y;
    private final float vx;
    private float vy;
    private final float radius;
    private final float gravity;
    private final int color;
    private int alpha = 255;
    private int life;
    private final int maxLife;

    private final Paint paint;

    private static final Random RNG = new Random();

    public Particle(float originX, float originY, int color, int screenWidth, int screenHeight) {
        x = originX;
        y = originY;
        this.color = color;

        float angle = RNG.nextFloat() * (float) (Math.PI * 2);
        float baseSpeed = screenHeight * 0.001f;
        float speedRange = screenHeight * 0.004f;
        float speed = baseSpeed + RNG.nextFloat() * speedRange;
        vx = (float) Math.cos(angle) * speed;
        vy = (float) Math.sin(angle) * speed;

        float baseRadius = screenWidth * 0.002f;
        float radiusRange = screenWidth * 0.005f;
        radius = baseRadius + RNG.nextFloat() * radiusRange;
        gravity = screenHeight * 0.0001f;
        maxLife = 25 + RNG.nextInt(25);
        life = maxLife;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void update() {
        x += vx;
        y += vy;
        vy += gravity;  // slight gravity
        life--;
        alpha = (int) (255f * life / maxLife);
    }

    public void draw(Canvas canvas) {
        paint.setColor(color);
        paint.setAlpha(Math.max(0, alpha));
        canvas.drawCircle(x, y, radius, paint);
    }

    public boolean isActive() {
        return life > 0;
    }
}
