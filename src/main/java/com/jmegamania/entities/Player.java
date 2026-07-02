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

    private int x;
    private final int y;
    private boolean movingLeft;
    private boolean movingRight;
    private boolean shooting;

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
        g.drawImage(SPRITE, x, y, WIDTH, HEIGHT, null);
    }
}
