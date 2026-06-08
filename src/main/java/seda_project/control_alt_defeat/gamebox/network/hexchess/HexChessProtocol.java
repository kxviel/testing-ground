package seda_project.control_alt_defeat.gamebox.network.hexchess;

import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMove;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class HexChessProtocol {

    public static final String JOIN = "JOIN";
    public static final String START = "START";
    public static final String MOVE = "MOVE";
    public static final String STATE = "STATE";
    public static final String DRAW_OFFER = "DRAW_OFFER";
    public static final String DRAW_ACCEPT = "DRAW_ACCEPT";
    public static final String DRAW_DECLINE = "DRAW_DECLINE";
    public static final String RESIGN = "RESIGN";
    public static final String RESTART = "RESTART";
    public static final String QUIT = "QUIT";
    public static final String ERROR = "ERROR";

    private HexChessProtocol() {
    }

    public static String join(String playerName) {
        return make(JOIN, playerName);
    }

    public static String start(HexChessGameSetup setup, String snapshot) {
        return make(START, setup.whiteName(), setup.blackName(), snapshot);
    }

    public static String move(HexMove move) {
        return make(MOVE,
                move.from().notation(),
                move.to().notation(),
                move.promotion() == null ? "" : move.promotion().name(),
                String.valueOf(move.enPassant()));
    }

    public static String state(String snapshot) {
        return make(STATE, snapshot);
    }

    public static String simple(String type) {
        return make(type);
    }

    public static String error(String message) {
        return make(ERROR, message);
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
                    .map(HexChessProtocol::decode)
                    .toList();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    public static HexMove parseMove(List<String> fields) {
        if (fields.size() < 2) {
            return null;
        }

        HexPieceType promotion = fields.size() >= 3 && !fields.get(2).isBlank()
                ? HexPieceType.valueOf(fields.get(2))
                : null;
        boolean enPassant = fields.size() >= 4 && Boolean.parseBoolean(fields.get(3));

        return new HexMove(
                seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate.of(fields.get(0)),
                seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate.of(fields.get(1)),
                promotion,
                enPassant);
    }

    private static String make(String type, String... fields) {
        String safeType = type == null ? "" : type.trim();

        if (fields == null || fields.length == 0) {
            return safeType;
        }

        return safeType + ":" + Stream.of(fields)
                .map(HexChessProtocol::encode)
                .collect(Collectors.joining(":"));
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
