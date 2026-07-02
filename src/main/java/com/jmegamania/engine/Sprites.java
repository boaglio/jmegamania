package com.jmegamania.engine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Sprites {

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private Sprites() {
    }

    public static BufferedImage load(String name) {
        return CACHE.computeIfAbsent(name, Sprites::read);
    }

    private static BufferedImage read(String name) {
        String path = "/sprites/" + name;
        try (var in = Sprites.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Sprite not found on classpath: " + path);
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load sprite: " + path, e);
        }
    }
}
