package com.jmegamania.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class EnemyShot {

    private static final int WIDTH = 2;
    private static final int HEIGHT = 10;
    // Measured from the original ROM: shots fall 2 scanlines per frame,
    // which is 2.5px at this port's 1.25x vertical scale.
    private static final double SPEED = 2.5;
    private static final Color COLOR = Color.BLUE;

    private final int x;
    private double y;

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
        return new Rectangle(x, (int) Math.round(y), WIDTH, HEIGHT);
    }

    public void render(Graphics2D g) {
        g.setColor(COLOR);
        g.fillRect(x, (int) Math.round(y), WIDTH, HEIGHT);
    }
}
