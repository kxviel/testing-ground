package seda_project.control_alt_defeat.gamebox.util;

public final class SafeText {

    public static final int MAX_PLAYER_NAME_CHARS = 32;
    public static final String PLAYER_ONE_NAME = "The Old Monk";
    public static final String PLAYER_TWO_NAME = "The Outer God";

    private SafeText() {
    }

    public static String playerName(String value, String fallback) {
        return singleLine(value, fallback, MAX_PLAYER_NAME_CHARS);
    }

    public static String singleLine(String value, String fallback, int maxChars) {
        String cleaned = normalizeSingleLine(value);
        if (cleaned.isBlank()) {
            cleaned = normalizeSingleLine(fallback);
        }
        if (cleaned.length() <= maxChars) {
            return cleaned;
        }
        return cleaned.substring(0, maxChars).stripTrailing();
    }

    private static String normalizeSingleLine(String value) {
        if (value == null) {
            return "";
        }

        return value.strip().replaceAll("\\p{Cntrl}+", " ").replaceAll("\\s+", " ");
    }
}
