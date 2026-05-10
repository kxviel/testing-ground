package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;

import java.util.Objects;
import java.util.List;

public record TetrisPlayerState(
        String playerName,
        PlayerSide side,
        TetrisBoard board,
        TetrisPiece activePiece,
        int score,
        PlayerStatus status,
        Integer finalScore,
        BoardPosition bugPosition) {

    public TetrisPlayerState {
        playerName = playerName == null || playerName.isBlank() ? "Player" : playerName.trim();
        Objects.requireNonNull(side, "side");
        board = board == null ? new TetrisBoard() : board;
        score = Math.max(0, score);
        status = status == null ? PlayerStatus.PLAYING : status;
        bugPosition = canUseBugPosition(board, null, bugPosition) ? bugPosition : null;
    }

    public TetrisPlayerState(
            String playerName,
            PlayerSide side,
            TetrisBoard board,
            TetrisPiece activePiece,
            int score,
            PlayerStatus status,
            Integer finalScore) {
        this(playerName, side, board, activePiece, score, status, finalScore, null);
    }

    public static TetrisPlayerState create(String playerName, PlayerSide side) {
        return new TetrisPlayerState(playerName, side, new TetrisBoard(), null, 0, PlayerStatus.PLAYING, null, null);
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
        return new TetrisPlayerState(playerName, side, nextBoard, activePiece, score, status, finalScore, bugPosition);
    }

    public TetrisPlayerState withActivePiece(TetrisPiece nextPiece) {
        return new TetrisPlayerState(playerName, side, board, nextPiece, score, status, finalScore, bugPosition);
    }

    public TetrisPlayerState withScore(int nextScore) {
        return new TetrisPlayerState(playerName, side, board, activePiece, nextScore, status, finalScore, bugPosition);
    }

    public TetrisPlayerState withBugPosition(BoardPosition nextBugPosition) {
        if (nextBugPosition == null) {
            return clearBug();
        }
        if (!isPlaying() || bugPosition != null || !canUseBugPosition(board, activePiece, nextBugPosition)) {
            return this;
        }

        return new TetrisPlayerState(
                playerName,
                side,
                board,
                activePiece,
                score,
                status,
                finalScore,
                nextBugPosition);
    }

    public TetrisPlayerState clearBug() {
        return new TetrisPlayerState(playerName, side, board, activePiece, score, status, finalScore, null);
    }

    public boolean canPlaceBug(BoardPosition position) {
        return isPlaying() && bugPosition == null && canUseBugPosition(board, activePiece, position);
    }

    public boolean isTouchingBug() {
        return bugPosition != null
                && activePiece != null
                && activePiece.boardCells().contains(bugPosition);
    }

    public TetrisPlayerState withPlayStateFrom(TetrisPlayerState source) {
        return new TetrisPlayerState(
                playerName,
                side,
                source.board,
                source.activePiece,
                source.score,
                source.status,
                source.finalScore,
                source.bugPosition);
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
                finalScore,
                null);
    }

    public TetrisPlayerState lost() {
        return new TetrisPlayerState(playerName, side, board, null, score, PlayerStatus.LOST, score, null);
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

    private static boolean canUseBugPosition(TetrisBoard board, TetrisPiece activePiece, BoardPosition position) {
        if (position == null || board == null || !board.isEmpty(position)) {
            return false;
        }

        return activePiece == null || !activePiece.boardCells().contains(position);
    }
}
