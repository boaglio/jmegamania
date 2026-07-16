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
 * One attack wave, reimplementing the movement, formation, and firing logic of
 * the original ROM (see the MegaMania disassembly linked in the README).
 *
 * Even attack waves (hamburgers, bugs, diamonds, bow ties) are the horizontal
 * ones: a 5/6/5 formation of two sprite clusters per row that sweeps rightward
 * around the wrap ring while the whole formation bobs vertically in a triangle
 * wave. Odd attack waves (cookies, tires, irons, dice) are the vertical ones:
 * six row sections that scroll down one section at a time, the bottom row
 * recycling to the top at a random column.
 *
 * All positions are kept in original hardware units (160 pixels wide, 144
 * scanlines tall) and scaled 2x horizontally / 1.25x vertically for rendering.
 */
public class EnemyFormation {

    public static final int WAVE_COUNT = 8;

    private static final int FIELD_W_HW = 160;
    private static final int SCALE_X = 2;
    private static final double SCALE_Y = 1.25;

    private record WaveDef(int points, int width, int height, int frameCount) {
    }

    private static final int ENEMY_W = 16;
    private static final int H_ENEMY_H = 10;

    // Heights are the sprites' visible scanline counts scaled by 1.25 (the
    // ROM draws 8 lines for most objects, 9 for tires, 13 for dice).
    private static final WaveDef[] WAVES = {
            new WaveDef(20, ENEMY_W, H_ENEMY_H, 3),   // hamburgers
            new WaveDef(30, ENEMY_W, 10, 3),          // cookies
            new WaveDef(40, ENEMY_W, H_ENEMY_H, 3),   // bugs
            new WaveDef(50, ENEMY_W, 11, 3),          // radial tires
            new WaveDef(60, ENEMY_W, H_ENEMY_H, 4),   // diamonds
            new WaveDef(70, ENEMY_W, 10, 3),          // steam irons
            new WaveDef(80, ENEMY_W, H_ENEMY_H, 4),   // bow ties
            new WaveDef(90, ENEMY_W, 16, 16),         // space dice
    };

    // Horizontal waves (measured off the real ROM in an emulator): the first
    // row sits 5 scanlines below the formation top and rows are 14 apart.
    private static final int H_ROW_OFFSET = 5;
    private static final int H_ROW_PITCH = 14;

    // Vertical waves: the ROM kernel spaces row sections 29 scanlines apart,
    // and the top section grows one scanline per descent tick, pushing
    // everything below it down — so the whole field slides smoothly and,
    // with 29 ticks per scroll, the row shift is seamless. Sprites sit at
    // the bottom of each section's graphic window, their bottom edge 4
    // lines past the section origin. The bottom section lies below the
    // kernel: the ROM never draws it, so its row is off screen (and safe
    // from the blaster) until it recycles back to the top.
    private static final int V_SECTION_HW = 29;
    private static final int V_SPRITE_BOTTOM_HW = 4;

    // TIA missile height: shots are drawn 8 scanlines tall, upward from the
    // spawn offset, so their top lands H_MISSILE lines above it.
    private static final int H_MISSILE = 8;

    // ROM EnemyDecendingRate: how often the bob counter advances per even wave.
    private static final int[] BOB_RATE_MASK = {0xFF, 0, 0x0F, 0, 0x07, 0, 0x00, 0};
    // ROM InitEnemyMissileVertPosOffset, reordered top/middle/bottom row.
    private static final int[] MISSILE_DROP = {27, 41, 50};
    // ROM INIT_ENEMY_HORIZ_MOVE in both nybbles: alternating row directions.
    private static final int INIT_MOVE_PATTERN = 0xAA;

    /** One enemy slot of a horizontal wave's 5/6/5 formation. */
    private static final class HSlot {
        final int row;          // 0 = top .. 2 = bottom
        final int side;         // 0 = left cluster, 1 = right cluster
        final int offsetHw;     // offset from the formation base, hardware px
        final int revealStep;   // ROM mask step (age/16) at which it appears
        final Enemy enemy;
        boolean alive = true;

        HSlot(int row, int side, int offsetHw, int revealStep, Enemy enemy) {
            this.row = row;
            this.side = side;
            this.offsetHw = offsetHw;
            this.revealStep = revealStep;
            this.enemy = enemy;
        }
    }

    /** One row section of a vertical wave; slot i sits at baseXHw + i * 32. */
    private static final class VRow {
        int baseXHw;
        final Enemy[] slots = new Enemy[3];
    }

    private final int waveNumber;
    private final int attackWave;
    private final boolean horizontal;
    private final WaveDef def;
    private final BufferedImage[] frames;
    private final int[] frameSequence;
    private final List<EnemyShot> shots = new ArrayList<>();
    private final List<Enemy> visible = new ArrayList<>();
    // shotSlots[0]/[1] mirror the TIA's two missiles: horizontal waves may
    // have one shot per cluster side in flight, vertical waves only slot 0.
    private final EnemyShot[] shotSlots = new EnemyShot[2];

    private int frame;

    // Horizontal wave state.
    private final List<HSlot> hslots = new ArrayList<>();
    private int baseXHw;
    private int age;                // reveal counter (ROM tmpEnemyVertPos)
    private int bobCounter = 64;    // ROM enemyDecentRate

    // Vertical wave state.
    private final VRow[] rows = new VRow[6];   // 0 = bottom .. 5 = top
    private int tick;                          // ROM oddAttackWaveVertPos
    private int lowest = 6;                    // ROM lowestOddEnemySection
    private int movePattern = INIT_MOVE_PATTERN;
    private int randomSeed;                    // ROM 8-bit LFSR seed

    /** @param waveNumber absolute wave count; the attack wave is waveNumber & 7. */
    public EnemyFormation(int waveNumber, int boardWidth) {
        this.waveNumber = Math.max(0, waveNumber);
        this.attackWave = this.waveNumber & 7;
        this.horizontal = (attackWave & 1) == 0;
        this.def = WAVES[attackWave];
        this.frames = new BufferedImage[def.frameCount()];
        for (int i = 0; i < frames.length; i++) {
            this.frames[i] = Sprites.load(
                    String.format("enemy%02d_%d.png", attackWave + 1, i));
        }
        this.frameSequence = def.frameCount() == 4
                ? new int[]{0, 1, 2, 3, 2, 1}
                : identity(def.frameCount());
        this.randomSeed = 1 + new Random().nextInt(255);
        if (horizontal) {
            buildHorizontalSlots();
        } else {
            buildVerticalRows();
        }
        rebuildVisible();
    }

    /**
     * ROM InitEvenWaveEnemyPatterns: a 5x3 grid, uniformly spaced 32 hardware
     * pixels, with the whole middle row staggered +16 (the row spacing at
     * line "adc EvenEnemyHorizSpacingValues,y" applies to both clusters).
     * Each row is a left cluster (top and bottom rows lack the +0 slot) and a
     * right cluster at +96 whose third copy wraps around the 160px ring: on
     * the outer rows it lands on B+0, completing the even grid, and on the
     * middle row it lands exactly on the left cluster's first slot — the ROM
     * tracks it as a 16th pattern bit, but it pops in at the same reveal step,
     * dies to the same shot, and scores once, so it is one enemy here. The
     * reveal steps reproduce EnemyNUSIZIndexMaskingValues: one slot pops in
     * every 16 frames, broadly right cluster first.
     */
    private void buildHorizontalSlots() {
        int[][] leftSteps = {{-1, 8, 6}, {9, 7, 5}, {-1, 8, 6}};
        int[][] rightSteps = {{4, 2, 0}, {3, 1, -1}, {4, 2, 0}};
        for (int row = 0; row < 3; row++) {
            int stagger = row == 1 ? 16 : 0;
            for (int slot = 0; slot < 3; slot++) {
                if (leftSteps[row][slot] >= 0) {
                    hslots.add(new HSlot(row, 0, stagger + slot * 32, leftSteps[row][slot],
                            new Enemy(def.width(), def.height(), 0, 0)));
                }
                if (rightSteps[row][slot] >= 0) {
                    hslots.add(new HSlot(row, 1, stagger + 96 + slot * 32,
                            rightSteps[row][slot],
                            new Enemy(def.width(), def.height(), 0, 0)));
                }
            }
        }
    }

    /**
     * Six rows of three (ROM pattern %0111), all hidden until they scroll in
     * from the top. From the third loop, cookies — and the dice of wave 24 —
     * shrink to rows of two wide-spaced enemies (pattern %0101).
     */
    private void buildVerticalRows() {
        boolean twoWide = waveNumber >= 16 && (waveNumber == 23 || attackWave < 3);
        for (int section = 0; section < rows.length; section++) {
            VRow row = new VRow();
            for (int i = 0; i < 3; i++) {
                if (twoWide && i == 1) {
                    continue;
                }
                row.slots[i] = new Enemy(def.width(), def.height(), 0, 0);
            }
            rows[section] = row;
        }
    }

    private static int[] identity(int n) {
        int[] seq = new int[n];
        for (int i = 0; i < n; i++) {
            seq[i] = i;
        }
        return seq;
    }

    /** ROM NextRandom: an 8-bit LFSR. */
    private int nextRandom() {
        int t = ((randomSeed << 3) & 0xFF) ^ randomSeed;
        randomSeed = ((randomSeed << 1) | ((t >> 7) & 1)) & 0xFF;
        return randomSeed;
    }

    public int getPoints() {
        return def.points();
    }

    public void update(int boardWidth, int boardHeight) {
        frame++;
        freeShotSlots();
        if (horizontal) {
            updateHorizontal();
            fireHorizontal();
        } else {
            updateVertical();
            fireVertical();
        }
        moveShots(boardHeight);
        rebuildVisible();
    }

    // ------------------------------------------------------------------
    // Horizontal (even) waves
    // ------------------------------------------------------------------

    private void updateHorizontal() {
        if (age < 160) {
            age++;
        }
        if ((frame & BOB_RATE_MASK[attackWave]) == 0) {
            bobCounter = (bobCounter + 1) & 0xFF;
        }
        // One step per frame on the first loop; afterwards the 256-frame cycle
        // is 80 frames paused, 48 at double speed, 128 at normal speed.
        int steps = 1;
        if (waveNumber >= 7) {
            int f = frame & 0xFF;
            steps = f < 80 ? 0 : (f < 128 ? 2 : 1);
        }
        baseXHw = (baseXHw + steps) % FIELD_W_HW;
    }

    /**
     * ROM enemyDecentRate: the masked counter folds into a triangle wave, so
     * the formation descends up to 63 scanlines and rises back.
     */
    private int bobDescentHw() {
        int v = bobCounter & 0x7E;
        if (v >= 64) {
            v ^= 0x7F;
        }
        return 63 - v;
    }

    private void fireHorizontal() {
        // ROM gates firing on enemyVertPos >= 85: hold fire at the deepest dip.
        if (bobDescentHw() > 58) {
            return;
        }
        int side = (frame >> 4) & 1;
        if (shotSlots[side] != null) {
            return;
        }
        for (int row = 2; row >= 0; row--) {   // bottom row gets first claim
            int offset = leftmostRevealedOffset(side, row);
            if (offset < 0) {
                continue;
            }
            int xHw = (baseXHw + offset + 5) % FIELD_W_HW;
            double y = (bobDescentHw() + MISSILE_DROP[row] - H_MISSILE) * SCALE_Y;
            EnemyShot shot = new EnemyShot(xHw * SCALE_X, y, shotSpeed());
            shots.add(shot);
            shotSlots[side] = shot;
            return;
        }
    }

    private int leftmostRevealedOffset(int side, int row) {
        int revealIndex = Math.min(9, age / 16);
        int best = -1;
        for (HSlot slot : hslots) {
            if (slot.alive && slot.side == side && slot.row == row
                    && slot.revealStep <= revealIndex
                    && (best < 0 || slot.offsetHw < best)) {
                best = slot.offsetHw;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Vertical (odd) waves
    // ------------------------------------------------------------------

    private void updateVertical() {
        if (attackWave == 5) {
            updateIrons();
            return;
        }
        // Descent timer: 1 tick per 4 frames on the first loop, every frame
        // from wave 8, and twice per frame on wave 8's dice and the third loop.
        int iterations = ((waveNumber & 0x0F) == 7 || waveNumber >= 16) ? 2 : 1;
        for (int i = 0; i < iterations; i++) {
            if (waveNumber >= 7 || (frame & 0x50) == 0) {
                tick++;
            }
            if (tick >= 29) {
                scrollRowsDown();
            }
        }
        if (attackWave == 1) {
            // Cookies sweep in unison, turning on frame-counter bit 7.
            int dx = (frame & 0x80) != 0 ? 1 : -1;
            for (VRow row : rows) {
                row.baseXHw = wrapHw(row.baseXHw + dx);
            }
        } else if (attackWave == 3 || (attackWave == 7 && waveNumber >= 8)) {
            // Tires always, and dice from the second loop, move one pixel per
            // frame with per-row directions from the rotating pattern byte.
            moveRowsByPattern();
        }
    }

    /**
     * Steam irons: direction and descent both come from the ROM's random seed,
     * refreshed every 32 frames — jittering within the x 26..64 band and
     * descending in half-second bursts.
     */
    private void updateIrons() {
        if ((frame & 0x1F) == 0) {
            nextRandom();
        }
        if ((frame & 1) == 0 && (randomSeed & 7) >= 4) {
            tick++;
        }
        if (tick >= 29) {
            scrollRowsDown();
        }
        int dir = (randomSeed & 0x80) != 0 ? -1 : 1;
        for (VRow row : rows) {
            int x = row.baseXHw;
            if (((x - 26) & 0xFF) >= 39) {
                x = 32;   // ROM re-centers strays into the band
            }
            x += dir;
            row.baseXHw = Math.max(26, Math.min(64, x));
        }
    }

    /**
     * All rows shift down one section; the bottom row's survivors recycle to
     * the top — at a new random column, except on the iron wave.
     */
    private void scrollRowsDown() {
        VRow bottom = rows[0];
        System.arraycopy(rows, 1, rows, 0, rows.length - 1);
        rows[rows.length - 1] = bottom;
        if (lowest > 0) {
            lowest--;
        }
        if (attackWave != 5) {
            bottom.baseXHw = (nextRandom() & 0x7F) + 16;
        }
        movePattern = ((movePattern >> 1) | ((movePattern & 1) << 7)) & 0xFF;
        tick = 0;
    }

    private void moveRowsByPattern() {
        for (int section = 5; section >= 0; section--) {
            boolean left = (movePattern & (1 << (section + 2))) != 0;
            rows[section].baseXHw = wrapHw(rows[section].baseXHw + (left ? -1 : 1));
        }
    }

    private void fireVertical() {
        if (attackWave == 7) {
            return;   // space dice never fire
        }
        if (lowest >= 5) {
            return;   // the firing row (section 4) has not scrolled in yet
        }
        if (shotSlots[0] != null) {
            return;
        }
        VRow row = rows[4];
        int offset = -1;
        for (int i = 0; i < row.slots.length; i++) {
            if (row.slots[i] != null) {
                offset = i * 32;
                break;
            }
        }
        if (offset < 0) {
            return;
        }
        int xHw = (row.baseXHw + offset + 5) % FIELD_W_HW;
        // The ROM spawns the missile level with the firing row's sprite, so
        // the shot emerges from the enemy itself (verified in an emulator).
        double y = (25 + tick) * SCALE_Y;
        EnemyShot shot = new EnemyShot(xHw * SCALE_X, y, shotSpeed());
        shots.add(shot);
        shotSlots[0] = shot;
    }

    // ------------------------------------------------------------------
    // Shots and bookkeeping
    // ------------------------------------------------------------------

    /** Shots fall 2 scanlines per frame, 3 from the third loop. */
    private double shotSpeed() {
        return (waveNumber >= 16 ? 3 : 2) * SCALE_Y;
    }

    private void freeShotSlots() {
        for (int i = 0; i < shotSlots.length; i++) {
            if (shotSlots[i] != null && !shots.contains(shotSlots[i])) {
                shotSlots[i] = null;
            }
        }
    }

    private void moveShots(int boardHeight) {
        Iterator<EnemyShot> it = shots.iterator();
        while (it.hasNext()) {
            EnemyShot shot = it.next();
            shot.update();
            if (shot.isOffScreen(boardHeight)) {
                it.remove();
            }
        }
    }

    private static int wrapHw(int x) {
        if (x < 0) {
            return FIELD_W_HW - 1;
        }
        if (x >= FIELD_W_HW) {
            return 0;
        }
        return x;
    }

    private void rebuildVisible() {
        visible.clear();
        if (horizontal) {
            int revealIndex = Math.min(9, age / 16);
            for (HSlot slot : hslots) {
                if (!slot.alive || slot.revealStep > revealIndex) {
                    continue;
                }
                slot.enemy.setX(((baseXHw + slot.offsetHw) % FIELD_W_HW) * SCALE_X);
                slot.enemy.setY((bobDescentHw() + H_ROW_OFFSET
                        + slot.row * H_ROW_PITCH) * SCALE_Y);
                visible.add(slot.enemy);
            }
        } else {
            // Section 0 sits below the ROM's kernel: it is never drawn, so
            // its row stays hidden until it recycles back to the top.
            for (int section = 5; section >= 1; section--) {
                if (section < lowest) {
                    continue;
                }
                // Every row moves down one scanline per descent tick; the top
                // row slides in from behind the top edge.
                double y = (tick + V_SPRITE_BOTTOM_HW + (5 - section) * V_SECTION_HW)
                        * SCALE_Y - def.height();
                VRow row = rows[section];
                for (int i = 0; i < row.slots.length; i++) {
                    Enemy enemy = row.slots[i];
                    if (enemy == null) {
                        continue;
                    }
                    enemy.setX(((row.baseXHw + i * 32) % FIELD_W_HW) * SCALE_X);
                    enemy.setY(y);
                    visible.add(enemy);
                }
            }
        }
    }

    /**
     * After the blaster is destroyed the survivors reset to their entry state:
     * horizontal waves re-reveal from the formation origin, vertical waves
     * scroll back in from the top, one row at a time.
     */
    public void retreat() {
        clearShots();
        if (horizontal) {
            baseXHw = 0;
            age = 0;
            bobCounter = 64;
        } else {
            lowest = 6;
            tick = 0;
            for (VRow row : rows) {
                row.baseXHw = 0;
            }
        }
        rebuildVisible();
    }

    public void clearShots() {
        shots.clear();
        for (int i = 0; i < shotSlots.length; i++) {
            shotSlots[i] = null;
        }
    }

    public void render(Graphics2D g) {
        BufferedImage frameImage =
                frames[frameSequence[(frame / 4) % frameSequence.length]];
        for (Enemy enemy : visible) {
            enemy.render(g, frameImage);
        }
        for (EnemyShot shot : shots) {
            shot.render(g);
        }
    }

    /** Enemies currently on screen (hidden ones can be neither shot nor rammed). */
    public List<Enemy> getEnemies() {
        return Collections.unmodifiableList(visible);
    }

    public List<EnemyShot> getShots() {
        return Collections.unmodifiableList(shots);
    }

    public int aliveCount() {
        int count = 0;
        if (horizontal) {
            for (HSlot slot : hslots) {
                if (slot.alive) {
                    count++;
                }
            }
        } else {
            for (VRow row : rows) {
                for (Enemy enemy : row.slots) {
                    if (enemy != null) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public boolean isCleared() {
        return aliveCount() == 0;
    }

    public void clear() {
        for (HSlot slot : hslots) {
            slot.alive = false;
        }
        for (VRow row : rows) {
            if (row == null) {
                continue;
            }
            for (int i = 0; i < row.slots.length; i++) {
                row.slots[i] = null;
            }
        }
        clearShots();
        visible.clear();
    }

    public void remove(Enemy enemy) {
        if (horizontal) {
            for (HSlot slot : hslots) {
                if (slot.enemy == enemy) {
                    slot.alive = false;
                }
            }
        } else {
            for (VRow row : rows) {
                for (int i = 0; i < row.slots.length; i++) {
                    if (row.slots[i] == enemy) {
                        row.slots[i] = null;
                    }
                }
            }
        }
        visible.remove(enemy);
    }

    public void removeShot(EnemyShot shot) {
        shots.remove(shot);
        for (int i = 0; i < shotSlots.length; i++) {
            if (shotSlots[i] == shot) {
                shotSlots[i] = null;
            }
        }
    }
}
