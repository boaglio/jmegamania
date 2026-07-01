package com.jmegamania.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class Bullet {

    private static final int WIDTH = 2;
    private static final int HEIGHT = 10;
    private static final int SPEED = 4;

    private int x;
    private int y;

    public Bullet(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update(int trackX) {
        x = trackX;
        y -= SPEED;
    }

    public boolean isOffScreen() {
        return y + HEIGHT < 0;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    public void render(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, WIDTH, HEIGHT);
    }
}
