package com.jmegamania.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the player ship: it must stay inside the original
 * ROM's walls (hardware pixels 24..132, doubled here), refire while the
 * button is held (game 1's guided-missile debounce), and die over the ROM's
 * 31-step (124-frame) color ramp.
 */
class PlayerTest {

    private static final int BOARD_WIDTH = 320;
    private static final int LEFT_LIMIT = Player.X_MIN;
    private static final int RIGHT_LIMIT = Player.X_MAX;
    private static final int DEATH_FRAMES = 124;

    private static int xOf(Player player) {
        return player.getBounds().x;
    }

    @Test
    void stopsAtLeftWallInsteadOfLeavingTheScreen() {
        Player player = new Player(LEFT_LIMIT + 1, 200);
        player.keyPressed(KeyEvent.VK_LEFT);

        // One step would carry it past the wall; the clamp must hold it at the edge.
        player.update(BOARD_WIDTH);
        assertEquals(LEFT_LIMIT, xOf(player), "ship should clamp to the left wall");

        // Further updates while still pushing left must not move it further out.
        player.update(BOARD_WIDTH);
        assertEquals(LEFT_LIMIT, xOf(player), "ship must not drift past the left wall");
    }

    @Test
    void stopsAtRightWallInsteadOfLeavingTheScreen() {
        Player player = new Player(RIGHT_LIMIT - 1, 200);
        player.keyPressed(KeyEvent.VK_RIGHT);

        player.update(BOARD_WIDTH);
        assertEquals(RIGHT_LIMIT, xOf(player), "ship should clamp to the right wall");

        player.update(BOARD_WIDTH);
        assertEquals(RIGHT_LIMIT, xOf(player), "ship must not drift past the right wall");
    }

    @Test
    void oppositeKeysHeldTogetherCancelOut() {
        int startX = 150;
        Player player = new Player(startX, 200);
        player.keyPressed(KeyEvent.VK_LEFT);
        player.keyPressed(KeyEvent.VK_RIGHT);

        player.update(BOARD_WIDTH);

        assertEquals(startX, xOf(player), "left and right held together should net zero movement");
    }

    @Test
    void releasingAKeyStopsThatDirection() {
        Player player = new Player(150, 200);
        player.keyPressed(KeyEvent.VK_RIGHT);
        player.update(BOARD_WIDTH);
        int afterMoving = xOf(player);
        assertTrue(afterMoving > 150, "ship should have moved right while the key was held");

        player.keyReleased(KeyEvent.VK_RIGHT);
        player.update(BOARD_WIDTH);
        assertEquals(afterMoving, xOf(player), "ship should hold still once the key is released");
    }

    @Test
    void holdingFireKeepsShootingUntilReleased() {
        Player player = new Player(150, 200);
        player.keyPressed(KeyEvent.VK_SPACE);
        assertTrue(player.isShooting(), "pressing fire must shoot");
        assertTrue(player.isShooting(), "holding fire must keep shooting");

        player.keyReleased(KeyEvent.VK_SPACE);
        assertFalse(player.isShooting(), "releasing fire must stop shooting");

        player.keyPressed(KeyEvent.VK_SPACE);
        player.die();
        assertFalse(player.isShooting(), "death must cancel the held trigger");
    }

    @Test
    void deathCompletesExactlyAtTheEndOfTheRamp() {
        Player player = new Player(150, 200);
        player.die();

        // The ship is mid-death for the whole 31-step color ramp and only
        // vanishes on the final frame, as in the original ROM.
        for (int frame = 1; frame < DEATH_FRAMES; frame++) {
            player.updateDeath();
            assertFalse(player.isDead(),
                    "ship still dying on frame " + frame + " of " + DEATH_FRAMES);
        }
        player.updateDeath();
        assertTrue(player.isDead(), "ship should be gone once the death ramp completes");
    }

    @Test
    void dyingCancelsPendingMovementInput() {
        Player player = new Player(150, 200);
        player.keyPressed(KeyEvent.VK_RIGHT);
        player.die();

        player.update(BOARD_WIDTH);

        assertEquals(150, xOf(player), "a dying ship must not keep coasting on old input");
    }

    @Test
    void updateDeathDoesNothingForALivingShip() {
        Player player = new Player(150, 200);
        for (int frame = 0; frame < 200; frame++) {
            player.updateDeath();
        }
        assertFalse(player.isDead(), "a ship that never died must never be marked dead");
    }
}
