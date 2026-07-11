package com.jmegamania.entities;

import com.jmegamania.engine.Sprites;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * One attack wave of the original game's eight: hamburgers, cookies, bugs, radial tires,
 * diamonds, steam irons, bow ties and space dice. Waves 1/3/5/7 sweep horizontally across
 * the MegaSphere (wrapping at the edges); waves 2/4/6/8 descend from the top.
 */
public class EnemyFormation {

    public static final int WAVE_COUNT = 8;

    private enum Pattern { SWEEP, ZIGZAG, SWEEP_BOB, STOP_GO, RAIN }

    private record WaveDef(String sprite, int points, int width, int height,
                           Pattern pattern, double velX, double velY,
                           int count, int shotInterval, int volleyHigh, int volleyLow,
                           boolean shotHeightLimited, int flipInterval, boolean perEnemyDir,
                           boolean bobOnBounds) {
    }

    private static final WaveDef[] WAVES = {
            // 1. Hamburgers: stream steadily across the screen.
            new WaveDef("enemy01.png", 20, 18, 10, Pattern.SWEEP, 1.6, 0,
                    15, 150, 3, 2, false, 0, false, false),
            // 2. Cookies: sweep back and forth, stepping down at each turn.
            new WaveDef("enemy02.png", 30, 18, 10, Pattern.ZIGZAG, 1.75, 0,
                    18, 100, 2, 1, true, 170, false, false),
            // 3. Bugs: like hamburgers, slightly smaller and slower.
            new WaveDef("enemy03.png", 40, 15, 10, Pattern.SWEEP, 1.5, 0,
                    15, 150, 3, 2, false, 0, false, false),
            // 4. Radial tires: cookies pattern, but rows alternate direction individually.
            new WaveDef("enemy04.png", 50, 18, 10, Pattern.ZIGZAG, 1.75, 0,
                    18, 100, 2, 1, true, 170, true, false),
            // 5. Diamonds: sweep across while slowly bobbing up and down.
            new WaveDef("enemy05.png", 60, 13, 8, Pattern.SWEEP_BOB, 1.5, 0.05,
                    15, 150, 3, 2, false, 0, false, true),
            // 6. Steam irons: descend in columns with erratic stop-and-go jinks.
            new WaveDef("enemy06.png", 70, 18, 10, Pattern.STOP_GO, 1.75, 0.6,
                    18, 100, 2, 1, true, 50, false, false),
            // 7. Bow ties: fast sweep with a pronounced vertical weave.
            new WaveDef("enemy07.png", 80, 13, 8, Pattern.SWEEP_BOB, 1.4, 0.75,
                    15, 150, 3, 2, false, 70, false, false),
            // 8. Space dice: rain straight down, reappearing at random columns.
            new WaveDef("enemy08.png", 90, 18, 15, Pattern.RAIN, 0, 1.5,
                    18, 0, 0, 0, false, 0, false, false),
    };

    // Vertical waves wrap back to the top after passing the blaster's row.
    private static final int VERTICAL_WRAP_Y = 175;
    private static final int STOP_GO_WRAP_Y = 165;
    // Descending shots may only be fired from the upper part of the playfield.
    private static final int SHOT_MAX_Y = 125;
    private static final int VOLLEY_STAGGER_FRAMES = 18;
    private static final int ZIGZAG_STEP_NOW = 15;
    private static final int ZIGZAG_STEP_LATER = 10;
    private static final int ZIGZAG_STEP_DELAY_FRAMES = 36;

    private static final class PendingShot {
        final Enemy source;
        int delay;

        PendingShot(Enemy source, int delay) {
            this.source = source;
            this.delay = delay;
        }
    }

    private final WaveDef def;
    private final BufferedImage sprite;
    private final int boardWidth;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<EnemyShot> shots = new ArrayList<>();
    private final List<PendingShot> pendingShots = new ArrayList<>();
    private final Random random = new Random();

    private int shotCooldown;
    private int dirX = 1;
    private int dirY = 1;
    private int flipCountdown;
    private int zigzagStepCountdown = -1;
    // Steam irons: horizontal and vertical motion pause and resume at random moments.
    private boolean stoppedX;
    private boolean stoppedY = true;
    private int toggleXCountdown;
    private int toggleYCountdown;
    // Space dice: each wrapped triplet re-enters at a shared random column.
    private double rainBaseX;
    private int rainWrapCounter;

    public EnemyFormation(int waveIndex, int boardWidth) {
        this.def = WAVES[waveIndex % WAVE_COUNT];
        this.sprite = Sprites.load(def.sprite());
        this.boardWidth = boardWidth;
        this.flipCountdown = def.flipInterval();
        this.toggleXCountdown = randomRange(30, 180);
        this.toggleYCountdown = randomRange(120, 222);
        this.rainBaseX = random.nextDouble() * boardWidth;
        spawn();
    }

    public int getPoints() {
        return def.points();
    }

    private void spawn() {
        switch (def.pattern()) {
            case SWEEP, SWEEP_BOB -> spawnSweepColumns();
            case ZIGZAG -> spawnZigzagRows();
            case STOP_GO -> spawnColumns(35, -75, 75);
            case RAIN -> spawnRain();
        }
    }

    /** Staggered triplets that enter from the left edge and sweep right. */
    private void spawnSweepColumns() {
        int y = def.pattern() == Pattern.SWEEP_BOB
                ? (def.bobOnBounds() ? 27 : 22)
                : 10;
        // Start well off the left edge so the wave streams in gradually.
        double x = -250;
        while (enemies.size() < def.count()) {
            add(x, y);
            add(x - 32, y + 18);
            add(x, y + 35);
            x -= 65;
        }
    }

    /** Rows of three descending from above the screen, each row shifted at random. */
    private void spawnZigzagRows() {
        double x = 20;
        double y = -15;
        int rowDir = 1;
        int lastBranch = -1;
        while (enemies.size() < def.count()) {
            double left = wrapX(x);
            double middle = wrapX(x + 75);
            double right = wrapX(x + 150);
            addWithDir(left, y, rowDir);
            addWithDir(middle, y, rowDir);
            addWithDir(right, y, rowDir);
            rowDir = -rowDir;

            int branch;
            do {
                branch = random.nextInt(3);
            } while (branch == lastBranch);
            lastBranch = branch;
            x = (branch == 0 ? left : branch == 1 ? middle : right) + 25;
            y -= 35;
        }
    }

    /** Straight columns of three stacked above the screen. */
    private void spawnColumns(double startX, double startY, int spacing) {
        double y = startY;
        while (enemies.size() < def.count()) {
            add(startX, y);
            add(startX + spacing, y);
            add(startX + 2 * spacing, y);
            y -= 40;
        }
    }

    /** Dice rows at a random column base, falling straight down. */
    private void spawnRain() {
        double y = -75;
        while (enemies.size() < def.count()) {
            double base = random.nextDouble() * boardWidth;
            add(base, y);
            add(base + 70, y);
            add(base + 140, y);
            y -= 40;
        }
    }

    private void add(double x, double y) {
        enemies.add(new Enemy(sprite, def.width(), def.height(), x, y));
    }

    private void addWithDir(double x, double y, int dir) {
        Enemy enemy = new Enemy(sprite, def.width(), def.height(), x, y);
        enemy.setDir(dir);
        enemies.add(enemy);
    }

    private double wrapX(double x) {
        return x >= boardWidth ? x - boardWidth : x;
    }

    public void update(int boardWidth, int boardHeight) {
        if (enemies.isEmpty()) {
            return;
        }
        switch (def.pattern()) {
            case SWEEP -> updateSweep();
            case SWEEP_BOB -> updateSweepBob();
            case ZIGZAG -> updateZigzag();
            case STOP_GO -> updateStopGo();
            case RAIN -> updateRain();
        }
        updateShooting(boardHeight);
    }

    private void updateSweep() {
        for (Enemy enemy : enemies) {
            if (enemy.getX() >= boardWidth) {
                enemy.setX(-5);
            } else {
                enemy.moveBy(def.velX(), 0);
            }
        }
    }

    private void updateSweepBob() {
        if (!def.bobOnBounds()) {
            flipCountdown--;
            if (flipCountdown <= 0) {
                dirY = -dirY;
                flipCountdown = def.flipInterval();
            }
        }
        boolean bounce = false;
        for (Enemy enemy : enemies) {
            enemy.moveBy(0, def.velY() * dirY);
            if (def.bobOnBounds() && (enemy.getY() >= SHOT_MAX_Y || enemy.getY() <= 15)) {
                bounce = true;
            }
            if (enemy.getX() >= boardWidth) {
                enemy.setX(-5);
            } else {
                enemy.moveBy(def.velX(), 0);
            }
        }
        if (bounce) {
            dirY = -dirY;
        }
    }

    private void updateZigzag() {
        flipCountdown--;
        if (flipCountdown <= 0) {
            flipCountdown = def.flipInterval();
            dirX = -dirX;
            for (Enemy enemy : enemies) {
                if (def.perEnemyDir()) {
                    enemy.flipDir();
                }
                enemy.moveBy(0, ZIGZAG_STEP_NOW);
            }
            zigzagStepCountdown = ZIGZAG_STEP_DELAY_FRAMES;
        }
        if (zigzagStepCountdown >= 0 && --zigzagStepCountdown < 0) {
            for (Enemy enemy : enemies) {
                enemy.moveBy(0, ZIGZAG_STEP_LATER);
            }
        }
        for (Enemy enemy : enemies) {
            int dir = def.perEnemyDir() ? enemy.getDir() : dirX;
            enemy.moveBy(def.velX() * dir, 0);
            if (enemy.getX() >= boardWidth) {
                enemy.setX(-def.width());
            } else if (enemy.getX() + def.width() <= 0) {
                enemy.setX(boardWidth);
            }
            if (enemy.getY() >= VERTICAL_WRAP_Y) {
                enemy.setY(-40);
            }
        }
    }

    private void updateStopGo() {
        if (--toggleYCountdown <= 0) {
            stoppedY = !stoppedY;
            toggleYCountdown = randomRange(120, 222);
        }
        if (--toggleXCountdown <= 0) {
            stoppedX = !stoppedX;
            toggleXCountdown = randomRange(30, 180);
        }
        if (!stoppedX) {
            flipCountdown--;
            if (flipCountdown <= 0) {
                dirX = -dirX;
                flipCountdown = def.flipInterval();
            }
        }
        for (Enemy enemy : enemies) {
            if (!stoppedX) {
                enemy.moveBy(def.velX() * dirX, 0);
            }
            if (!stoppedY) {
                enemy.moveBy(0, def.velY());
            }
            if (enemy.getY() >= STOP_GO_WRAP_Y) {
                enemy.setY(-75);
            }
        }
    }

    private void updateRain() {
        for (Enemy enemy : enemies) {
            enemy.moveBy(0, def.velY());
            if (enemy.getY() >= VERTICAL_WRAP_Y) {
                enemy.setY(-65);
                enemy.setX(wrapX(rainBaseX + rainWrapCounter * 70));
                rainWrapCounter++;
                if (rainWrapCounter == 3) {
                    rainWrapCounter = 0;
                    rainBaseX = random.nextDouble() * boardWidth;
                }
            }
        }
    }

    private void updateShooting(int boardHeight) {
        if (def.shotInterval() > 0) {
            if (shotCooldown > 0) {
                shotCooldown--;
            } else {
                int amount = enemies.size() >= 4 ? def.volleyHigh() : def.volleyLow();
                for (int i = 1; i <= amount; i++) {
                    Enemy source = enemies.get(random.nextInt(enemies.size()));
                    pendingShots.add(new PendingShot(source, i * VOLLEY_STAGGER_FRAMES));
                }
                shotCooldown = def.shotInterval();
            }
        }

        Iterator<PendingShot> pending = pendingShots.iterator();
        while (pending.hasNext()) {
            PendingShot shot = pending.next();
            if (--shot.delay > 0) {
                continue;
            }
            pending.remove();
            if (!enemies.contains(shot.source)) {
                continue;
            }
            double y = shot.source.getY();
            if (def.shotHeightLimited() && (y < 0 || y > SHOT_MAX_Y)) {
                continue;
            }
            shots.add(new EnemyShot((int) Math.round(shot.source.getX()) + def.width() / 2,
                    (int) Math.round(y) + 10));
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

    /** After the blaster is destroyed, survivors back away and re-enter gradually. */
    public void retreat() {
        shots.clear();
        pendingShots.clear();
        if (enemies.isEmpty()) {
            return;
        }
        if (def.pattern() == Pattern.SWEEP || def.pattern() == Pattern.SWEEP_BOB) {
            for (Enemy enemy : enemies) {
                enemy.moveBy(-(boardWidth + 160), 0);
            }
        } else {
            double maxY = enemies.get(0).getY();
            for (Enemy enemy : enemies) {
                maxY = Math.max(maxY, enemy.getY());
            }
            double shift = maxY <= 50 ? 70 : maxY * 1.25;
            for (Enemy enemy : enemies) {
                enemy.moveBy(0, -shift);
            }
        }
    }

    private int randomRange(int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
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
        pendingShots.clear();
    }

    public void remove(Enemy enemy) {
        enemies.remove(enemy);
    }

    public void removeShot(EnemyShot shot) {
        shots.remove(shot);
    }
}
