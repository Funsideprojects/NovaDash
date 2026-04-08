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
    private final int color;
    private int alpha = 255;
    private int life;
    private final int maxLife;

    private final Paint paint;

    private static final Random RNG = new Random();

    public Particle(float originX, float originY, int color) {
        x = originX;
        y = originY;
        this.color = color;

        float angle = RNG.nextFloat() * (float) (Math.PI * 2);
        float speed = 2f + RNG.nextFloat() * 9f;
        vx = (float) Math.cos(angle) * speed;
        vy = (float) Math.sin(angle) * speed;

        radius = 2f + RNG.nextFloat() * 5f;
        maxLife = 25 + RNG.nextInt(25);
        life = maxLife;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void update() {
        x += vx;
        y += vy;
        vy += 0.25f;  // slight gravity
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
