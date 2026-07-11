package com.jmegamania.engine;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.Mixer;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight sound effect loaded from a WAV on the classpath.
 * <p>
 * One-shot effects are fired with {@link #play()} and are allowed to overlap.
 * A single looping effect can be driven with {@link #loop()} / {@link #stop()}.
 * If no audio device is available the effect silently becomes a no-op so the
 * game keeps running.
 */
public final class Sound {

    private static final Map<String, Sound> CACHE = new ConcurrentHashMap<>();
    /**
     * On Linux, {@link AudioSystem#getClip()} may pick a raw hardware device
     * (e.g. "plughw:0,0") that bypasses the system sound server, so audio ends up
     * on an output nobody is listening to. Prefer the ALSA "default" mixer, which
     * routes through PulseAudio/PipeWire; on Windows/macOS no mixer matches and
     * the standard default clip is used.
     */
    private static final Mixer.Info PREFERRED_MIXER = findDefaultMixer();

    private final byte[] data;
    private Clip loopClip;

    private Sound(byte[] data) {
        this.data = data;
    }

    public static Sound load(String name) {
        return CACHE.computeIfAbsent(name, Sound::read);
    }

    private static Sound read(String name) {
        String path = "/sfx/" + name;
        try (InputStream in = Sound.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Sound not found on classpath: " + path);
            }
            return new Sound(in.readAllBytes());
        } catch (Exception e) {
            // Missing or unreadable audio should not take down the game.
            return new Sound(new byte[0]);
        }
    }

    /** Plays the effect once. Multiple plays may overlap. */
    public void play() {
        Clip clip = openClip();
        if (clip == null) {
            return;
        }
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                clip.close();
            }
        });
        clip.start();
    }

    /** Starts looping the effect, replacing any loop already running. */
    public void loop() {
        stop();
        loopClip = openClip();
        if (loopClip != null) {
            loopClip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    /** Stops the currently looping effect, if any. */
    public void stop() {
        if (loopClip != null) {
            loopClip.stop();
            loopClip.close();
            loopClip = null;
        }
    }

    private Clip openClip() {
        if (data.length == 0) {
            return null;
        }
        try (AudioInputStream stream =
                     AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
            Clip clip = PREFERRED_MIXER != null
                    ? AudioSystem.getClip(PREFERRED_MIXER)
                    : AudioSystem.getClip();
            clip.open(stream);
            return clip;
        } catch (Exception e) {
            return null;
        }
    }

    private static Mixer.Info findDefaultMixer() {
        try {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (info.getName().toLowerCase().contains("default")
                        && AudioSystem.getMixer(info).isLineSupported(new Line.Info(Clip.class))) {
                    return info;
                }
            }
        } catch (Exception e) {
            // No usable mixer info; fall through to the platform default.
        }
        return null;
    }
}
