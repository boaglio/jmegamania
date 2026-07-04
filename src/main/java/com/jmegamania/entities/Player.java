package com.jmegamania.entities;

import com.jmegamania.engine.Sprites;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public class Player {

    public static final int WIDTH = 16;
    public static final int HEIGHT = 23;
    private static final int SPEED = 2;
    private static final int HORIZONTAL_MARGIN = 5;
    private static final BufferedImage SPRITE = Sprites.load("player_blue.png");
    private static final BufferedImage WHITE_SPRITE = tintWhite(SPRITE);
    private static final int DEATH_FLASH_FRAMES = 6;
    private static final int DEATH_TOTAL_FRAMES = 60;

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

    /** Starts the "turns white then disappears" death animation. */
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

    /** Advances the death flash; the ship vanishes once it completes. */
    public void updateDeath() {
        if (!dying) {
            return;
        }
        deathTimer++;
        if (deathTimer >= DEATH_TOTAL_FRAMES) {
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
        x = Math.max(HORIZONTAL_MARGIN, Math.min(boardWidth - HORIZONTAL_MARGIN - WIDTH, x));
    }

    public void render(Graphics2D g) {
        if (dead) {
            return;
        }
        if (dying) {
            boolean visible = (deathTimer / DEATH_FLASH_FRAMES) % 2 == 0;
            if (visible) {
                g.drawImage(WHITE_SPRITE, x, y, WIDTH, HEIGHT, null);
            }
            return;
        }
        g.drawImage(SPRITE, x, y, WIDTH, HEIGHT, null);
    }

    private static BufferedImage tintWhite(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int alpha = src.getRGB(x, y) >>> 24;
                out.setRGB(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
        return out;
    }
}
