package com.jmegamania.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class EnemyShot {

    private static final int WIDTH = 2;
    private static final int HEIGHT = 10;
    private static final Color COLOR = Color.BLUE;

    private final int x;
    private final double speed;
    private double y;

    /**
     * speed comes from the formation: the ROM drops shots 2 scanlines per
     * frame, 3 from the third loop of waves.
     */
    public EnemyShot(int x, double y, double speed) {
        this.x = x;
        this.y = y;
        this.speed = speed;
    }

    public void update() {
        y += speed;
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
