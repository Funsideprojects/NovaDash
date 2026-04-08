package com.novadash.game;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

/**
 * Dedicated rendering thread that drives the game loop at a target of 60 fps.
 */
public class GameThread extends Thread {

    private static final int TARGET_FPS = 60;
    private static final long TARGET_MS_PER_FRAME = 1000 / TARGET_FPS;

    private final SurfaceHolder surfaceHolder;
    private final GameView gameView;
    private volatile boolean running;

    public GameThread(SurfaceHolder surfaceHolder, GameView gameView) {
        super("GameThread");
        this.surfaceHolder = surfaceHolder;
        this.gameView = gameView;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public void run() {
        while (running) {
            long frameStart = System.currentTimeMillis();

            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    synchronized (surfaceHolder) {
                        gameView.update();
                        gameView.render(canvas);
                    }
                }
            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }

            long elapsed = System.currentTimeMillis() - frameStart;
            long sleepMs = TARGET_MS_PER_FRAME - elapsed;
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
