package seda_project.control_alt_defeat.gamebox.network;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class SnapshotCodec {

    private SnapshotCodec() {
    }

    public static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing encoded value.");
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing integer value.");
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value: " + value, e);
        }
    }

    public static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean parseBoolean(String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value: " + value);
    }
}
