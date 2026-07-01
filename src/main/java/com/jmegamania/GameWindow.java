package com.jmegamania;

import javax.swing.JFrame;

public class GameWindow extends JFrame {

    public GameWindow() {
        super("JMegamania");

        GamePanel gamePanel = new GamePanel();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        add(gamePanel);
        pack();
        setLocationRelativeTo(null);

        gamePanel.start();
    }
}
