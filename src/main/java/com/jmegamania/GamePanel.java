package com.jmegamania;

import com.jmegamania.engine.Sound;
import com.jmegamania.engine.Sprites;
import com.jmegamania.entities.Bullet;
import com.jmegamania.entities.Enemy;
import com.jmegamania.entities.EnemyFormation;
import com.jmegamania.entities.EnemyShot;
import com.jmegamania.entities.Player;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
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
    public static final int SCALE = 4;
    private static final int TARGET_FPS = 60;
    private static final long NANOS_PER_FRAME = 1_000_000_000L / TARGET_FPS;
    private static final int HUD_HEIGHT = 54;
    private static final int PLAYFIELD_HEIGHT = HEIGHT - HUD_HEIGHT;
    private static final int PLAYER_START_X = (WIDTH - Player.WIDTH) / 2;
    private static final int PLAYER_START_Y = PLAYFIELD_HEIGHT - Player.HEIGHT - 3;
    private static final int STARTING_LIVES = 3;
    // An extra blaster every 10,000 points, never more than six in reserve.
    private static final int EXTRA_SHIP_SCORE = 10_000;
    private static final int MAX_LIVES = 6;
    private static final Color HUD_GRAY = new Color(144, 144, 144);
    private static final Color ENERGY_RED = new Color(164, 26, 28);
    private static final Color ENERGY_YELLOW = new Color(212, 211, 41);
    private static final Color SCORE_BLUE = new Color(0, 0, 240);
    private static final BufferedImage LIVES_ICON = Sprites.load("lifes.png");
    private static final int LIVES_ICON_HEIGHT = 10;
    private static final int LIVES_ICON_WIDTH =
            LIVES_ICON_HEIGHT * LIVES_ICON.getWidth() / LIVES_ICON.getHeight();
    // Energy model taken from the original ROM: the counter at $E6 fills to $53 (83)
    // one unit per frame, drains one unit every 32 frames during play, and pays out
    // one unit per two frames as wave-clear bonus. The bar shows it in 20 chunks of 4.
    private static final int ENERGY_MAX = 83;
    private static final int ENERGY_DRAIN_FRAMES = 32;
    private static final int ENERGY_UNITS_PER_CHUNK = 4;
    private static final int ENERGY_BAR_CHUNKS = 20;
    private static final int BONUS_DRAIN_FRAMES = 2;

    // Dev aid: -Djmegamania.wave=N starts the game at wave N (0-7).
    private static final int START_WAVE =
            Math.floorMod(Integer.getInteger("jmegamania.wave", 0), EnemyFormation.WAVE_COUNT);

    private final Player player = new Player(PLAYER_START_X, PLAYER_START_Y);
    private final List<Bullet> bullets = new ArrayList<>();
    private EnemyFormation enemyFormation = new EnemyFormation(START_WAVE, WIDTH);
    private final Sound sfxShoot = Sound.load("shipShoot.wav");
    private final Sound sfxEnemyHit = Sound.load("enemyHit.wav");
    private final Sound sfxShipHit = Sound.load("shipHit.wav");
    private final Sound sfxLoad = Sound.load("load.wav");
    private final Sound sfxEmptying = Sound.load("emptying.wav");
    private int score;
    private int lives = STARTING_LIVES;
    private int currentWave;
    // From the second loop of eight waves onward, every attacker is worth 90 points.
    private boolean secondLoop;
    private int nextExtraShipScore = EXTRA_SHIP_SCORE;
    private int energy;
    private long frame;
    private boolean emptying;
    private boolean fuelling;
    private boolean dying;
    private boolean ended;
    private Thread gameThread;
    private volatile boolean running;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F11) {
                    if (SwingUtilities.getWindowAncestor(GamePanel.this)
                            instanceof GameWindow window) {
                        window.toggleFullscreen();
                    }
                    return;
                }
                if (ended) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        restart();
                    }
                    return;
                }
                if (dying) {
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
        currentWave = START_WAVE;
        secondLoop = false;
        nextExtraShipScore = EXTRA_SHIP_SCORE;
        energy = 0;
        emptying = false;
        dying = false;
        ended = false;
        sfxEmptying.stop();
        bullets.clear();
        player.resetPosition(PLAYER_START_X);
        enemyFormation = new EnemyFormation(currentWave, WIDTH);
        beginFuelling();
    }

    private int waveValue() {
        return secondLoop ? 90 : enemyFormation.getPoints();
    }

    public void start() {
        running = true;
        gameThread = new Thread(this, "game-loop");
        gameThread.start();
        requestFocusInWindow();
        beginFuelling();
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
        if (ended) {
            return;
        }
        frame++;

        while (score >= nextExtraShipScore) {
            if (lives < MAX_LIVES) {
                lives++;
            }
            nextExtraShipScore += EXTRA_SHIP_SCORE;
        }

        if (dying) {
            player.updateDeath();
            if (player.isDead()) {
                dying = false;
                if (lives <= 0) {
                    ended = true;
                    enemyFormation.clear();
                    bullets.clear();
                } else {
                    lives--;
                    enemyFormation.retreat();
                    player.resetPosition(PLAYER_START_X);
                    beginFuelling();
                }
            }
            return;
        }

        if (emptying) {
            if (energy > 0) {
                if (frame % BONUS_DRAIN_FRAMES == 0) {
                    energy--;
                    score += waveValue();
                }
            } else {
                emptying = false;
                sfxEmptying.stop();
                currentWave = (currentWave + 1) % EnemyFormation.WAVE_COUNT;
                if (currentWave == 0) {
                    secondLoop = true;
                }
                enemyFormation = new EnemyFormation(currentWave, WIDTH, secondLoop);
                beginFuelling();
            }
            return;
        }

        if (fuelling) {
            energy++;
            if (energy >= ENERGY_MAX) {
                energy = ENERGY_MAX;
                fuelling = false;
            }
            return;
        }

        player.update(WIDTH);
        enemyFormation.update(WIDTH, PLAYFIELD_HEIGHT);

        if (player.isShooting() && bullets.isEmpty()) {
            bullets.add(new Bullet(player.getMuzzleX(), player.getMuzzleY()));
            sfxShoot.play();
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
                    score += waveValue();
                    sfxEnemyHit.play();
                    break;
                }
            }
        }

        for (EnemyShot shot : enemyFormation.getShots()) {
            if (shot.getBounds().intersects(player.getBounds())) {
                enemyFormation.removeShot(shot);
                hitPlayer();
                break;
            }
        }

        for (Enemy enemy : enemyFormation.getEnemies()) {
            if (enemy.getBounds().intersects(player.getBounds())) {
                hitPlayer();
                break;
            }
        }

        if (enemyFormation.getEnemies().isEmpty()) {
            emptying = true;
            enemyFormation.clear();
            bullets.clear();
            sfxEmptying.loop();
            return;
        }

        if (frame % ENERGY_DRAIN_FRAMES == 0 && energy > 0) {
            energy--;
        }
        if (energy <= 0) {
            dying = true;
            player.die();
            bullets.clear();
        }
    }

    private void hitPlayer() {
        if (energy > 0) {
            sfxShipHit.play();
        }
        // Being hit wipes the energy out instantly, as in the original.
        energy = 0;
    }

    /** Starts a level with an empty bar that fills back up, as at the beginning of the game. */
    private void beginFuelling() {
        fuelling = true;
        energy = 0;
        sfxLoad.play();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // Fit the 320x240 playfield to the panel, letterboxing to keep the aspect ratio.
        double scale = Math.min(getWidth() / (double) WIDTH, getHeight() / (double) HEIGHT);
        g2.translate((getWidth() - WIDTH * scale) / 2, (getHeight() - HEIGHT * scale) / 2);
        g2.scale(scale, scale);
        // Sprites entering from off screen must not show up in the letterbox bars.
        g2.clipRect(0, 0, WIDTH, HEIGHT);

        player.render(g2);
        for (Bullet bullet : bullets) {
            bullet.render(g2);
        }
        enemyFormation.render(g2);

        renderHud(g2);

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
        // The original bar moves in chunks of four energy units, not continuously.
        int chunks = Math.min(ENERGY_BAR_CHUNKS, energy / ENERGY_UNITS_PER_CHUNK);
        int yellowWidth = chunks * (barWidth / ENERGY_BAR_CHUNKS);
        g2.setColor(ENERGY_YELLOW);
        g2.fillRect(barX, barY, yellowWidth, barHeight);

        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g2.setColor(SCORE_BLUE);
        int textY = PLAYFIELD_HEIGHT + HUD_HEIGHT - 8;
        g2.drawString(String.valueOf(score), 8, textY);

        // Reserve blasters drawn as a row of ship icons, right-aligned.
        int iconY = textY - LIVES_ICON_HEIGHT + 2;
        for (int i = 0; i < lives; i++) {
            int iconX = WIDTH - 8 - (i + 1) * (LIVES_ICON_WIDTH + 2);
            g2.drawImage(LIVES_ICON, iconX, iconY, LIVES_ICON_WIDTH, LIVES_ICON_HEIGHT, null);
        }
    }
}
