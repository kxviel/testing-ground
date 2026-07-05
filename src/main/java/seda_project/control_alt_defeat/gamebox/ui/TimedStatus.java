package seda_project.control_alt_defeat.gamebox.ui;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.util.Duration;

public final class TimedStatus {

    private final Label label;
    private PauseTransition timer;

    public TimedStatus(Label label) {
        this.label = label;
    }

    public void show(String message, int seconds) {
        label.setText(message);
        if (timer != null) {
            timer.stop();
        }

        timer = new PauseTransition(Duration.seconds(seconds));
        timer.setOnFinished(event -> {
            if (message.equals(label.getText())) {
                label.setText("");
            }
            timer = null;
        });
        timer.play();
    }
}
