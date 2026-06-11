package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.List;

public record HexPositionValidation(boolean isValid, String message) {

    public static HexPositionValidation valid() {
        return new HexPositionValidation(true, "Position is valid.");
    }

    public static HexPositionValidation invalid(String message) {
        return new HexPositionValidation(false, message);
    }

    public static HexPositionValidation invalid(List<String> messages) {
        return invalid(String.join(System.lineSeparator(), messages));
    }
}
