# NovaDash

An Android arcade game where you pilot a spaceship through a meteor shower and collect power-ups.

## Gameplay

- **Drag your finger** left and right to steer the ship.
- **Avoid meteors** – each hit costs a life; you start with 3 (max 5).
- **Collect power-ups** that fall from the top of the screen:
  | Icon | Type | Effect |
  |------|------|--------|
  | **S** | Shield | Invincibility bubble for 5 s (deflected meteors score +15) |
  | **T** | Slow Time | Halves meteor speed for 5 s |
  | **♥** | Extra Life | Adds one life (up to 5) |
  | **2x** | Score Boost | Doubles score gain for 5 s |
- The game speeds up every 10 seconds – survive as long as you can!

## Opening in Android Studio

1. Open Android Studio → **File → Open** → select this repository folder.
2. Let Gradle sync finish (it will download the Gradle distribution automatically).
3. Create an Android Virtual Device (**AVD Manager**) or connect a physical device.
4. Press **Run ▶** (or `Shift+F10`).

**Requirements:** Android Studio Hedgehog or later · compileSdk 34 · minSdk 21 (Android 5.0+)

## Project structure

```
app/src/main/java/com/novadash/game/
├── MainActivity.java   – Full-screen Activity host
├── GameThread.java     – Fixed 60 fps game loop thread
├── GameView.java       – SurfaceView: all game logic & rendering
├── Player.java         – Spaceship (Canvas-drawn, no image assets)
├── Meteor.java         – Falling obstacle
├── PowerUp.java        – Collectible item (4 types)
└── Particle.java       – Explosion particle effect
```

All graphics are drawn with the Android Canvas API – no external image files are needed.
