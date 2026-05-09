package seda_project.control_alt_defeat.gamebox.model.tetris;

import java.util.Objects;
import java.util.List;

public record TetrisPlayerState(
        String playerName,
        PlayerSide side,
        TetrisBoard board,
        TetrisPiece activePiece,
        int score,
        PlayerStatus status,
        Integer finalScore) {

    public TetrisPlayerState {
        playerName = playerName == null || playerName.isBlank() ? "Player" : playerName.trim();
        Objects.requireNonNull(side, "side");
        board = board == null ? new TetrisBoard() : board;
        score = Math.max(0, score);
        status = status == null ? PlayerStatus.PLAYING : status;
    }

    public static TetrisPlayerState create(String playerName, PlayerSide side) {
        return new TetrisPlayerState(playerName, side, new TetrisBoard(), null, 0, PlayerStatus.PLAYING, null);
    }

    public boolean isPlaying() {
        return status == PlayerStatus.PLAYING;
    }

    public TetrisPlayerState spawnPiece(PieceShape shape) {
        if (!isPlaying() || shape == null || activePiece != null) {
            return this;
        }

        int row = 0;
        int column = (TetrisBoard.COLUMNS - shape.width()) / 2;
        TetrisPiece piece = new TetrisPiece(shape, new BoardPosition(row, column), Rotation.SPAWN);

        if (!board.canPlace(piece)) {
            return lost();
        }

        return withActivePiece(piece);
    }

    public TetrisPlayerState moveLeft() {
        return move(0, -1);
    }

    public TetrisPlayerState moveRight() {
        return move(0, 1);
    }

    public TetrisPlayerState softDrop() {
        return move(side.gravityDirection().rowStep(), 0);
    }

    public TetrisPlayerState applyGravity() {
        if (!isPlaying() || activePiece == null) {
            return this;
        }

        TetrisPlayerState moved = softDrop();
        if (moved != this) {
            return moved;
        }

        return lockActivePiece();
    }

    public TetrisPlayerState rotateClockwise() {
        if (!isPlaying() || activePiece == null) {
            return this;
        }

        TetrisPiece rotated = activePiece.withRotation(activePiece.rotation().clockwise());
        return board.canPlace(rotated) ? withActivePiece(rotated) : this;
    }

    public TetrisPlayerState withBoard(TetrisBoard nextBoard) {
        return new TetrisPlayerState(playerName, side, nextBoard, activePiece, score, status, finalScore);
    }

    public TetrisPlayerState withActivePiece(TetrisPiece nextPiece) {
        return new TetrisPlayerState(playerName, side, board, nextPiece, score, status, finalScore);
    }

    public TetrisPlayerState withScore(int nextScore) {
        return new TetrisPlayerState(playerName, side, board, activePiece, nextScore, status, finalScore);
    }

    public TetrisPlayerState lockActivePiece() {
        if (activePiece == null || !board.canPlace(activePiece)) {
            return this;
        }

        TetrisBoard lockedBoard = board.lockPiece(activePiece);
        List<Integer> fullRows = lockedBoard.fullRows();
        TetrisBoard clearedBoard = lockedBoard.clearRows(fullRows);

        return new TetrisPlayerState(
                playerName,
                side,
                clearedBoard,
                null,
                score + fullRows.size(),
                status,
                finalScore);
    }

    public TetrisPlayerState lost() {
        return new TetrisPlayerState(playerName, side, board, null, score, PlayerStatus.LOST, score);
    }

    private TetrisPlayerState move(int rowDelta, int columnDelta) {
        if (!isPlaying() || activePiece == null) {
            return this;
        }

        BoardPosition position = activePiece.position();
        TetrisPiece moved = activePiece.withPosition(new BoardPosition(
                position.row() + rowDelta,
                position.column() + columnDelta));

        return board.canPlace(moved) ? withActivePiece(moved) : this;
    }
}
