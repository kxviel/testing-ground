package seda_project.control_alt_defeat.gamebox.util;

import javafx.event.ActionEvent;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.AudioClip;

public final class SoundManager {

    private static final String CLICK_SOUND_PATH = "/sounds/click.wav";
    private static final String BUTTON_CLICK_HANDLER_KEY = SoundManager.class.getName() + ".buttonClickHandler";
    private static final String LAST_POINTER_SOUND_KEY = SoundManager.class.getName() + ".lastPointerSound";
    private static final long POINTER_ACTION_SUPPRESSION_NANOS = 300_000_000L;
    private static final double CLICK_VOLUME = 0.35;
    private static final AudioClip CLICK_SOUND = loadClickSound();

    private SoundManager() {
    }

    public static void playClick() {
        CLICK_SOUND.play();
    }

    public static void installButtonClickSound(Parent root) {
        if (root == null || Boolean.TRUE.equals(root.getProperties().get(BUTTON_CLICK_HANDLER_KEY))) {
            return;
        }

        root.getProperties().put(BUTTON_CLICK_HANDLER_KEY, true);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            ButtonBase button = buttonFrom(event.getTarget());
            if (button != null && !button.isDisabled()) {
                button.getProperties().put(LAST_POINTER_SOUND_KEY, System.nanoTime());
                playClick();
            }
        });
        root.addEventHandler(ActionEvent.ACTION, event -> {
            ButtonBase button = buttonFrom(event.getTarget());
            if (button != null && !recentPointerSound(button)) {
                playClick();
            }
        });
    }

    private static ButtonBase buttonFrom(EventTarget target) {
        if (target instanceof ButtonBase button) {
            return button;
        }
        if (!(target instanceof Node node)) {
            return null;
        }

        Node current = node;
        while (current != null) {
            if (current instanceof ButtonBase button) {
                return button;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean recentPointerSound(ButtonBase button) {
        Object value = button.getProperties().get(LAST_POINTER_SOUND_KEY);
        return value instanceof Long lastPlayed
                && System.nanoTime() - lastPlayed < POINTER_ACTION_SUPPRESSION_NANOS;
    }

    private static AudioClip loadClickSound() {
        var soundUrl = SoundManager.class.getResource(CLICK_SOUND_PATH);
        if (soundUrl == null) {
            throw new IllegalStateException("Missing required sound resource: " + CLICK_SOUND_PATH);
        }

        AudioClip sound = new AudioClip(soundUrl.toExternalForm());
        sound.setVolume(CLICK_VOLUME);
        prime(sound);
        return sound;
    }

    private static void prime(AudioClip sound) {
        try {
            sound.play(0.0);
            sound.stop();
        } catch (RuntimeException ignored) {
            // Some systems initialize audio lazily; real clicks still use the loaded clip.
        }
    }
}
