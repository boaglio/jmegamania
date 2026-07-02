package com.jmegamania;

import com.jmegamania.engine.Sprites;
import com.jmegamania.entities.Bullet;
import com.jmegamania.entities.Enemy;
import com.jmegamania.entities.EnemyFormation;
import com.jmegamania.entities.EnemyShot;
import com.jmegamania.entities.Player;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GamePanel extends JPanel implements Runnable {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 240;
    public static final int SCALE = 2;
    private static final int TARGET_FPS = 60;
    private static final long NANOS_PER_FRAME = 1_000_000_000L / TARGET_FPS;
    private static final int HUD_HEIGHT = 54;
    private static final int PLAYFIELD_HEIGHT = HEIGHT - HUD_HEIGHT;
    private static final int PLAYER_START_X = (WIDTH - Player.WIDTH) / 2;
    private static final int PLAYER_START_Y = PLAYFIELD_HEIGHT - Player.HEIGHT - 3;
    private static final int STARTING_LIVES = 3;
    private static final int ENEMY_KILL_SCORE = 100;
    private static final Color HUD_GRAY = new Color(144, 144, 144);
    private static final Color ENERGY_RED = new Color(164, 26, 28);
    private static final Color ENERGY_YELLOW = new Color(212, 211, 41);
    private static final BufferedImage LIVES_ICON = Sprites.load("lifes.png");
    private static final int LIVES_ICON_HEIGHT = 18;
    private static final int LIVES_ICON_WIDTH =
            LIVES_ICON_HEIGHT * LIVES_ICON.getWidth() / LIVES_ICON.getHeight();
    private static final double TIMER_MAX = 320;
    private static final double TIMER_INCREMENT = 0.08;
    private static final double TIMER_DRAIN_PER_FRAME = 8;

    private final Player player = new Player(PLAYER_START_X, PLAYER_START_Y);
    private final List<Bullet> bullets = new ArrayList<>();
    private final EnemyFormation enemyFormation = new EnemyFormation(15, 12);
    private int score;
    private int lives = STARTING_LIVES;
    private double timer;
    private boolean resetting;
    private boolean gameOver;
    private Thread gameThread;
    private volatile boolean running;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (gameOver) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        restart();
                    }
                    return;
                }
                player.keyPressed(e.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                player.keyReleased(e.getKeyCode());
            }
        });
    }

    private void restart() {
        score = 0;
        lives = STARTING_LIVES;
        timer = 0;
        resetting = false;
        gameOver = false;
        bullets.clear();
        player.resetPosition(PLAYER_START_X);
        enemyFormation.reset();
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
        if (gameOver) {
            return;
        }

        if (resetting) {
            timer -= TIMER_DRAIN_PER_FRAME;
            if (timer <= 0) {
                timer = 0;
                resetting = false;
            }
            return;
        }

        player.update(WIDTH);
        enemyFormation.update(WIDTH, PLAYFIELD_HEIGHT);

        if (player.isShooting() && bullets.isEmpty()) {
            bullets.add(new Bullet(player.getMuzzleX(), player.getMuzzleY()));
        }

        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet bullet = it.next();
            bullet.update(player.getMuzzleX());
            if (bullet.isOffScreen()) {
                it.remove();
                continue;
            }

            for (Enemy enemy : enemyFormation.getEnemies()) {
                if (bullet.getBounds().intersects(enemy.getBounds())) {
                    enemyFormation.remove(enemy);
                    it.remove();
                    score += ENEMY_KILL_SCORE;
                    break;
                }
            }
        }

        for (EnemyShot shot : enemyFormation.getShots()) {
            if (shot.getBounds().intersects(player.getBounds())) {
                enemyFormation.removeShot(shot);
                timer = TIMER_MAX;
                break;
            }
        }

        for (Enemy enemy : enemyFormation.getEnemies()) {
            if (enemy.getBounds().intersects(player.getBounds())) {
                timer = TIMER_MAX;
                break;
            }
        }

        if (enemyFormation.getEnemies().isEmpty()) {
            enemyFormation.reset();
        }

        timer = Math.min(TIMER_MAX, timer + TIMER_INCREMENT);
        if (timer >= TIMER_MAX) {
            lives--;
            if (lives <= 0) {
                gameOver = true;
            } else {
                resetting = true;
                enemyFormation.pushOffScreen(WIDTH);
                player.resetPosition(PLAYER_START_X);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.scale(SCALE, SCALE);

        player.render(g2);
        for (Bullet bullet : bullets) {
            bullet.render(g2);
        }
        enemyFormation.render(g2);

        renderHud(g2);
        if (gameOver) {
            renderGameOver(g2);
        }

        g2.dispose();
    }

    private void renderHud(Graphics2D g2) {
        g2.setColor(HUD_GRAY);
        g2.fillRect(0, PLAYFIELD_HEIGHT, WIDTH, HUD_HEIGHT);

        int barWidth = 220;
        int barX = (WIDTH - barWidth) / 2;
        int barY = PLAYFIELD_HEIGHT + 8;
        int barHeight = 12;

        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
        g2.setColor(Color.BLACK);
        FontMetrics energyMetrics = g2.getFontMetrics();
        int barCenterY = barY + barHeight / 2;
        int energyTextBaseline = barCenterY + (energyMetrics.getAscent() - energyMetrics.getDescent()) / 2;
        g2.drawString("ENERGY", 8, energyTextBaseline);

        g2.setColor(ENERGY_RED);
        g2.fillRect(barX, barY, barWidth, barHeight);
        int yellowWidth = (int) Math.round(barWidth * (1 - timer / TIMER_MAX));
        g2.setColor(ENERGY_YELLOW);
        g2.fillRect(barX, barY, yellowWidth, barHeight);

        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        g2.setColor(Color.BLACK);
        int textY = PLAYFIELD_HEIGHT + HUD_HEIGHT - 8;
        g2.drawString("SCORE " + score, 8, textY);

        String livesText = "x" + lives;
        FontMetrics metrics = g2.getFontMetrics();
        int groupWidth = LIVES_ICON_WIDTH + 4 + metrics.stringWidth(livesText);
        int groupX = WIDTH - groupWidth - 8;
        int iconY = textY - LIVES_ICON_HEIGHT + 4;
        g2.drawImage(LIVES_ICON, groupX, iconY, LIVES_ICON_WIDTH, LIVES_ICON_HEIGHT, null);
        g2.drawString(livesText, groupX + LIVES_ICON_WIDTH + 4, textY);
    }

    private void renderGameOver(Graphics2D g2) {
        g2.setColor(Color.WHITE);

        Font titleFont = new Font(Font.MONOSPACED, Font.BOLD, 20);
        g2.setFont(titleFont);
        drawCentered(g2, "GAME OVER", HEIGHT / 2 - 10);

        Font promptFont = new Font(Font.MONOSPACED, Font.PLAIN, 10);
        g2.setFont(promptFont);
        drawCentered(g2, "PRESS ENTER TO RESTART", HEIGHT / 2 + 14);
    }

    private void drawCentered(Graphics2D g2, String text, int y) {
        FontMetrics metrics = g2.getFontMetrics();
        int x = (WIDTH - metrics.stringWidth(text)) / 2;
        g2.drawString(text, x, y);
    }
}
