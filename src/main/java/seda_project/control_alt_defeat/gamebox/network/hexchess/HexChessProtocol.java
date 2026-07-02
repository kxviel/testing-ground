package seda_project.control_alt_defeat.gamebox.network.hexchess;

import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMove;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;
import seda_project.control_alt_defeat.gamebox.network.NetworkMessage;

import java.util.List;

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
        return NetworkMessage.isType(message, type);
    }

    public static String type(String message) {
        return NetworkMessage.type(message);
    }

    public static List<String> fields(String message) {
        return NetworkMessage.fields(message);
    }

    public static HexMove parseMove(List<String> fields) {
        if (fields == null || fields.size() != 4) {
            return null;
        }

        try {
            HexPieceType promotion = parsePromotion(fields);
            boolean enPassant = parseBoolean(fields.get(3));

            return new HexMove(
                    HexCoordinate.of(fields.getFirst()),
                    HexCoordinate.of(fields.get(1)),
                    promotion,
                    enPassant);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static HexPieceType parsePromotion(List<String> fields) {
        if (fields.get(2).isBlank()) {
            return null;
        }

        HexPieceType promotion = HexPieceType.valueOf(fields.get(2));
        return switch (promotion) {
            case QUEEN, ROOK, BISHOP, KNIGHT -> promotion;
            default -> throw new IllegalArgumentException("Invalid promotion piece: " + promotion);
        };
    }

    private static boolean parseBoolean(String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value: " + value);
    }

    private static String make(String type, String... fields) {
        return NetworkMessage.make(type, fields);
    }
}
