package com.jmegamania.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class Bullet {

    private static final int WIDTH = 2;
    private static final int HEIGHT = 8;
    // Measured from the original ROM: the missile climbs 4 scanlines per frame,
    // which is 5px at this port's 1.25x vertical scale.
    private static final int SPEED = 5;

    private int x;
    private int y;

    /**
     * y is the trailing (bottom) edge of the shot, anchored at the ship's nose on spawn;
     * the beam is drawn extending upward from this point so it appears above the ship, not through it.
     */
    public Bullet(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update(int trackX) {
        x = trackX;
        y -= SPEED;
    }

    public boolean isOffScreen() {
        return y < 0;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y - HEIGHT, WIDTH, HEIGHT);
    }

    public void render(Graphics2D g) {
        g.setColor(Color.RED);
        g.fillRect(x, y - HEIGHT, WIDTH, HEIGHT);
    }
}
