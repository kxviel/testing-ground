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
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPositionValidation;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPositionValidator;
import seda_project.control_alt_defeat.gamebox.util.SafeText;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.decode;
import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.encode;
import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.parseBoolean;
import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.parseInt;

public final class HexChessStateSnapshot {

    private static final int LEGACY_SNAPSHOT_FIELD_COUNT = 12;
    private static final int SNAPSHOT_FIELD_COUNT = 14;
    private static final int LAST_MOVE_FIELD_COUNT = 9;

    private HexChessStateSnapshot() {
    }

    public static String serialize(HexGameState state) {
        return serialize(state, SafeText.PLAYER_ONE_NAME, SafeText.PLAYER_TWO_NAME);
    }

    public static String serialize(HexGameState state, String whiteName, String blackName) {
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
                String.valueOf(state.blackScore()),
                serializeCoordinateSet(state.doubleMoveEligibleSquares()),
                serializeRepetitionCounts(state.repetitionCounts()),
                encode(SafeText.playerName(whiteName, SafeText.PLAYER_ONE_NAME)),
                encode(SafeText.playerName(blackName, SafeText.PLAYER_TWO_NAME)));
    }

    public static HexGameState deserialize(String value) {
        return deserializeMatch(value).gameState();
    }

    public static MatchSnapshot deserializeMatch(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Snapshot is empty.");
        }

        String[] parts = value.split("~", SNAPSHOT_FIELD_COUNT + 1);
        if (parts.length != LEGACY_SNAPSHOT_FIELD_COUNT && parts.length != SNAPSHOT_FIELD_COUNT) {
            throw new IllegalArgumentException("Snapshot has wrong field count.");
        }

        HexBoard board = deserializeBoard(parts[0]);
        HexPositionValidation materialValidation = HexPositionValidator.validateMaterial(board);
        if (!materialValidation.isValid()) {
            throw new IllegalArgumentException("Snapshot contains impossible material: "
                    + materialValidation.message());
        }
        HexPieceColor turn = parseColor(parts[1]);
        HexGameStatus status = parseStatus(parts[2]);
        String statusMessage = decode(parts[3]);
        if (statusMessage.length() > HexGameState.MAX_STATUS_MESSAGE_CHARS) {
            throw new IllegalArgumentException("Status message exceeds the supported length.");
        }
        HexMoveRecord lastMove = deserializeLastMove(parts[4]);
        HexCoordinate enPassantTarget = parseCoordinate(parts[5]);
        HexPieceColor drawOfferBy = "-".equals(parts[6]) ? null : parseColor(parts[6]);
        int halfMoveClock = parseInt(parts[7]);
        double whiteScore = parseDouble(parts[8]);
        double blackScore = parseDouble(parts[9]);
        Set<HexCoordinate> doubleMoveEligibleSquares = deserializeCoordinateSet(parts[10]);
        Map<String, Integer> repetitionCounts = deserializeRepetitionCounts(parts[11]);
        String whiteName = parts.length == SNAPSHOT_FIELD_COUNT
                ? deserializePlayerName(parts[12], SafeText.PLAYER_ONE_NAME)
                : SafeText.PLAYER_ONE_NAME;
        String blackName = parts.length == SNAPSHOT_FIELD_COUNT
                ? deserializePlayerName(parts[13], SafeText.PLAYER_TWO_NAME)
                : SafeText.PLAYER_TWO_NAME;

        if (halfMoveClock < 0 || halfMoveClock > HexGameState.MAX_HALF_MOVE_CLOCK) {
            throw new IllegalArgumentException("Half-move clock is outside the supported range.");
        }
        if (whiteScore < 0 || whiteScore > 1 || blackScore < 0 || blackScore > 1) {
            throw new IllegalArgumentException("Score is outside the supported range.");
        }

        return new MatchSnapshot(
                new HexGameState(
                        board,
                        turn,
                        status,
                        statusMessage,
                        lastMove,
                        enPassantTarget,
                        drawOfferBy,
                        halfMoveClock,
                        repetitionCounts,
                        doubleMoveEligibleSquares,
                        whiteScore,
                        blackScore),
                whiteName,
                blackName);
    }

    private static String deserializePlayerName(String value, String fallback) {
        String decoded = decode(value);
        if (decoded.length() > SafeText.MAX_PLAYER_NAME_CHARS) {
            throw new IllegalArgumentException("Player name exceeds the supported length.");
        }
        return SafeText.playerName(decoded, fallback);
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
        String[] entries = value.split(";", HexBoard.MAX_PIECES + 1);
        if (entries.length > HexBoard.MAX_PIECES) {
            throw new IllegalArgumentException("Snapshot contains too many pieces.");
        }
        for (String part : entries) {
            Map.Entry<HexCoordinate, HexPiece> entry = deserializePiece(part);
            if (pieces.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("Duplicate coordinate in snapshot: " + entry.getKey());
            }
        }

        return new HexBoard(pieces);
    }

    private static Map.Entry<HexCoordinate, HexPiece> deserializePiece(String value) {
        String[] parts = value.split(",", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid piece entry: " + value);
        }

        HexCoordinate coordinate = HexCoordinate.of(parts[0]);
        HexPieceColor color = parseColor(parts[1]);
        HexPieceType type = HexPieceType.valueOf(parts[2]);

        return Map.entry(coordinate, new HexPiece(color, type));
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

        String[] parts = value.split(",", LAST_MOVE_FIELD_COUNT + 1);
        if (parts.length != LAST_MOVE_FIELD_COUNT) {
            throw new IllegalArgumentException("Invalid last move entry: " + value);
        }

        HexPieceType promotion = "-".equals(parts[2]) ? null : HexPieceType.valueOf(parts[2]);
        HexMove move = new HexMove(
                HexCoordinate.of(parts[0]),
                HexCoordinate.of(parts[1]),
                promotion,
                parseBoolean(parts[3]));
        HexPiece movedPiece = new HexPiece(
                parseColor(parts[4]),
                HexPieceType.valueOf(parts[5]));
        HexPiece capturedPiece = "-".equals(parts[6])
                ? null
                : new HexPiece(parseColor(parts[6]), HexPieceType.valueOf(parts[7]));
        HexCoordinate capturedAt = parseCoordinate(parts[8]);

        return new HexMoveRecord(move, movedPiece, capturedPiece, capturedAt);
    }

    private static HexCoordinate parseCoordinate(String value) {
        return value == null || value.isBlank() || "-".equals(value) ? null : HexCoordinate.of(value);
    }

    private static HexPieceColor parseColor(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing piece color.");
        }

        return HexPieceColor.valueOf(value);
    }

    private static HexGameStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing game status.");
        }

        return HexGameStatus.valueOf(value);
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing score value.");
        }

        try {
            double score = Double.parseDouble(value);
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("Invalid score value: " + value);
            }
            return score;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid score value: " + value, e);
        }
    }

    private static String serializeCoordinateSet(Set<HexCoordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return "-";
        }

        return coordinates.stream()
                .sorted()
                .map(HexCoordinate::notation)
                .collect(Collectors.joining(","));
    }

    private static Set<HexCoordinate> deserializeCoordinateSet(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return Set.of();
        }

        String[] entries = value.split(",", HexBoard.MAX_PIECES + 1);
        if (entries.length > HexBoard.MAX_PIECES) {
            throw new IllegalArgumentException("Snapshot contains too many double-move squares.");
        }
        return Arrays.stream(entries)
                .map(HexCoordinate::of)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String serializeRepetitionCounts(Map<String, Integer> repetitionCounts) {
        if (repetitionCounts == null || repetitionCounts.isEmpty()) {
            return "-";
        }

        return repetitionCounts.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private static Map<String, Integer> deserializeRepetitionCounts(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return Map.of();
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        String[] entries = value.split(";", HexGameState.MAX_REPETITION_ENTRIES + 1);
        if (entries.length > HexGameState.MAX_REPETITION_ENTRIES) {
            throw new IllegalArgumentException("Snapshot contains too many repetition entries.");
        }
        for (String entry : entries) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid repetition entry: " + entry);
            }
            String key = decode(parts[0]);
            int count = parseInt(parts[1]);
            if (key.isBlank() || key.length() > HexGameState.MAX_POSITION_KEY_CHARS
                    || count < 1 || count > HexGameState.MAX_REPETITION_COUNT
                    || counts.putIfAbsent(key, count) != null) {
                throw new IllegalArgumentException("Invalid repetition count for key: " + key);
            }
        }
        return Map.copyOf(counts);
    }

    public record MatchSnapshot(HexGameState gameState, String whiteName, String blackName) {
    }

}
