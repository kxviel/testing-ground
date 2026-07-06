package seda_project.control_alt_defeat.gamebox.network.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoardObject;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisEffectState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoard;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPiece;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPlayerState;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.decode;
import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.encode;
import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.parseInt;

public final class TetrisStateSnapshot {

    private static final int MAX_SNAPSHOT_ROWS = 64;
    private static final int MAX_SNAPSHOT_COLUMNS = 64;

    private TetrisStateSnapshot() {
    }

    public static String serialize(TetrisGameState state) {
        return String.join("|",
                encode(state.config().serialize()),
                state.status().name(),
                encode(serializePlayer(state.bottomPlayer())),
                encode(serializePlayer(state.topPlayer())));
    }

    public static TetrisGameState deserialize(String value, TetrisGameConfig fallbackConfig) {
        if (value == null || value.isBlank()) {
            return fallbackState(fallbackConfig);
        }

        try {
            String[] parts = value.split("\\|", 4);
            if (parts.length != 4) {
                return fallbackState(fallbackConfig);
            }

            TetrisGameConfig config = TetrisGameConfig.deserialize(decode(parts[0]));
            if (!config.hasPieces()) {
                config = fallbackConfig == null ? TetrisGameConfig.defaultConfig() : fallbackConfig;
            }

            TetrisPlayerState bottom = deserializePlayer(decode(parts[2]), PlayerSide.BOTTOM);
            TetrisPlayerState top = deserializePlayer(decode(parts[3]), PlayerSide.TOP);
            TetrisGameStatus status = parseStatus(parts[1]);

            return new TetrisGameState(bottom, top, config, status);
        } catch (RuntimeException e) {
            return fallbackState(fallbackConfig);
        }
    }

    private static String serializePlayer(TetrisPlayerState player) {
        return String.join(";",
                encode(player.playerName()),
                String.valueOf(player.score()),
                player.status().name(),
                player.finalScore() == null ? "-" : String.valueOf(player.finalScore()),
                serializeBoard(player.board()),
                serializeBoardColors(player.board()),
                encode(serializePiece(player.activePiece())),
                serializeObject(player.boardObject()),
                serializeEffects(player.effects()),
                serializeQueue(player.queuedShapes()));
    }

    private static TetrisPlayerState deserializePlayer(String value, PlayerSide side) {
        String[] parts = value.split(";", -1);
        if (parts.length < 10) {
            return TetrisPlayerState.create("Player", side);
        }

        String name = decode(parts[0]);
        int score = parseInt(parts[1], 0);
        PlayerStatus status = parsePlayerStatus(parts[2]);
        Integer finalScore = "-".equals(parts[3]) ? null : parseInt(parts[3], score);
        TetrisBoard board = deserializeBoard(parts[4], parts[5]);
        TetrisPiece activePiece = deserializePiece(decode(parts[6]));
        TetrisBoardObject boardObject = deserializeObject(parts[7]);
        TetrisEffectState effects = deserializeEffects(parts[8]);
        List<PieceShape> queuedShapes = deserializeQueue(parts[9]);

        return new TetrisPlayerState(
                name,
                side,
                board,
                activePiece,
                score,
                status,
                finalScore,
                boardObject,
                effects,
                queuedShapes);
    }

    private static String serializeBoard(TetrisBoard board) {
        StringBuilder builder = new StringBuilder();
        builder.append(board.rows()).append('x').append(board.columns()).append(':');
        for (int row = 0; row < board.rows(); row++) {
            for (int column = 0; column < board.columns(); column++) {
                TetrisCell cell = board.cellAt(new BoardPosition(row, column));
                builder.append(cell == TetrisCell.FILLED ? '1' : '0');
            }
        }
        return builder.toString();
    }

    private static TetrisBoard deserializeBoard(String value, String colorsValue) {
        int rows = TetrisBoard.DEFAULT_ROWS;
        int columns = TetrisBoard.DEFAULT_COLUMNS;
        String cellData = value;

        if (value != null && value.contains(":")) {
            int colonIdx = value.indexOf(':');
            String dimensionPrefix = value.substring(0, colonIdx);
            cellData = value.substring(colonIdx + 1);

            if (dimensionPrefix.contains("x")) {
                String[] dimensions = dimensionPrefix.split("x", 2);
                rows = parseInt(dimensions[0], TetrisBoard.DEFAULT_ROWS);
                columns = dimensions.length > 1
                        ? parseInt(dimensions[1], TetrisBoard.DEFAULT_COLUMNS)
                        : TetrisBoard.DEFAULT_COLUMNS;
            } else {
                rows = parseInt(dimensionPrefix, TetrisBoard.DEFAULT_ROWS);
            }
        }

        rows = clamp(rows, TetrisBoard.MIN_ROWS, MAX_SNAPSHOT_ROWS);
        columns = clamp(columns, TetrisBoard.MIN_COLUMNS, MAX_SNAPSHOT_COLUMNS);

        TetrisCell[][] cells = new TetrisCell[rows][columns];
        int[][] colors = new int[rows][columns];

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                boolean filled = cellData != null && index < cellData.length() && cellData.charAt(index) == '1';
                cells[row][column] = filled ? TetrisCell.FILLED : TetrisCell.EMPTY;
                colors[row][column] = filled ? parseColor(colorsValue, index) : -1;
            }
        }

        return new TetrisBoard(cells, colors);
    }

    private static String serializeBoardColors(TetrisBoard board) {
        StringBuilder builder = new StringBuilder(board.rows() * board.columns());

        for (int row = 0; row < board.rows(); row++) {
            for (int column = 0; column < board.columns(); column++) {
                int color = board.colorAt(new BoardPosition(row, column));
                builder.append(color < 0 ? '.' : Character.forDigit(Math.min(color, 35), 36));
            }
        }

        return builder.toString();
    }

    private static String serializePiece(TetrisPiece piece) {
        if (piece == null) {
            return "-";
        }

        return serializeShape(piece.shape()) + ","
                + piece.position().row() + ","
                + piece.position().column() + ","
                + piece.rotation().name() + ","
                + piece.colorIndex();
    }

    private static String serializeShape(PieceShape shape) {
        return String.join(",",
                shape.type().name(),
                encode(shape.name()),
                serializeCells(shape.cells()));
    }

    private static TetrisPiece deserializePiece(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }

        String[] parts = value.split(",", 7);
        if (parts.length < 6) {
            return null;
        }

        PieceType type = parsePieceType(parts[0]);
        String name = decode(parts[1]);
        List<BoardPosition> cells = deserializeCells(parts[2]);
        BoardPosition position = new BoardPosition(parseInt(parts[3], 0), parseInt(parts[4], 0));
        Rotation rotation = parseRotation(parts[5]);
        int colorIndex = parts.length < 7 ? -1 : parseInt(parts[6], -1);
        PieceShape shape = new PieceShape(type, name, cells);

        return new TetrisPiece(shape, position, rotation, colorIndex);
    }

    private static PieceShape deserializeShape(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }

        String[] parts = value.split(",", 3);
        if (parts.length < 3) {
            return null;
        }

        return new PieceShape(parsePieceType(parts[0]), decode(parts[1]), deserializeCells(parts[2]));
    }

    private static String serializeCells(List<BoardPosition> cells) {
        return cells.stream()
                .map(cell -> cell.row() + "." + cell.column())
                .collect(Collectors.joining("_"));
    }

    private static List<BoardPosition> deserializeCells(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split("_"))
                .map(cellValue -> cellValue.split("\\.", 2))
                .filter(parts -> parts.length == 2)
                .map(parts -> new BoardPosition(parseInt(parts[0], 0), parseInt(parts[1], 0)))
                .toList();
    }

    private static String serializePosition(BoardPosition position) {
        if (position == null) {
            return "-";
        }

        return position.row() + "," + position.column();
    }

    private static String serializeObject(TetrisBoardObject object) {
        if (object == null) {
            return "-";
        }

        return object.type().name() + "@"
                + serializePosition(object.position()) + "@"
                + object.lifetimeTicks();
    }

    private static TetrisBoardObject deserializeObject(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }

        String[] parts = value.split("@", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid board object entry: " + value);
        }

        BoardPosition position = deserializePosition(parts[1]);
        if (position == null) {
            throw new IllegalArgumentException("Invalid board object position: " + value);
        }

        int lifetimeTicks = parseInt(parts[2], TetrisBoardObject.DEFAULT_LIFETIME_TICKS);
        return new TetrisBoardObject(TetrisItemType.valueOf(parts[0]), position, lifetimeTicks);
    }

    private static String serializeEffects(TetrisEffectState effects) {
        TetrisEffectState safeEffects = effects == null ? TetrisEffectState.none() : effects;

        return safeEffects.gravityPercent()
                + ","
                + safeEffects.gravityTicks()
                + ","
                + safeEffects.rotationEffectTicks()
                + ","
                + safeEffects.rotationLagTicks();
    }

    private static TetrisEffectState deserializeEffects(String value) {
        if (value == null || value.isBlank()) {
            return TetrisEffectState.none();
        }

        String[] parts = value.split(",", -1);
        return new TetrisEffectState(
                parts.length > 0 ? parseInt(parts[0], TetrisEffectState.NORMAL_GRAVITY_PERCENT) : TetrisEffectState.NORMAL_GRAVITY_PERCENT,
                parts.length > 1 ? parseInt(parts[1], 0) : 0,
                parts.length > 2 ? parseInt(parts[2], 0) : 0,
                parts.length > 3 ? parseInt(parts[3], 0) : 0);
    }

    private static String serializeQueue(List<PieceShape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return "-";
        }

        return shapes.stream()
                .map(shape -> encode(serializeShape(shape)))
                .collect(Collectors.joining("."));
    }

    private static List<PieceShape> deserializeQueue(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return List.of();
        }

        return Arrays.stream(value.split("\\."))
                .map(encodedShape -> decode(encodedShape))
                .map(TetrisStateSnapshot::deserializeShape)
                .filter(shape -> shape != null)
                .toList();
    }

    private static BoardPosition deserializePosition(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }

        String[] parts = value.split(",", 2);
        if (parts.length != 2) {
            return null;
        }

        return new BoardPosition(parseInt(parts[0], -1), parseInt(parts[1], -1));
    }

    private static int parseColor(String value, int index) {
        if (value == null || index >= value.length()) {
            return -1;
        }

        char color = value.charAt(index);
        return color == '.' ? -1 : Character.digit(color, 36);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private static TetrisGameStatus parseStatus(String value) {
        try {
            return TetrisGameStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TetrisGameStatus.READY;
        }
    }

    private static PlayerStatus parsePlayerStatus(String value) {
        try {
            return PlayerStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return PlayerStatus.PLAYING;
        }
    }

    private static PieceType parsePieceType(String value) {
        try {
            return PieceType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return PieceType.CUSTOM;
        }
    }

    private static Rotation parseRotation(String value) {
        try {
            return Rotation.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Rotation.SPAWN;
        }
    }

    private static TetrisGameState fallbackState(TetrisGameConfig fallbackConfig) {
        return new TetrisGameState(
                null,
                null,
                fallbackConfig == null ? TetrisGameConfig.defaultConfig() : fallbackConfig,
                TetrisGameStatus.READY);
    }
}
