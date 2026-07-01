package com.jmegamania.entities;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnemyFormation {

    private static final int ROWS = 3;
    private static final int COLS = 6;
    private static final int H_SPACING = 32;
    private static final int V_SPACING = 20;
    private static final int SIDE_MARGIN = 20;
    private static final int STEP_DOWN = 8;
    private static final int SPEED = 1;

    private final List<Enemy> enemies = new ArrayList<>();
    private int direction = 1;

    public EnemyFormation(int startX, int startY) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                enemies.add(new Enemy(startX + col * H_SPACING, startY + row * V_SPACING));
            }
        }
    }

    public void update(int boardWidth) {
        if (enemies.isEmpty()) {
            return;
        }

        int leftMost = enemies.stream().mapToInt(Enemy::getX).min().orElse(0);
        int rightMost = enemies.stream().mapToInt(e -> e.getX() + Enemy.WIDTH).max().orElse(boardWidth);

        boolean hitEdge = (direction > 0 && rightMost >= boardWidth - SIDE_MARGIN)
                || (direction < 0 && leftMost <= SIDE_MARGIN);

        if (hitEdge) {
            direction = -direction;
            for (Enemy enemy : enemies) {
                enemy.moveBy(0, STEP_DOWN);
            }
        } else {
            for (Enemy enemy : enemies) {
                enemy.moveBy(direction * SPEED, 0);
            }
        }
    }

    public void render(Graphics2D g) {
        for (Enemy enemy : enemies) {
            enemy.render(g);
        }
    }

    public List<Enemy> getEnemies() {
        return Collections.unmodifiableList(enemies);
    }

    public void remove(Enemy enemy) {
        enemies.remove(enemy);
    }
}
