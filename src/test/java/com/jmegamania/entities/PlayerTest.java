package com.jmegamania.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the player ship: it must stay inside the playfield walls
 * and its death must play out over a fixed number of frames, matching the
 * original Megamania timing.
 */
class PlayerTest {

    private static final int BOARD_WIDTH = 320;
    // Mirrors Player's internal HORIZONTAL_MARGIN / WIDTH so the expected clamp
    // bounds are spelled out here rather than copied blindly from the class.
    private static final int MARGIN = 5;
    private static final int WIDTH = Player.WIDTH;
    private static final int LEFT_LIMIT = MARGIN;
    private static final int RIGHT_LIMIT = BOARD_WIDTH - MARGIN - WIDTH;

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
    void deathCompletesExactlyAtTheEndOfTheAnimation() {
        Player player = new Player(150, 200);
        player.die();

        // The ship is mid-death for the whole flash animation and only vanishes
        // on the final frame; dying one frame early would be a visible regression.
        for (int frame = 1; frame < 60; frame++) {
            player.updateDeath();
            assertFalse(player.isDead(), "ship still dying on frame " + frame + " of 60");
        }
        player.updateDeath();
        assertTrue(player.isDead(), "ship should be gone once the 60-frame death completes");
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
        for (int frame = 0; frame < 120; frame++) {
            player.updateDeath();
        }
        assertFalse(player.isDead(), "a ship that never died must never be marked dead");
    }
}
