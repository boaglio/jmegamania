package com.jmegamania.entities;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public class Enemy {

    private final int width;
    private final int height;
    private double x;
    private double y;
    // Per-enemy horizontal direction; only the radial-tire wave flips it individually.
    private int dir = 1;

    public Enemy(int width, int height, double x, double y) {
        this.width = width;
        this.height = height;
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDir() {
        return dir;
    }

    public void setDir(int dir) {
        this.dir = dir;
    }

    public void flipDir() {
        dir = -dir;
    }

    public void moveBy(double dx, double dy) {
        x += dx;
        y += dy;
    }

    public Rectangle getBounds() {
        return new Rectangle((int) Math.round(x), (int) Math.round(y), width, height);
    }

    public void render(Graphics2D g, BufferedImage sprite) {
        g.drawImage(sprite, (int) Math.round(x), (int) Math.round(y), width, height, null);
    }
}
