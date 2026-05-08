package seda_project.control_alt_defeat.gamebox.network;

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

    /**
     * Builds a protocol line from a message type and optional fields.
     *
     * @param type  protocol message type
     * @param parts message fields
     * @return colon-separated protocol line
     */
    public static String make(String type, String... parts) {
        if (parts.length == 0)
            return type;
        return type + ":" + String.join(":", parts);
    }
}