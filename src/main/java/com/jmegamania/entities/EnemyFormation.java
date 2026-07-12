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
                           boolean bobOnBounds, int frameCount) {
    }

    // Geometry doubled from the 160px-wide original: 8px sprites -> 16, objects
    // spaced 32 hardware pixels -> 64, rows moved one hardware pixel per frame -> 2.
    private static final int ENEMY_W = 16;
    private static final int ENEMY_H = 10;
    private static final double SWEEP_SPEED = 2;

    private static final WaveDef[] WAVES = {
            // 1. Hamburgers: stream steadily across the screen.
            new WaveDef("enemy01.png", 20, ENEMY_W, ENEMY_H, Pattern.SWEEP, SWEEP_SPEED, 0,
                    15, 150, 3, 2, false, 0, false, false, 3),
            // 2. Cookies: sweep back and forth, stepping down at each turn.
            new WaveDef("enemy02.png", 30, ENEMY_W, ENEMY_H, Pattern.ZIGZAG, SWEEP_SPEED, 0,
                    18, 100, 2, 1, true, 170, false, false, 3),
            // 3. Bugs: like hamburgers.
            new WaveDef("enemy03.png", 40, ENEMY_W, ENEMY_H, Pattern.SWEEP, SWEEP_SPEED, 0,
                    15, 150, 3, 2, false, 0, false, false, 3),
            // 4. Radial tires: cookies pattern, but rows alternate direction individually.
            new WaveDef("enemy04.png", 50, ENEMY_W, ENEMY_H, Pattern.ZIGZAG, SWEEP_SPEED, 0,
                    18, 100, 2, 1, true, 170, true, false, 3),
            // 5. Diamonds: sweep across while slowly bobbing up and down, spinning.
            new WaveDef("enemy05.png", 60, ENEMY_W, ENEMY_H, Pattern.SWEEP_BOB, SWEEP_SPEED, 0.05,
                    15, 150, 3, 2, false, 0, false, true, 4),
            // 6. Steam irons: descend in columns with erratic stop-and-go jinks.
            new WaveDef("enemy06.png", 70, ENEMY_W, ENEMY_H, Pattern.STOP_GO, SWEEP_SPEED, 0.6,
                    18, 100, 2, 1, true, 50, false, false, 3),
            // 7. Bow ties: fast sweep with a pronounced vertical weave, spinning.
            new WaveDef("enemy07.png", 80, ENEMY_W, ENEMY_H, Pattern.SWEEP_BOB, SWEEP_SPEED, 0.75,
                    15, 150, 3, 2, false, 70, false, false, 4),
            // 8. Space dice: rain straight down, tumbling through 16 phases.
            new WaveDef("enemy08.png", 90, ENEMY_W, 12, Pattern.RAIN, 0, 1.25,
                    18, 0, 0, 0, false, 0, false, false, 16),
    };

    // Horizontal waves: three staggered rows of five, tiling the wrap ring exactly.
    private static final int SWEEP_ROWS = 3;
    private static final int SWEEP_PER_ROW = 5;
    private static final int SWEEP_SPACING = 64;
    private static final int SWEEP_STAGGER = SWEEP_SPACING / 2;
    private static final int SWEEP_ROW_PITCH = 18;
    // Spawn one full ring off the left edge so the wave streams in gradually.
    private static final int SWEEP_ENTRY_SHIFT = SWEEP_PER_ROW * SWEEP_SPACING + SWEEP_SPACING;
    // Multiple of the spacing so survivors re-enter on their original ring slots.
    private static final int SWEEP_RETREAT_SHIFT = SWEEP_PER_ROW * SWEEP_SPACING + 2 * SWEEP_SPACING;
    // Vertical waves: three columns 64 apart, rows 36 apart (29 scanlines originally).
    private static final int COLUMN_SPACING = 64;
    private static final int ROW_PITCH = 36;
    // Vertical waves wrap back to the top after passing the blaster's row.
    private static final int VERTICAL_WRAP_Y = 175;
    private static final int STOP_GO_WRAP_Y = 165;
    // Descending shots may only be fired from the upper part of the playfield.
    private static final int SHOT_MAX_Y = 125;
    private static final int VOLLEY_STAGGER_FRAMES = 18;
    private static final int ZIGZAG_STEP_NOW = 22;
    private static final int ZIGZAG_STEP_LATER = 14;
    private static final int ZIGZAG_STEP_DELAY_FRAMES = 36;
    // Second loop: hamburgers cruise, pause for a while, then dash before settling.
    private static final int PHASE_CRUISE_FRAMES = 150;
    private static final int PHASE_PAUSE_FRAMES = 90;
    private static final int PHASE_DASH_FRAMES = 50;
    private static final double DASH_SPEED = 5;
    // Second loop: cookies and tires dive a full drop quickly instead of stepping.
    private static final int DIVE_FRAMES = 18;
    private static final double COOKIE_DIVE_SPEED = 4;
    private static final double TIRE_DIVE_SPEED = 5;
    // Second loop: dice drift sideways at ~45 degrees, rows alternating direction.
    private static final double RAIN_DRIFT_SPEED = 1.25;

    private static final class PendingShot {
        final Enemy source;
        int delay;

        PendingShot(Enemy source, int delay) {
            this.source = source;
            this.delay = delay;
        }
    }

    private final int waveIndex;
    private final boolean secondLoop;
    private final WaveDef def;
    // Authentic ROM animation: one frame step every four game frames. Waves with
    // four frames ping-pong 0-1-2-3-2-1 (the diamond and bow-tie spin); the dice
    // cycle through all sixteen tumble phases.
    private final BufferedImage[] frames;
    private final int[] frameSequence;
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
    // Second-loop hamburgers: 0 = cruise, 1 = pause, 2 = dash.
    private int sweepPhase;
    private int sweepPhaseTimer = PHASE_CRUISE_FRAMES;
    private int diveFramesLeft;
    private int animTick;

    public EnemyFormation(int waveIndex, int boardWidth) {
        this(waveIndex, boardWidth, false);
    }

    public EnemyFormation(int waveIndex, int boardWidth, boolean secondLoop) {
        this.waveIndex = waveIndex % WAVE_COUNT;
        this.secondLoop = secondLoop;
        this.def = secondLoop ? secondLoopVariant(this.waveIndex) : WAVES[this.waveIndex];
        this.frames = new BufferedImage[def.frameCount()];
        for (int i = 0; i < frames.length; i++) {
            this.frames[i] = Sprites.load(
                    String.format("enemy%02d_%d.png", this.waveIndex + 1, i));
        }
        this.frameSequence = def.frameCount() == 4
                ? new int[]{0, 1, 2, 3, 2, 1}
                : identity(def.frameCount());
        this.boardWidth = boardWidth;
        this.flipCountdown = def.flipInterval();
        this.toggleXCountdown = randomRange(30, 180);
        this.toggleYCountdown = randomRange(120, 222);
        this.rainBaseX = random.nextDouble() * boardWidth;
        spawn();
    }

    /**
     * From the second loop of eight waves on, the patterns change: hamburgers pause
     * and dash, bugs undulate vertically, cookies and tires dive, and dice drift
     * sideways. Steam irons are the one wave that never changes.
     */
    private static WaveDef secondLoopVariant(int waveIndex) {
        WaveDef base = WAVES[waveIndex];
        if (waveIndex == 2) {
            // Bugs gain a vertical undulation like the bow ties'.
            return new WaveDef(base.sprite(), base.points(), base.width(), base.height(),
                    Pattern.SWEEP_BOB, base.velX(), 0.3,
                    base.count(), base.shotInterval(), base.volleyHigh(), base.volleyLow(),
                    base.shotHeightLimited(), 70, base.perEnemyDir(), false, base.frameCount());
        }
        return base;
    }

    public int getPoints() {
        return def.points();
    }

    private void spawn() {
        switch (def.pattern()) {
            case SWEEP, SWEEP_BOB -> spawnSweepRows();
            case ZIGZAG -> spawnZigzagRows();
            case STOP_GO -> spawnColumns(96, -75);
            case RAIN -> spawnRain();
        }
    }

    /**
     * Three uniform rows of five, evenly spaced so they tile the wrap ring exactly,
     * with alternate rows offset by half a spacing, as in the original.
     */
    private void spawnSweepRows() {
        int baseY = def.pattern() == Pattern.SWEEP_BOB
                ? (def.bobOnBounds() ? 27 : 22)
                : 7;
        for (int row = 0; row < SWEEP_ROWS; row++) {
            double y = baseY + row * SWEEP_ROW_PITCH;
            int stagger = (row % 2 == 1) ? SWEEP_STAGGER : 0;
            for (int col = 0; col < SWEEP_PER_ROW; col++) {
                add(col * SWEEP_SPACING + stagger - SWEEP_ENTRY_SHIFT, y);
            }
        }
    }

    /** Rows of three descending from above the screen, each row shifted at random. */
    private void spawnZigzagRows() {
        double x = 40;
        double y = -15;
        int rowDir = 1;
        int lastBranch = -1;
        while (enemies.size() < def.count()) {
            double left = wrapX(x);
            double middle = wrapX(x + COLUMN_SPACING);
            double right = wrapX(x + 2 * COLUMN_SPACING);
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
            y -= ROW_PITCH;
        }
    }

    /** Straight columns of three stacked above the screen. */
    private void spawnColumns(double startX, double startY) {
        double y = startY;
        while (enemies.size() < def.count()) {
            add(startX, y);
            add(startX + COLUMN_SPACING, y);
            add(startX + 2 * COLUMN_SPACING, y);
            y -= ROW_PITCH;
        }
    }

    /** Dice rows at a random column base, falling straight down. */
    private void spawnRain() {
        double y = -75;
        int rowDir = 1;
        while (enemies.size() < def.count()) {
            double base = random.nextDouble() * boardWidth;
            addWithDir(base, y, rowDir);
            addWithDir(base + COLUMN_SPACING, y, rowDir);
            addWithDir(base + 2 * COLUMN_SPACING, y, rowDir);
            y -= ROW_PITCH;
            rowDir = -rowDir;
        }
    }

    private void add(double x, double y) {
        enemies.add(new Enemy(def.width(), def.height(), x, y));
    }

    private void addWithDir(double x, double y, int dir) {
        Enemy enemy = new Enemy(def.width(), def.height(), x, y);
        enemy.setDir(dir);
        enemies.add(enemy);
    }

    private static int[] identity(int n) {
        int[] seq = new int[n];
        for (int i = 0; i < n; i++) {
            seq[i] = i;
        }
        return seq;
    }

    private double wrapX(double x) {
        return x >= boardWidth ? x - boardWidth : x;
    }

    public void update(int boardWidth, int boardHeight) {
        if (enemies.isEmpty()) {
            return;
        }
        animTick++;
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
        double speed = sweepSpeed();
        for (Enemy enemy : enemies) {
            enemy.moveBy(speed, 0);
            if (enemy.getX() >= boardWidth) {
                // Ring wrap keeps the original's even spacing intact.
                enemy.moveBy(-boardWidth, 0);
            }
        }
    }

    /** Second-loop hamburgers periodically pause, then dash before settling down. */
    private double sweepSpeed() {
        if (!secondLoop || waveIndex != 0) {
            return def.velX();
        }
        if (--sweepPhaseTimer <= 0) {
            sweepPhase = (sweepPhase + 1) % 3;
            sweepPhaseTimer = switch (sweepPhase) {
                case 1 -> PHASE_PAUSE_FRAMES;
                case 2 -> PHASE_DASH_FRAMES;
                default -> PHASE_CRUISE_FRAMES;
            };
        }
        return switch (sweepPhase) {
            case 1 -> 0;
            case 2 -> DASH_SPEED;
            default -> def.velX();
        };
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
            enemy.moveBy(def.velX(), def.velY() * dirY);
            if (def.bobOnBounds() && (enemy.getY() >= SHOT_MAX_Y || enemy.getY() <= 15)) {
                bounce = true;
            }
            if (enemy.getX() >= boardWidth) {
                enemy.moveBy(-boardWidth, 0);
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
            if (secondLoop) {
                // Cookies and tires dive a full drop quickly instead of stepping.
                diveFramesLeft = DIVE_FRAMES;
            } else {
                for (Enemy enemy : enemies) {
                    enemy.moveBy(0, ZIGZAG_STEP_NOW);
                }
                zigzagStepCountdown = ZIGZAG_STEP_DELAY_FRAMES;
            }
            if (def.perEnemyDir()) {
                for (Enemy enemy : enemies) {
                    enemy.flipDir();
                }
            }
        }
        if (zigzagStepCountdown >= 0 && --zigzagStepCountdown < 0) {
            for (Enemy enemy : enemies) {
                enemy.moveBy(0, ZIGZAG_STEP_LATER);
            }
        }
        if (diveFramesLeft > 0) {
            diveFramesLeft--;
            double diveSpeed = waveIndex == 3 ? TIRE_DIVE_SPEED : COOKIE_DIVE_SPEED;
            for (Enemy enemy : enemies) {
                enemy.moveBy(0, diveSpeed);
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
            // Second-loop dice fall at ~45 degrees, rows alternating left and right.
            double drift = secondLoop ? RAIN_DRIFT_SPEED * enemy.getDir() : 0;
            enemy.moveBy(drift, def.velY());
            if (enemy.getX() >= boardWidth) {
                enemy.moveBy(-boardWidth - ENEMY_W, 0);
            } else if (enemy.getX() + ENEMY_W <= 0) {
                enemy.moveBy(boardWidth + ENEMY_W, 0);
            }
            if (enemy.getY() >= VERTICAL_WRAP_Y) {
                enemy.setY(-65);
                enemy.setX(wrapX(rainBaseX + rainWrapCounter * COLUMN_SPACING));
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
                enemy.moveBy(-SWEEP_RETREAT_SHIFT, 0);
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
        BufferedImage frame =
                frames[frameSequence[(animTick / 4) % frameSequence.length]];
        for (Enemy enemy : enemies) {
            enemy.render(g, frame);
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
