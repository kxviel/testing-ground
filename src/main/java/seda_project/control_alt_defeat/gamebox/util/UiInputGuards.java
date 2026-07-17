package seda_project.control_alt_defeat.gamebox.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

public final class UiInputGuards {

    private UiInputGuards() {
    }

    public static void limitPlayerNames(TextField... fields) {
        for (TextField field : fields) {
            limitText(field, SafeText.MAX_PLAYER_NAME_CHARS);
        }
    }

    public static void limitWholeNumber(TextField field, int maxDigits) {
        if (field == null) {
            return;
        }

        int safeMaxDigits = Math.max(0, maxDigits);
        field.setTextFormatter(new TextFormatter<>(change -> {
            String nextText = change.getControlNewText();
            if (nextText.length() > safeMaxDigits || !nextText.matches("\\d*")) {
                return null;
            }
            return change;
        }));
        truncateExistingText(field, safeMaxDigits);
    }

    public static void limitText(TextInputControl control, int maxChars) {
        if (control == null) {
            return;
        }

        int safeMaxChars = Math.max(0, maxChars);
        control.setTextFormatter(new TextFormatter<>(change -> {
            String nextText = change.getControlNewText();
            if (nextText.length() <= safeMaxChars) {
                return change;
            }

            int existingWithoutSelection = control.getLength()
                    - Math.max(0, change.getRangeEnd() - change.getRangeStart());
            int allowedInsertedChars = Math.max(0, safeMaxChars - existingWithoutSelection);
            if (allowedInsertedChars == 0) {
                return null;
            }

            String insertedText = change.getText();
            change.setText(insertedText.substring(0, Math.min(allowedInsertedChars, insertedText.length())));
            return change;
        }));
        truncateExistingText(control, safeMaxChars);
    }

    private static void truncateExistingText(TextInputControl control, int maxChars) {
        String current = control.getText();
        if (current != null && current.length() > maxChars) {
            control.setText(current.substring(0, maxChars));
        }
    }
}
