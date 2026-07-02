package com.jmegamania.entities;

import com.jmegamania.engine.Sprites;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public class Enemy {

    public static final int WIDTH = 18;
    public static final int HEIGHT = 13;
    private static final BufferedImage SPRITE = Sprites.load("enemy01.png");

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
        g.drawImage(SPRITE, x, y, WIDTH, HEIGHT, null);
    }
}
