package seda_project.control_alt_defeat.gamebox.network;

import java.util.List;

// Defines the small line-based protocol used by network multiplayer.
public final class Protocol {
    private Protocol() {
    }

    public static final String INIT = "INIT";
    public static final String FLIP = "FLIP";
    public static final String CLOSE = "CLOSE";
    public static final String GAMEOVER = "GAMEOVER";
    public static final String RESTART = "RESTART";
    public static final String QUIT = "QUIT";

    public static String make(String type, String... parts) {
        return NetworkMessage.make(type, parts);
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
}
