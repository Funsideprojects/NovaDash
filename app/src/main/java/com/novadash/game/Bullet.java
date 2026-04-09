package com.novadash.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * A bullet fired by the PISTOL power-up.  Travels straight up from the player's ship
 * and is removed when it leaves the screen or hits a meteor.
 */
public class Bullet {

    private final float x;
    private float y;
    private final float radius;
    private final float speedY;
    private boolean active = true;

    private final Paint corePaint;
    private final Paint glowPaint;

    public Bullet(float x, float y, int screenHeight) {
        this.x = x;
        this.y = y;
        radius  = screenHeight * 0.008f;
        speedY  = screenHeight * 0.018f;

        corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        corePaint.setColor(Color.rgb(255, 220, 50));
        corePaint.setStyle(Paint.Style.FILL);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(Color.argb(100, 255, 200, 30));
        glowPaint.setStyle(Paint.Style.FILL);
    }

    public void update() {
        y -= speedY;
        if (y + radius < 0) active = false;
    }

    public void draw(Canvas canvas) {
        canvas.drawCircle(x, y, radius * 2.5f, glowPaint);
        canvas.drawCircle(x, y, radius, corePaint);
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public boolean isActive() { return active; }
    public float getX()       { return x; }
    public float getY()       { return y; }
    public float getRadius()  { return radius; }
}
