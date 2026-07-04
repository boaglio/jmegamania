package com.jmegamania.entities;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class EnemyFormation {

    private static final int ROWS = 2;
    // COLS * H_SPACING spans the full 320px playfield so a row tiles it with no gap.
    private static final int COLS = 10;
    private static final int H_SPACING = 32;
    private static final int V_SPACING = 30;
    private static final int STAGGER_OFFSET = H_SPACING / 2;
    // Horizontal wrap distance: enemies loop seamlessly every COLS columns.
    private static final int LOOP_WIDTH = COLS * H_SPACING;
    private static final double SPEED_X = 1.6;
    private static final int SHOT_INTERVAL_FRAMES = 150;
    // Blank space (px) kept to the left of the screen before the first enemy scrolls in.
    private static final int ENTRY_GAP = 40;

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<EnemyShot> shots = new ArrayList<>();
    private final Random random = new Random();
    private final int startX;
    private final int startY;
    private double moveAccumulator;
    private int shotCooldown;

    public EnemyFormation(int startX, int startY) {
        this.startX = startX;
        this.startY = startY;
        spawnGrid();
    }

    public void reset() {
        enemies.clear();
        shots.clear();
        moveAccumulator = 0;
        shotCooldown = 0;
        spawnGrid();
    }

    public void pushOffScreen(int boardWidth) {
        shots.clear();
        if (enemies.isEmpty()) {
            return;
        }
        int rightmost = enemies.get(0).getX();
        for (Enemy enemy : enemies) {
            rightmost = Math.max(rightmost, enemy.getX());
        }
        // Move the whole surviving group just off the left edge so it scrolls back in gradually.
        int shift = -(rightmost + Enemy.WIDTH + ENTRY_GAP);
        for (Enemy enemy : enemies) {
            enemy.moveBy(shift, 0);
        }
    }

    private void spawnGrid() {
        int rightmostColumn = startX + STAGGER_OFFSET + (COLS - 1) * H_SPACING;
        int entryShift = rightmostColumn + Enemy.WIDTH + ENTRY_GAP;
        for (int row = 0; row < ROWS; row++) {
            int rowOffset = (row % 2 == 1) ? STAGGER_OFFSET : 0;
            for (int col = 0; col < COLS; col++) {
                enemies.add(new Enemy(startX + rowOffset + col * H_SPACING - entryShift,
                        startY + row * V_SPACING));
            }
        }
    }

    public void update(int boardWidth, int boardHeight) {
        if (enemies.isEmpty()) {
            return;
        }

        moveAccumulator += SPEED_X;
        int step = (int) moveAccumulator;
        if (step > 0) {
            moveAccumulator -= step;
            for (Enemy enemy : enemies) {
                enemy.moveBy(step, 0);
                if (enemy.getX() >= boardWidth) {
                    enemy.moveBy(-LOOP_WIDTH, 0);
                }
            }
        }

        updateShooting(boardHeight);
    }

    private void updateShooting(int boardHeight) {
        if (shotCooldown > 0) {
            shotCooldown--;
        } else {
            int amount = enemies.size() >= 4 ? 3 : 2;
            for (int i = 0; i < amount; i++) {
                Enemy source = enemies.get(random.nextInt(enemies.size()));
                shots.add(new EnemyShot(source.getX() + Enemy.WIDTH / 2, source.getY() + Enemy.HEIGHT));
            }
            shotCooldown = SHOT_INTERVAL_FRAMES;
        }

        Iterator<EnemyShot> it = shots.iterator();
        while (it.hasNext()) {
            EnemyShot shot = it.next();
            shot.update();
            if (shot.isOffScreen(boardHeight)) {
                it.remove();
            }
        }
    }

    public void render(Graphics2D g) {
        for (Enemy enemy : enemies) {
            enemy.render(g);
        }
        for (EnemyShot shot : shots) {
            shot.render(g);
        }
    }

    public List<Enemy> getEnemies() {
        return Collections.unmodifiableList(enemies);
    }

    public List<EnemyShot> getShots() {
        return Collections.unmodifiableList(shots);
    }

    public void clear() {
        enemies.clear();
        shots.clear();
    }

    public void remove(Enemy enemy) {
        enemies.remove(enemy);
    }

    public void removeShot(EnemyShot shot) {
        shots.remove(shot);
    }
}
