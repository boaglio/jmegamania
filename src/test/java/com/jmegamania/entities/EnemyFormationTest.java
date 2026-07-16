package com.jmegamania.entities;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyFormationTest {

    private static final int BOARD_WIDTH = 320;
    private static final int BOARD_HEIGHT = 186;

    private static void run(EnemyFormation formation, int frames) {
        for (int i = 0; i < frames; i++) {
            formation.update(BOARD_WIDTH, BOARD_HEIGHT);
        }
    }

    @Test
    void eachWaveHasOriginalPointValue() {
        int[] expected = {20, 30, 40, 50, 60, 70, 80, 90};
        for (int wave = 0; wave < EnemyFormation.WAVE_COUNT; wave++) {
            EnemyFormation formation = new EnemyFormation(wave, BOARD_WIDTH);
            assertEquals(expected[wave], formation.getPoints(), "wave " + wave);
        }
    }

    @Test
    void horizontalWavesSpawnFifteenAndVerticalWavesEighteen() {
        // Horizontal waves are a staggered 5x3 grid (the ROM's 16th pattern
        // bit is permanently coincident with the middle row's first slot).
        int[] expected = {15, 18, 15, 18, 15, 18, 15, 18};
        for (int wave = 0; wave < EnemyFormation.WAVE_COUNT; wave++) {
            EnemyFormation formation = new EnemyFormation(wave, BOARD_WIDTH);
            assertEquals(expected[wave], formation.aliveCount(), "wave " + wave);
        }
    }

    @Test
    void thirdLoopCookiesShrinkToRowsOfTwo() {
        assertEquals(12, new EnemyFormation(17, BOARD_WIDTH).aliveCount(),
                "third-loop cookies come in rows of two");
        assertEquals(12, new EnemyFormation(23, BOARD_WIDTH).aliveCount(),
                "wave 24's dice come in rows of two");
        assertEquals(18, new EnemyFormation(15, BOARD_WIDTH).aliveCount(),
                "second-loop dice keep rows of three");
    }

    @Test
    void horizontalRowsFormAUniformStaggeredGrid() {
        EnemyFormation formation = new EnemyFormation(0, BOARD_WIDTH);
        run(formation, 160);   // complete the reveal

        Map<Double, List<Double>> rows = new TreeMap<>();
        for (Enemy enemy : formation.getEnemies()) {
            rows.computeIfAbsent(enemy.getY(), y -> new ArrayList<>()).add(enemy.getX());
        }
        assertEquals(3, rows.size(), "three rows");

        List<List<Double>> byRow = new ArrayList<>(rows.values());
        for (List<Double> xs : byRow) {
            assertEquals(5, xs.size(), "five columns per row");
            Collections.sort(xs);
            for (int i = 0; i < xs.size(); i++) {
                double gap = Math.floorMod(
                        (int) Math.round(xs.get((i + 1) % 5) - xs.get(i)), BOARD_WIDTH);
                assertEquals(64, gap, "columns must tile the ring evenly");
            }
        }
        // The middle row is staggered by half a column spacing.
        int top = (int) Math.round(byRow.get(0).get(0)) % 64;
        int middle = (int) Math.round(byRow.get(1).get(0)) % 64;
        int bottom = (int) Math.round(byRow.get(2).get(0)) % 64;
        assertEquals(32, Math.floorMod(middle - top, 64), "middle row staggered +32");
        assertEquals(top, bottom, "top and bottom rows aligned");
    }

    @Test
    void everyWaveEventuallyEntersTheVisibleField() {
        for (int wave = 0; wave < EnemyFormation.WAVE_COUNT; wave++) {
            EnemyFormation formation = new EnemyFormation(wave, BOARD_WIDTH);
            boolean visible = false;
            for (int frame = 0; frame < 1200 && !visible; frame++) {
                formation.update(BOARD_WIDTH, BOARD_HEIGHT);
                for (Enemy enemy : formation.getEnemies()) {
                    if (enemy.getX() >= 0 && enemy.getX() + enemy.getWidth() <= BOARD_WIDTH
                            && enemy.getY() >= 0) {
                        visible = true;
                        break;
                    }
                }
            }
            assertTrue(visible, "wave " + wave + " never entered the playfield");
        }
    }

    @Test
    void wavesWithGunsFireShotsAndDiceDoNot() {
        for (int wave = 0; wave < EnemyFormation.WAVE_COUNT; wave++) {
            EnemyFormation formation = new EnemyFormation(wave, BOARD_WIDTH);
            boolean fired = false;
            for (int frame = 0; frame < 3600 && !fired; frame++) {
                formation.update(BOARD_WIDTH, BOARD_HEIGHT);
                fired = !formation.getShots().isEmpty();
            }
            if (wave == 7) {
                assertFalse(fired, "space dice must not shoot");
            } else {
                assertTrue(fired, "wave " + wave + " never fired");
            }
        }
    }

    @Test
    void shotsRespectTheHardwareMissileSlots() {
        // Horizontal waves own two missile slots, vertical waves only one.
        for (int wave = 0; wave < 2; wave++) {
            EnemyFormation formation = new EnemyFormation(wave, BOARD_WIDTH);
            int maxConcurrent = 0;
            for (int frame = 0; frame < 3600; frame++) {
                formation.update(BOARD_WIDTH, BOARD_HEIGHT);
                maxConcurrent = Math.max(maxConcurrent, formation.getShots().size());
            }
            assertTrue(maxConcurrent <= 2 - wave,
                    "wave " + wave + " exceeded its missile slots: " + maxConcurrent);
            assertTrue(maxConcurrent > 0, "wave " + wave + " never fired");
        }
    }

    @Test
    void firstLoopDiceFallStraightButSecondLoopDiceDrift() {
        for (boolean secondLoop : new boolean[]{false, true}) {
            EnemyFormation formation = new EnemyFormation(secondLoop ? 15 : 7, BOARD_WIDTH);
            Map<Enemy, double[]> previous = new IdentityHashMap<>();
            boolean drifted = false;
            for (int frame = 0; frame < 300; frame++) {
                formation.update(BOARD_WIDTH, BOARD_HEIGHT);
                for (Enemy die : formation.getEnemies()) {
                    double[] prev = previous.get(die);
                    double dy = prev == null ? -1 : die.getY() - prev[1];
                    // Only normally-descending dice count (recycled rows jump
                    // up and may land at a new column); screen wraps show up
                    // as huge dx values and are skipped too.
                    if (prev != null && dy >= 0 && dy <= 2.6) {
                        double dx = Math.abs(die.getX() - prev[0]);
                        if (dx > 0 && dx <= 20) {
                            drifted = true;
                            assertTrue(secondLoop,
                                    "loop-1 dice must fall straight (saw dx=" + dx + ")");
                        }
                    }
                    previous.put(die, new double[]{die.getX(), die.getY()});
                }
            }
            if (secondLoop) {
                assertTrue(drifted, "loop-2 dice must drift sideways");
            }
        }
    }

    @Test
    void verticalShotsEmergeFromTheFiringEnemy() {
        // The ROM spawns the enemy missile level with the firing row's
        // sprite, so the shot slides out of the enemy with no gap.
        EnemyFormation formation = new EnemyFormation(1, BOARD_WIDTH);
        for (int frame = 0; frame < 3600; frame++) {
            formation.update(BOARD_WIDTH, BOARD_HEIGHT);
            if (formation.getShots().isEmpty()) {
                continue;
            }
            EnemyShot shot = formation.getShots().get(0);
            boolean overlapping = false;
            for (Enemy enemy : formation.getEnemies()) {
                if (shot.getBounds().intersects(enemy.getBounds())) {
                    overlapping = true;
                    break;
                }
            }
            assertTrue(overlapping, "the first shot must overlap its cookie");
            return;
        }
        assertTrue(false, "cookies never fired");
    }

    @Test
    void secondLoopHorizontalWavesPauseAndDash() {
        // From the second loop, every horizontal wave follows the 256-frame
        // rhythm: paused below frame 80, double speed until 128, then normal.
        for (int wave : new int[]{8, 10, 12, 14}) {
            EnemyFormation formation = new EnemyFormation(wave, BOARD_WIDTH);
            Enemy enemy = formation.getEnemies().get(0);
            double prevX = enemy.getX();
            for (int frame = 1; frame <= 255; frame++) {
                formation.update(BOARD_WIDTH, BOARD_HEIGHT);
                int dx = Math.floorMod((int) Math.round(enemy.getX() - prevX), BOARD_WIDTH);
                prevX = enemy.getX();
                if (frame < 80) {
                    assertEquals(0, dx, "wave " + wave + " must pause on frame " + frame);
                } else if (frame < 128) {
                    assertEquals(4, dx, "wave " + wave + " must dash on frame " + frame);
                } else {
                    assertEquals(2, dx, "wave " + wave + " must cruise on frame " + frame);
                }
            }
        }
    }

    @Test
    void firstLoopHorizontalWavesNeverPause() {
        EnemyFormation formation = new EnemyFormation(0, BOARD_WIDTH);
        Enemy enemy = formation.getEnemies().get(0);
        double prevX = enemy.getX();
        for (int frame = 1; frame <= 256; frame++) {
            formation.update(BOARD_WIDTH, BOARD_HEIGHT);
            int dx = Math.floorMod((int) Math.round(enemy.getX() - prevX), BOARD_WIDTH);
            prevX = enemy.getX();
            assertEquals(2, dx, "loop-1 hamburgers must sweep steadily");
        }
    }

    @Test
    void verticalWavesDescendSmoothly() {
        EnemyFormation formation = new EnemyFormation(1, BOARD_WIDTH);
        Map<Enemy, Double> previous = new IdentityHashMap<>();
        boolean descended = false;
        for (int frame = 0; frame < 600; frame++) {
            formation.update(BOARD_WIDTH, BOARD_HEIGHT);
            for (Enemy cookie : formation.getEnemies()) {
                Double prevY = previous.get(cookie);
                if (prevY != null) {
                    double dy = cookie.getY() - prevY;
                    // The whole field slides down at the tick rate (at most a
                    // couple of scanlines per frame); the only jumps allowed
                    // are upward, when the bottom row recycles to the top.
                    assertTrue((dy >= 0 && dy <= 2.6) || dy < 0,
                            "descent must be smooth, saw dy=" + dy);
                    if (dy > 0 && dy <= 2.6) {
                        descended = true;
                    }
                }
                previous.put(cookie, cookie.getY());
            }
        }
        assertTrue(descended, "rows must creep downward over time");
    }

    @Test
    void steamIronsStayInsideTheirHorizontalBand() {
        EnemyFormation formation = new EnemyFormation(5, BOARD_WIDTH);
        run(formation, 300);
        for (int frame = 0; frame < 600; frame++) {
            formation.update(BOARD_WIDTH, BOARD_HEIGHT);
            for (Enemy iron : formation.getEnemies()) {
                // Row bases are clamped to the ROM band 26..64 (52..128 scaled)
                // and each row's copies sit at +64/+128, so no iron may appear
                // left of 52 or right of 256.
                assertTrue(iron.getX() >= 52 && iron.getX() <= 256,
                        "iron outside band: " + iron.getX());
            }
        }
    }

    @Test
    void retreatResetsEntryAndKeepsSurvivors() {
        EnemyFormation formation = new EnemyFormation(0, BOARD_WIDTH);
        run(formation, 600);
        formation.remove(formation.getEnemies().get(0));
        int survivors = formation.aliveCount();
        assertEquals(14, survivors);

        formation.retreat();
        assertTrue(formation.getShots().isEmpty(), "retreat must clear enemy shots");
        assertEquals(survivors, formation.aliveCount(), "retreat must keep survivors");
        assertTrue(formation.getEnemies().size() < survivors,
                "survivors must re-enter gradually after a retreat");

        run(formation, 200);
        assertEquals(survivors, formation.getEnemies().size(),
                "all survivors must be back on screen after the re-entry");
    }
}
