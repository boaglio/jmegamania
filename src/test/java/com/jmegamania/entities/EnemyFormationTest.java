package com.jmegamania.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyFormationTest {

    private static final int BOARD_WIDTH = 320;
    private static final int BOARD_HEIGHT = 186;

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
        int[] expected = {15, 18, 15, 18, 15, 18, 15, 18};
        for (int wave = 0; wave < EnemyFormation.WAVE_COUNT; wave++) {
            EnemyFormation formation = new EnemyFormation(wave, BOARD_WIDTH);
            assertEquals(expected[wave], formation.getEnemies().size(), "wave " + wave);
        }
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
    void retreatClearsShotsAndKeepsSurvivors() {
        EnemyFormation formation = new EnemyFormation(0, BOARD_WIDTH);
        for (int frame = 0; frame < 600; frame++) {
            formation.update(BOARD_WIDTH, BOARD_HEIGHT);
        }
        int survivors = formation.getEnemies().size();
        formation.retreat();
        assertTrue(formation.getShots().isEmpty());
        assertEquals(survivors, formation.getEnemies().size());
        for (Enemy enemy : formation.getEnemies()) {
            assertTrue(enemy.getX() < 0, "survivors should re-enter from off screen");
        }
    }
}
