package seda_project.control_alt_defeat.gamebox.network;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class NetworkMessage {

    private NetworkMessage() {
    }

    public static String make(String type, String... fields) {
        String safeType = type == null ? "" : type.trim();
        if (fields == null || fields.length == 0) {
            return safeType;
        }

        return safeType + ":" + Stream.of(fields)
                .map(SnapshotCodec::encode)
                .collect(Collectors.joining(":"));
    }

    public static boolean isType(String message, String type) {
        return type(message).equals(type);
    }

    public static String type(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        int splitIndex = message.indexOf(':');
        return splitIndex < 0 ? message : message.substring(0, splitIndex);
    }

    public static List<String> fields(String message) {
        if (message == null || !message.contains(":")) {
            return List.of();
        }

        try {
            return Arrays.stream(message.split(":", -1))
                    .skip(1)
                    .map(SnapshotCodec::decode)
                    .toList();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}
