package com.jmegamania.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

public class Player {

    public static final int WIDTH = 16;
    public static final int HEIGHT = 22;
    private static final int SPEED = 2;

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

    public void update(int boardWidth) {
        if (movingLeft) {
            x -= SPEED;
        }
        if (movingRight) {
            x += SPEED;
        }
        x = Math.max(0, Math.min(boardWidth - WIDTH, x));
    }

    public void render(Graphics2D g) {
        g.setColor(Color.GREEN);
        g.fillRect(x, y, WIDTH, HEIGHT);
    }
}
