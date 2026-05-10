package seda_project.control_alt_defeat.gamebox.network.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TetrisProtocol {

    public static final String JOIN = "JOIN";
    public static final String START = "START";
    public static final String INPUT = "INPUT";
    public static final String STATE = "STATE";
    public static final String RESTART_REQUEST = "RESTART_REQUEST";
    public static final String RESTART_STATE = "RESTART_STATE";
    public static final String QUIT = "QUIT";
    public static final String ERROR = "ERROR";

    public static final String MOVE_LEFT = "MOVE_LEFT";
    public static final String MOVE_RIGHT = "MOVE_RIGHT";
    public static final String SOFT_DROP = "SOFT_DROP";
    public static final String ROTATE = "ROTATE";

    private TetrisProtocol() {
    }

    public static String join(String playerName) {
        return make(JOIN, playerName);
    }

    public static String start(String hostName, String joinerName, TetrisGameConfig config) {
        return make(START, hostName, joinerName, config == null ? "" : config.serialize());
    }

    public static String input(PlayerSide side, String command) {
        return make(INPUT, side == null ? "" : side.name(), command);
    }

    public static String state(String snapshot) {
        return make(STATE, snapshot);
    }

    public static String restartRequest(String playerName) {
        return make(RESTART_REQUEST, playerName);
    }

    public static String restartState(String snapshot) {
        return make(RESTART_STATE, snapshot);
    }

    public static String error(String message) {
        return make(ERROR, message);
    }

    public static String quit(String playerName) {
        return make(QUIT, playerName);
    }

    public static String make(String type, String... fields) {
        String safeType = type == null ? "" : type.trim();

        if (fields == null || fields.length == 0) {
            return safeType;
        }

        return safeType + ":" + Stream.of(fields)
                .map(TetrisProtocol::encode)
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

        String[] parts = message.split(":", -1);
        List<String> fields = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            try {
                fields.add(decode(parts[i]));
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }

        return List.copyOf(fields);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
