package com.novadash.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;

/**
 * Entry point. Sets up a full-screen window and hands control to GameView.
 */
public class MainActivity extends Activity implements GameView.ReviveAdListener {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request full-screen before setting content
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Keep screen on while the game is running
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        gameView = new GameView(this);
        gameView.setReviveAdListener(this);
        setContentView(gameView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.onGamePause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.onGameResume();
    }

    // ── ReviveAdListener ────────────────────────────────────────────────────────

    /**
     * Shows a simulated rewarded-ad dialog. After the countdown the player is
     * revived. Replace this method body with a real ad-SDK call when available.
     */
    @Override
    public void onWatchAdRequested() {
        runOnUiThread(() -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Ad")
                    .setMessage("Your ad starts now. Please wait…")
                    .setCancelable(false)
                    .create();
            dialog.show();

            // Simulate a 3-second ad, then revive
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && dialog.isShowing()) {
                    dialog.dismiss();
                    gameView.revive();
                }
            }, 3000);
        });
    }
}
