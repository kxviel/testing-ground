package seda_project.control_alt_defeat.gamebox.network.hexchess;

import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoard;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameStatus;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMove;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMoveRecord;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPiece;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class HexChessStateSnapshot {

    private HexChessStateSnapshot() {
    }

    public static String serialize(HexGameState state) {
        return String.join("~",
                serializeBoard(state.board()),
                state.turn().name(),
                state.status().name(),
                encode(state.statusMessage()),
                serializeLastMove(state.lastMove()),
                state.enPassantTarget() == null ? "-" : state.enPassantTarget().notation(),
                state.drawOfferBy() == null ? "-" : state.drawOfferBy().name(),
                String.valueOf(state.halfMoveClock()),
                String.valueOf(state.whiteScore()),
                String.valueOf(state.blackScore()));
    }

    public static HexGameState deserialize(String value) {
        if (value == null || value.isBlank()) {
            return HexGameState.standard();
        }

        String[] parts = value.split("~", -1);
        if (parts.length < 10) {
            return HexGameState.standard();
        }

        HexBoard board = deserializeBoard(parts[0]);
        HexPieceColor turn = parseColor(parts[1], HexPieceColor.WHITE);
        HexGameStatus status = parseStatus(parts[2]);
        String statusMessage = decode(parts[3]);
        HexMoveRecord lastMove = deserializeLastMove(parts[4]);
        HexCoordinate enPassantTarget = parseCoordinate(parts[5]);
        HexPieceColor drawOfferBy = "-".equals(parts[6]) ? null : parseColor(parts[6], null);
        int halfMoveClock = parseInt(parts[7]);
        double whiteScore = parseDouble(parts[8]);
        double blackScore = parseDouble(parts[9]);

        return new HexGameState(
                board,
                turn,
                status,
                statusMessage,
                lastMove,
                enPassantTarget,
                drawOfferBy,
                halfMoveClock,
                Map.of(),
                whiteScore,
                blackScore);
    }

    private static String serializeBoard(HexBoard board) {
        return board.pieces()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().notation()
                        + "," + entry.getValue().color().name()
                        + "," + entry.getValue().type().name())
                .collect(Collectors.joining(";"));
    }

    private static HexBoard deserializeBoard(String value) {
        if (value == null || value.isBlank()) {
            return HexBoard.empty();
        }

        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();
        Arrays.stream(value.split(";"))
                .map(piece -> piece.split(",", 3))
                .filter(parts -> parts.length == 3)
                .forEach(parts -> pieces.put(
                        HexCoordinate.of(parts[0]),
                        new HexPiece(parseColor(parts[1], HexPieceColor.WHITE), HexPieceType.valueOf(parts[2]))));

        return new HexBoard(pieces);
    }

    private static String serializeLastMove(HexMoveRecord record) {
        if (record == null) {
            return "-";
        }

        HexMove move = record.move();
        return String.join(",",
                move.from().notation(),
                move.to().notation(),
                move.promotion() == null ? "-" : move.promotion().name(),
                String.valueOf(move.enPassant()),
                record.movedPiece().color().name(),
                record.movedPiece().type().name(),
                record.capturedPiece() == null ? "-" : record.capturedPiece().color().name(),
                record.capturedPiece() == null ? "-" : record.capturedPiece().type().name(),
                record.capturedAt() == null ? "-" : record.capturedAt().notation());
    }

    private static HexMoveRecord deserializeLastMove(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }

        String[] parts = value.split(",", -1);
        if (parts.length < 9) {
            return null;
        }

        HexPieceType promotion = "-".equals(parts[2]) ? null : HexPieceType.valueOf(parts[2]);
        HexMove move = new HexMove(
                HexCoordinate.of(parts[0]),
                HexCoordinate.of(parts[1]),
                promotion,
                Boolean.parseBoolean(parts[3]));
        HexPiece movedPiece = new HexPiece(parseColor(parts[4], HexPieceColor.WHITE), HexPieceType.valueOf(parts[5]));
        HexPiece capturedPiece = "-".equals(parts[6])
                ? null
                : new HexPiece(parseColor(parts[6], HexPieceColor.BLACK), HexPieceType.valueOf(parts[7]));
        HexCoordinate capturedAt = parseCoordinate(parts[8]);

        return new HexMoveRecord(move, movedPiece, capturedPiece, capturedAt);
    }

    private static HexCoordinate parseCoordinate(String value) {
        return value == null || value.isBlank() || "-".equals(value) ? null : HexCoordinate.of(value);
    }

    private static HexPieceColor parseColor(String value, HexPieceColor fallback) {
        try {
            return value == null || value.isBlank() ? fallback : HexPieceColor.valueOf(value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static HexGameStatus parseStatus(String value) {
        try {
            return HexGameStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return HexGameStatus.RUNNING;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
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
