package seda_project.control_alt_defeat.gamebox.network.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoard;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPiece;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPlayerState;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
                encode(serializePiece(player.activePiece())),
                serializePosition(player.bugPosition()));
    }

    private static TetrisPlayerState deserializePlayer(String value, PlayerSide side) {
        String[] parts = value.split(";", 7);
        if (parts.length < 6) {
            return TetrisPlayerState.create("Player", side);
        }

        String name = decode(parts[0]);
        int score = parseInt(parts[1], 0);
        PlayerStatus status = parsePlayerStatus(parts[2]);
        Integer finalScore = "-".equals(parts[3]) ? null : parseInt(parts[3], score);
        TetrisBoard board = deserializeBoard(parts[4]);
        TetrisPiece activePiece = deserializePiece(decode(parts[5]));
        BoardPosition bugPosition = parts.length < 7 ? null : deserializePosition(parts[6]);

        return new TetrisPlayerState(name, side, board, activePiece, score, status, finalScore, bugPosition);
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
        TetrisCell[][] cells = new TetrisCell[TetrisBoard.ROWS][TetrisBoard.COLUMNS];

        for (int row = 0; row < TetrisBoard.ROWS; row++) {
            for (int column = 0; column < TetrisBoard.COLUMNS; column++) {
                int index = row * TetrisBoard.COLUMNS + column;
                boolean filled = value != null && index < value.length() && value.charAt(index) == '1';
                cells[row][column] = filled ? TetrisCell.FILLED : TetrisCell.EMPTY;
            }
        }

        return new TetrisBoard(cells);
    }

    private static String serializePiece(TetrisPiece piece) {
        if (piece == null) {
            return "-";
        }

        return String.join(",",
                piece.shape().type().name(),
                encode(piece.shape().name()),
                serializeCells(piece.shape().cells()),
                String.valueOf(piece.position().row()),
                String.valueOf(piece.position().column()),
                piece.rotation().name());
    }

    private static TetrisPiece deserializePiece(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }

        String[] parts = value.split(",", 6);
        if (parts.length != 6) {
            return null;
        }

        PieceType type = parsePieceType(parts[0]);
        String name = decode(parts[1]);
        List<BoardPosition> cells = deserializeCells(parts[2]);
        BoardPosition position = new BoardPosition(parseInt(parts[3], 0), parseInt(parts[4], 0));
        Rotation rotation = parseRotation(parts[5]);
        PieceShape shape = new PieceShape(type, name, cells);

        return new TetrisPiece(shape, position, rotation);
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

        List<BoardPosition> cells = new ArrayList<>();
        String[] cellValues = value.split("_");

        for (String cellValue : cellValues) {
            String[] parts = cellValue.split("\\.", 2);
            if (parts.length == 2) {
                cells.add(new BoardPosition(parseInt(parts[0], 0), parseInt(parts[1], 0)));
            }
        }

        return cells;
    }

    private static String serializePosition(BoardPosition position) {
        if (position == null) {
            return "-";
        }

        return position.row() + "," + position.column();
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
