package com.jmegamania.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class Enemy {

    public static final int WIDTH = 18;
    public static final int HEIGHT = 10;

    private int x;
    private int y;

    public Enemy(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void moveBy(int dx, int dy) {
        x += dx;
        y += dy;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    public void render(Graphics2D g) {
        g.setColor(Color.RED);
        g.fillRect(x, y, WIDTH, HEIGHT);
    }
}
