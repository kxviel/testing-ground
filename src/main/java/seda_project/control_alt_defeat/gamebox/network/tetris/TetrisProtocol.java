package seda_project.control_alt_defeat.gamebox.network.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.network.NetworkMessage;

import java.util.List;

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
        return NetworkMessage.make(type, fields);
    }

    public static boolean isType(String message, String type) {
        return NetworkMessage.isType(message, type);
    }

    public static String type(String message) {
        return NetworkMessage.type(message);
    }

    public static List<String> fields(String message) {
        return NetworkMessage.fields(message);
    }
}
