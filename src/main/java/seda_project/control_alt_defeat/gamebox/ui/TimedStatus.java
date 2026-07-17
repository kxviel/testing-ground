package seda_project.control_alt_defeat.gamebox.ui;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.util.Duration;
import seda_project.control_alt_defeat.gamebox.util.SafeText;

public final class TimedStatus {

    private final Label label;
    private PauseTransition timer;

    public TimedStatus(Label label) {
        this.label = label;
    }

    public void show(String message, int seconds) {
        String safeMessage = SafeText.singleLine(message, "", 512);
        label.setText(safeMessage);
        if (timer != null) {
            timer.stop();
        }

        timer = new PauseTransition(Duration.seconds(Math.min(3_600, Math.max(0, seconds))));
        timer.setOnFinished(event -> {
            if (safeMessage.equals(label.getText())) {
                label.setText("");
            }
            timer = null;
        });
        timer.play();
    }
}
