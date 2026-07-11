package com.jmegamania;

import javax.swing.JFrame;
import java.awt.GraphicsDevice;

public class GameWindow extends JFrame {

    private final GamePanel gamePanel;
    private boolean fullscreen;

    public GameWindow() {
        super("JMegamania");

        gamePanel = new GamePanel();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        add(gamePanel);
        pack();
        setLocationRelativeTo(null);

        gamePanel.start();
    }

    /** Switches between the windowed view and exclusive fullscreen (F11). */
    public void toggleFullscreen() {
        GraphicsDevice device = getGraphicsConfiguration().getDevice();
        // Changing decorations requires disposing the native window first.
        dispose();
        fullscreen = !fullscreen;
        if (fullscreen) {
            setUndecorated(true);
            device.setFullScreenWindow(this);
        } else {
            device.setFullScreenWindow(null);
            setUndecorated(false);
            pack();
            setLocationRelativeTo(null);
        }
        setVisible(true);
        gamePanel.requestFocusInWindow();
    }
}
