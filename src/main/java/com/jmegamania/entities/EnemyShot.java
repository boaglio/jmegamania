package com.jmegamania.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class EnemyShot {

    private static final int WIDTH = 2;
    private static final int HEIGHT = 10;
    private static final int SPEED = 2;
    private static final Color COLOR = Color.BLUE;

    private final int x;
    private int y;

    public EnemyShot(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update() {
        y += SPEED;
    }

    public boolean isOffScreen(int boardHeight) {
        return y > boardHeight;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    public void render(Graphics2D g) {
        g.setColor(COLOR);
        g.fillRect(x, y, WIDTH, HEIGHT);
    }
}
