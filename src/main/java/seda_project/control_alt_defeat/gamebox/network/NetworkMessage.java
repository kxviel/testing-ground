package seda_project.control_alt_defeat.gamebox.network;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class NetworkMessage {

    public static final int MAX_MESSAGE_CHARS = 128 * 1024;
    public static final int MAX_TYPE_CHARS = 32;
    public static final int MAX_FIELDS = 16;

    private NetworkMessage() {
    }

    public static String make(String type, String... fields) {
        String safeType = type == null ? "" : type.trim();
        if (!isValidType(safeType)) {
            return "";
        }
        if (fields != null && fields.length > MAX_FIELDS) {
            return "";
        }
        if (fields == null || fields.length == 0) {
            return safeType;
        }

        try {
            String message = safeType + ":" + Stream.of(fields)
                    .map(SnapshotCodec::encode)
                    .collect(Collectors.joining(":"));
            return message.length() <= MAX_MESSAGE_CHARS ? message : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public static boolean isType(String message, String type) {
        return type(message).equals(type);
    }

    public static String type(String message) {
        if (message == null || message.isBlank() || message.length() > MAX_MESSAGE_CHARS) {
            return "";
        }

        int splitIndex = message.indexOf(':');
        String candidate = splitIndex < 0 ? message : message.substring(0, splitIndex);
        return isValidType(candidate) ? candidate : "";
    }

    public static List<String> fields(String message) {
        if (type(message).isEmpty() || !message.contains(":")) {
            return List.of();
        }

        try {
            String[] parts = message.split(":", MAX_FIELDS + 2);
            if (parts.length > MAX_FIELDS + 1) {
                return List.of();
            }
            List<String> decoded = new ArrayList<>(parts.length - 1);
            for (int i = 1; i < parts.length; i++) {
                decoded.add(SnapshotCodec.decode(parts[i]));
            }
            return List.copyOf(decoded);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private static boolean isValidType(String type) {
        if (type == null || type.isEmpty() || type.length() > MAX_TYPE_CHARS) {
            return false;
        }
        for (int i = 0; i < type.length(); i++) {
            char character = type.charAt(i);
            if ((character < 'A' || character > 'Z')
                    && (character < '0' || character > '9')
                    && character != '_') {
                return false;
            }
        }
        return true;
    }
}
