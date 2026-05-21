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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public final class TetrisStateSnapshot {

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
        } catch (IllegalArgumentException e) {
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
        if (parts.length < 6) {
            return TetrisPlayerState.create("Player", side);
        }

        String name = decode(parts[0]);
        int score = parseInt(parts[1], 0);
        PlayerStatus status = parsePlayerStatus(parts[2]);
        Integer finalScore = "-".equals(parts[3]) ? null : parseInt(parts[3], score);
        String boardValue = parts[4];
        String colorValue = "";
        String pieceValue = parts[5];
        String objectValue = null;
        String effectsValue = null;
        String queueValue = null;

        if (parts.length >= 10) {
            colorValue = parts[5];
            pieceValue = parts[6];
            objectValue = parts[7];
            effectsValue = parts[8];
            queueValue = parts[9];
        } else if (parts.length >= 8) {
            colorValue = parts[5];
            pieceValue = parts[6];
            objectValue = parts[7];
        } else if (parts.length == 7) {
            objectValue = parts[6];
        }

        TetrisBoard board = deserializeBoard(boardValue, colorValue);
        TetrisPiece activePiece = deserializePiece(decode(pieceValue));
        TetrisBoardObject boardObject = deserializeObject(objectValue);
        TetrisEffectState effects = deserializeEffects(effectsValue);
        List<PieceShape> queuedShapes = deserializeQueue(queueValue);

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
        StringBuilder builder = new StringBuilder(TetrisBoard.ROWS * TetrisBoard.COLUMNS);

        for (int row = 0; row < TetrisBoard.ROWS; row++) {
            for (int column = 0; column < TetrisBoard.COLUMNS; column++) {
                TetrisCell cell = board.cellAt(new BoardPosition(row, column));
                builder.append(cell == TetrisCell.FILLED ? '1' : '0');
            }
        }

        return builder.toString();
    }

    private static TetrisBoard deserializeBoard(String value) {
        return deserializeBoard(value, "");
    }

    private static TetrisBoard deserializeBoard(String value, String colorsValue) {
        TetrisCell[][] cells = new TetrisCell[TetrisBoard.ROWS][TetrisBoard.COLUMNS];
        int[][] colors = new int[TetrisBoard.ROWS][TetrisBoard.COLUMNS];

        for (int row = 0; row < TetrisBoard.ROWS; row++) {
            for (int column = 0; column < TetrisBoard.COLUMNS; column++) {
                int index = row * TetrisBoard.COLUMNS + column;
                boolean filled = value != null && index < value.length() && value.charAt(index) == '1';
                cells[row][column] = filled ? TetrisCell.FILLED : TetrisCell.EMPTY;
                colors[row][column] = filled ? parseColor(colorsValue, index) : -1;
            }
        }

        return new TetrisBoard(cells, colors);
    }

    private static String serializeBoardColors(TetrisBoard board) {
        StringBuilder builder = new StringBuilder(TetrisBoard.ROWS * TetrisBoard.COLUMNS);

        for (int row = 0; row < TetrisBoard.ROWS; row++) {
            for (int column = 0; column < TetrisBoard.COLUMNS; column++) {
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

        return object.type().name() + "@" + serializePosition(object.position());
    }

    private static TetrisBoardObject deserializeObject(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }

        if (!value.contains("@")) {
            BoardPosition legacyBugPosition = deserializePosition(value);
            return legacyBugPosition == null
                    ? null
                    : new TetrisBoardObject(TetrisItemType.TELEPORT_SWAP, legacyBugPosition);
        }

        String[] parts = value.split("@", 2);
        BoardPosition position = deserializePosition(parts.length == 2 ? parts[1] : "-");
        if (position == null) {
            return null;
        }

        try {
            return new TetrisBoardObject(TetrisItemType.valueOf(parts[0]), position);
        } catch (IllegalArgumentException e) {
            return new TetrisBoardObject(TetrisItemType.TELEPORT_SWAP, position);
        }
    }

    private static String serializeEffects(TetrisEffectState effects) {
        TetrisEffectState safeEffects = effects == null ? TetrisEffectState.none() : effects;

        return safeEffects.gravityPercent()
                + ","
                + safeEffects.gravityTicks()
                + ","
                + safeEffects.rotationDelayTicks();
    }

    private static TetrisEffectState deserializeEffects(String value) {
        if (value == null || value.isBlank()) {
            return TetrisEffectState.none();
        }

        String[] parts = value.split(",", 3);
        return new TetrisEffectState(
                parts.length > 0 ? parseInt(parts[0], TetrisEffectState.NORMAL_GRAVITY_PERCENT) : TetrisEffectState.NORMAL_GRAVITY_PERCENT,
                parts.length > 1 ? parseInt(parts[1], 0) : 0,
                parts.length > 2 ? parseInt(parts[2], 0) : 0);
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
                .map(TetrisStateSnapshot::decode)
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

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
