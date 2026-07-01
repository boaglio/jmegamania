package com.jmegamania;

import com.jmegamania.entities.Player;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class GamePanel extends JPanel implements Runnable {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 240;
    public static final int SCALE = 2;
    private static final int TARGET_FPS = 60;
    private static final long NANOS_PER_FRAME = 1_000_000_000L / TARGET_FPS;

    private final Player player = new Player(WIDTH / 2, HEIGHT - 20);
    private Thread gameThread;
    private volatile boolean running;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                player.keyPressed(e.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                player.keyReleased(e.getKeyCode());
            }
        });
    }

    public void start() {
        running = true;
        gameThread = new Thread(this, "game-loop");
        gameThread.start();
        requestFocusInWindow();
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        long accumulator = 0;

        while (running) {
            long now = System.nanoTime();
            accumulator += now - lastTime;
            lastTime = now;

            while (accumulator >= NANOS_PER_FRAME) {
                update();
                accumulator -= NANOS_PER_FRAME;
            }

            repaint();

            long sleepMillis = (NANOS_PER_FRAME - (System.nanoTime() - now)) / 1_000_000L;
            if (sleepMillis > 0) {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
    }

    private void update() {
        player.update(WIDTH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.scale(SCALE, SCALE);

        player.render(g2);

        g2.dispose();
    }
}
