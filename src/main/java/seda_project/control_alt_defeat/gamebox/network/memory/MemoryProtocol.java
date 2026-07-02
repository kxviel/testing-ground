package seda_project.control_alt_defeat.gamebox.network.memory;

import seda_project.control_alt_defeat.gamebox.network.NetworkMessage;

import java.util.List;

public final class MemoryProtocol {

    public static final String JOIN = "JOIN";
    public static final String START = "START";
    public static final String FLIP = "FLIP";
    public static final String STATE = "STATE";
    public static final String RESTART_REQUEST = "RESTART_REQUEST";
    public static final String RESTART_STATE = "RESTART_STATE";
    public static final String QUIT = "QUIT";
    public static final String ERROR = "ERROR";

    private MemoryProtocol() {
    }

    public static String join(String playerName) {
        return make(JOIN, playerName);
    }

    public static String start(String hostName, String joinerName, String snapshot) {
        return make(START, hostName, joinerName, snapshot);
    }

    public static String flip(int cardPosition) {
        return make(FLIP, String.valueOf(cardPosition));
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

    public static String quit(String playerName) {
        return make(QUIT, playerName);
    }

    public static String error(String message) {
        return make(ERROR, message);
    }

    public static String type(String message) {
        return NetworkMessage.type(message);
    }

    public static boolean isType(String message, String type) {
        return NetworkMessage.isType(message, type);
    }

    public static List<String> fields(String message) {
        return NetworkMessage.fields(message);
    }

    private static String make(String type, String... fields) {
        return NetworkMessage.make(type, fields);
    }
}
