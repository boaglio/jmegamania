package com.jmegamania.entities;

import com.jmegamania.engine.Sprites;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public class Player {

    public static final int WIDTH = 16;
    public static final int HEIGHT = 23;
    private static final int SPEED = 2;
    // The ROM clamps the blaster to hardware pixels 24..132 of 160 (2x here).
    public static final int X_MIN = 48;
    public static final int X_MAX = 264;
    private static final BufferedImage SPRITE = Sprites.load("player_blue.png");
    // ROM death: playerDeathTimer steps 31 -> 0, one step every 4 frames
    // (~124 frames total), tinting the ship per PlayerDeathColors — grays
    // brightening into a white/blue flicker, then fading down to black.
    private static final int DEATH_STEP_FRAMES = 4;
    private static final int DEATH_STEPS = 31;
    private static final BufferedImage[] DEATH_SPRITES = buildDeathSprites();

    private int x;
    private final int y;
    private boolean movingLeft;
    private boolean movingRight;
    private boolean shooting;
    private boolean dying;
    private boolean dead;
    private int deathTimer;

    public Player(int startX, int startY) {
        this.x = startX;
        this.y = startY;
    }

    public void keyPressed(int keyCode) {
        if (keyCode == KeyEvent.VK_LEFT) {
            movingLeft = true;
        } else if (keyCode == KeyEvent.VK_RIGHT) {
            movingRight = true;
        } else if (keyCode == KeyEvent.VK_SPACE) {
            shooting = true;
        }
    }

    public void keyReleased(int keyCode) {
        if (keyCode == KeyEvent.VK_LEFT) {
            movingLeft = false;
        } else if (keyCode == KeyEvent.VK_RIGHT) {
            movingRight = false;
        } else if (keyCode == KeyEvent.VK_SPACE) {
            shooting = false;
        }
    }

    /**
     * In the guided-missile games the ROM resets its fire-button debounce
     * every frame, so a held button refires the moment the missile slot is
     * free: holding space gives a continuous stream of shots.
     */
    public boolean isShooting() {
        return shooting;
    }

    public int getMuzzleX() {
        return x + WIDTH / 2;
    }

    public int getMuzzleY() {
        return y;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    public void resetPosition(int startX) {
        this.x = startX;
        this.dying = false;
        this.dead = false;
        this.deathTimer = 0;
    }

    /** Starts the death color ramp; the ship vanishes once it completes. */
    public void die() {
        dying = true;
        dead = false;
        deathTimer = 0;
        movingLeft = false;
        movingRight = false;
        shooting = false;
    }

    public boolean isDead() {
        return dead;
    }

    /** Advances the death ramp; the ship vanishes once it completes. */
    public void updateDeath() {
        if (!dying) {
            return;
        }
        deathTimer++;
        if (deathTimer >= DEATH_STEPS * DEATH_STEP_FRAMES) {
            dying = false;
            dead = true;
        }
    }

    public void update(int boardWidth) {
        if (movingLeft) {
            x -= SPEED;
        }
        if (movingRight) {
            x += SPEED;
        }
        x = Math.max(X_MIN, Math.min(X_MAX, x));
    }

    public void render(Graphics2D g) {
        if (dead) {
            return;
        }
        if (dying) {
            int step = DEATH_STEPS - deathTimer / DEATH_STEP_FRAMES;
            BufferedImage img = DEATH_SPRITES[Math.max(0, Math.min(DEATH_STEPS, step))];
            if (img != null) {
                g.drawImage(img, x, y, WIDTH, HEIGHT, null);
            }
            return;
        }
        g.drawImage(SPRITE, x, y, WIDTH, HEIGHT, null);
    }

    /** ROM PlayerDeathColors mapped onto tinted silhouettes; null = invisible. */
    private static BufferedImage[] buildDeathSprites() {
        Color blue = new Color(172, 204, 255);   // Atari BLUE + 14
        Color white = Color.WHITE;
        Color[] ramp = new Color[DEATH_STEPS + 1];
        // Steps 0-8 stay black (invisible) before the respawn.
        setSteps(ramp, gray(2), 9, 10);
        setSteps(ramp, gray(4), 11, 12);
        setSteps(ramp, gray(6), 13, 14);
        setSteps(ramp, gray(8), 15, 16);
        setSteps(ramp, gray(10), 17, 18);
        setSteps(ramp, gray(12), 19, 20);
        ramp[21] = white;
        ramp[22] = blue;
        ramp[23] = white;
        ramp[24] = white;
        ramp[25] = blue;
        ramp[26] = white;
        ramp[27] = gray(12);
        ramp[28] = gray(10);
        ramp[29] = gray(8);
        ramp[30] = gray(6);
        ramp[31] = white;
        BufferedImage[] sprites = new BufferedImage[ramp.length];
        for (int i = 0; i < ramp.length; i++) {
            if (ramp[i] != null) {
                sprites[i] = tint(SPRITE, ramp[i]);
            }
        }
        return sprites;
    }

    private static void setSteps(Color[] ramp, Color color, int from, int to) {
        for (int i = from; i <= to; i++) {
            ramp[i] = color;
        }
    }

    private static Color gray(int atariLuminance) {
        int v = atariLuminance * 17;
        return new Color(v, v, v);
    }

    private static BufferedImage tint(BufferedImage src, Color color) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        int rgb = color.getRGB() & 0x00FFFFFF;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int alpha = src.getRGB(x, y) >>> 24;
                out.setRGB(x, y, (alpha << 24) | rgb);
            }
        }
        return out;
    }
}
