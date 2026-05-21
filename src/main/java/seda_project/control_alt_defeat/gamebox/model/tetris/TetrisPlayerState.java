package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.List;
import java.util.Objects;

public record TetrisPlayerState(
        String playerName,
        PlayerSide side,
        TetrisBoard board,
        TetrisPiece activePiece,
        int score,
        PlayerStatus status,
        Integer finalScore,
        TetrisBoardObject boardObject,
        TetrisEffectState effects,
        List<PieceShape> queuedShapes) {

    public TetrisPlayerState {
        playerName = playerName == null || playerName.isBlank() ? "Player" : playerName.trim();
        Objects.requireNonNull(side, "side");
        board = board == null ? new TetrisBoard() : board;
        score = Math.max(0, score);
        status = status == null ? PlayerStatus.PLAYING : status;
        boardObject = canUseObjectPosition(board, null, boardObject) ? boardObject : null;
        effects = effects == null ? TetrisEffectState.none() : effects;
        queuedShapes = queuedShapes == null
                ? List.of()
                : queuedShapes.stream()
                        .filter(Objects::nonNull)
                        .toList();
    }

    public TetrisPlayerState(
            String playerName,
            PlayerSide side,
            TetrisBoard board,
            TetrisPiece activePiece,
            int score,
            PlayerStatus status,
            Integer finalScore) {
        this(playerName, side, board, activePiece, score, status, finalScore, null, null, List.of());
    }

    public TetrisPlayerState(
            String playerName,
            PlayerSide side,
            TetrisBoard board,
            TetrisPiece activePiece,
            int score,
            PlayerStatus status,
            Integer finalScore,
            BoardPosition bugPosition) {
        this(
                playerName,
                side,
                board,
                activePiece,
                score,
                status,
                finalScore,
                bugPosition == null ? null : new TetrisBoardObject(TetrisItemType.TELEPORT_SWAP, bugPosition),
                null,
                List.of());
    }

    public static TetrisPlayerState create(String playerName, PlayerSide side) {
        return new TetrisPlayerState(
                playerName,
                side,
                new TetrisBoard(),
                null,
                0,
                PlayerStatus.PLAYING,
                null,
                null,
                null,
                List.of());
    }

    public boolean isPlaying() {
        return status == PlayerStatus.PLAYING;
    }

    public TetrisPlayerState spawnPiece(PieceShape shape) {
        return spawnPiece(shape, -1, side.gravityDirection());
    }

    public TetrisPlayerState spawnPiece(PieceShape shape, int colorIndex) {
        return spawnPiece(shape, colorIndex, side.gravityDirection());
    }

    public TetrisPlayerState spawnPiece(PieceShape shape, int colorIndex, GravityDirection gravityDirection) {
        if (!isPlaying() || activePiece != null) {
            return this;
        }

        PieceShape spawnShape = queuedShapes.isEmpty() ? shape : queuedShapes.getFirst();
        if (spawnShape == null) {
            return this;
        }

        TetrisPiece piece = new TetrisPiece(
                spawnShape,
                spawnPosition(spawnShape, gravityDirection),
                Rotation.SPAWN,
                colorIndex);

        if (!board.canPlace(piece)) {
            return lost();
        }

        List<PieceShape> nextQueue = queuedShapes.isEmpty() ? queuedShapes : queuedShapes.subList(1, queuedShapes.size());
        return new TetrisPlayerState(
                playerName,
                side,
                board,
                piece,
                score,
                status,
                finalScore,
                boardObject,
                effects,
                nextQueue);
    }

    public TetrisPlayerState moveLeft() {
        return moveLeft(side.gravityDirection());
    }

    public TetrisPlayerState moveLeft(GravityDirection gravityDirection) {
        return move(perpendicularRowStep(gravityDirection, -1), perpendicularColumnStep(gravityDirection, -1));
    }

    public TetrisPlayerState moveRight() {
        return moveRight(side.gravityDirection());
    }

    public TetrisPlayerState moveRight(GravityDirection gravityDirection) {
        return move(perpendicularRowStep(gravityDirection, 1), perpendicularColumnStep(gravityDirection, 1));
    }

    public TetrisPlayerState softDrop() {
        return softDrop(side.gravityDirection());
    }

    public TetrisPlayerState softDrop(GravityDirection gravityDirection) {
        return move(gravityDirection.rowStep(), gravityDirection.columnStep());
    }

    public TetrisPlayerState applyGravity() {
        return applyGravity(side.gravityDirection());
    }

    public TetrisPlayerState applyGravity(GravityDirection gravityDirection) {
        if (!isPlaying() || activePiece == null) {
            return this;
        }

        TetrisPlayerState moved = softDrop(gravityDirection);
        if (moved != this) {
            return moved;
        }

        return lockActivePiece();
    }

    public TetrisPlayerState rotateClockwise() {
        if (!isPlaying() || activePiece == null || !effects.canRotate()) {
            return this;
        }

        TetrisPiece rotated = activePiece.withRotation(activePiece.rotation().clockwise());
        return board.canPlace(rotated) ? withActivePiece(rotated) : this;
    }

    public TetrisPlayerState withBoard(TetrisBoard nextBoard) {
        return new TetrisPlayerState(
                playerName,
                side,
                nextBoard,
                activePiece,
                score,
                status,
                finalScore,
                boardObject,
                effects,
                queuedShapes);
    }

    public TetrisPlayerState withActivePiece(TetrisPiece nextPiece) {
        return new TetrisPlayerState(
                playerName,
                side,
                board,
                nextPiece,
                score,
                status,
                finalScore,
                boardObject,
                effects,
                queuedShapes);
    }

    public TetrisPlayerState withScore(int nextScore) {
        return new TetrisPlayerState(
                playerName,
                side,
                board,
                activePiece,
                nextScore,
                status,
                finalScore,
                boardObject,
                effects,
                queuedShapes);
    }

    public TetrisPlayerState withBoardObject(TetrisBoardObject nextBoardObject) {
        if (nextBoardObject == null) {
            return clearBoardObject();
        }
        if (!isPlaying() || boardObject != null || !canUseObjectPosition(board, activePiece, nextBoardObject)) {
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
                nextBoardObject,
                effects,
                queuedShapes);
    }

    public TetrisPlayerState withBugPosition(BoardPosition nextBugPosition) {
        return withBoardObject(nextBugPosition == null
                ? null
                : new TetrisBoardObject(TetrisItemType.TELEPORT_SWAP, nextBugPosition));
    }

    public TetrisPlayerState clearBoardObject() {
        return new TetrisPlayerState(
                playerName,
                side,
                board,
                activePiece,
                score,
                status,
                finalScore,
                null,
                effects,
                queuedShapes);
    }

    public TetrisPlayerState clearBug() {
        return clearBoardObject();
    }

    public boolean canPlaceObject(TetrisBoardObject object) {
        return isPlaying() && boardObject == null && canUseObjectPosition(board, activePiece, object);
    }

    public boolean canPlaceBug(BoardPosition position) {
        return canPlaceObject(new TetrisBoardObject(TetrisItemType.TELEPORT_SWAP, position));
    }

    public boolean isTouchingObject() {
        return boardObject != null
                && activePiece != null
                && activePiece.boardCells().contains(boardObject.position());
    }

    public boolean isTouchingBug() {
        return isTouchingObject();
    }

    public BoardPosition bugPosition() {
        return boardObject == null ? null : boardObject.position();
    }

    public TetrisPlayerState withEffects(TetrisEffectState nextEffects) {
        return new TetrisPlayerState(
                playerName,
                side,
                board,
                activePiece,
                score,
                status,
                finalScore,
                boardObject,
                nextEffects,
                queuedShapes);
    }

    public TetrisPlayerState tickEffects() {
        return withEffects(effects.tick());
    }

    public TetrisPlayerState queueShape(PieceShape shape) {
        if (shape == null) {
            return this;
        }

        List<PieceShape> nextQueue = new java.util.ArrayList<>(queuedShapes);
        nextQueue.add(shape);

        return new TetrisPlayerState(
                playerName,
                side,
                board,
                activePiece,
                score,
                status,
                finalScore,
                boardObject,
                effects,
                nextQueue);
    }

    public boolean hasQueuedShape() {
        return !queuedShapes.isEmpty();
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
                source.boardObject,
                source.effects,
                source.queuedShapes);
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
                boardObject,
                effects,
                queuedShapes);
    }

    public TetrisPlayerState lost() {
        return new TetrisPlayerState(
                playerName,
                side,
                board,
                null,
                score,
                PlayerStatus.LOST,
                score,
                null,
                effects,
                queuedShapes);
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

    private static BoardPosition spawnPosition(PieceShape shape, GravityDirection gravityDirection) {
        GravityDirection direction = gravityDirection == null ? GravityDirection.DOWN : gravityDirection;
        int row = switch (direction) {
            case DOWN -> 0;
            case UP -> TetrisBoard.ROWS - shape.height();
            case LEFT, RIGHT -> (TetrisBoard.ROWS - shape.height()) / 2;
        };
        int column = switch (direction) {
            case DOWN, UP -> (TetrisBoard.COLUMNS - shape.width()) / 2;
            case RIGHT -> 0;
            case LEFT -> TetrisBoard.COLUMNS - shape.width();
        };

        return new BoardPosition(row, column);
    }

    private static int perpendicularRowStep(GravityDirection gravityDirection, int direction) {
        return gravityDirection != null && gravityDirection.isHorizontal() ? direction : 0;
    }

    private static int perpendicularColumnStep(GravityDirection gravityDirection, int direction) {
        return gravityDirection != null && gravityDirection.isHorizontal() ? 0 : direction;
    }

    private static boolean canUseObjectPosition(
            TetrisBoard board,
            TetrisPiece activePiece,
            TetrisBoardObject object) {
        if (object == null || board == null || !board.isEmpty(object.position())) {
            return false;
        }

        return activePiece == null || !activePiece.boardCells().contains(object.position());
    }
}
