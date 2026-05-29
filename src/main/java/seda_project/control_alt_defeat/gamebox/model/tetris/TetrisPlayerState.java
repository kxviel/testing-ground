package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
        boardObject = canKeepObject(board, boardObject) ? boardObject : null;
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
        return create(playerName, side, false);
    }

    public static TetrisPlayerState create(String playerName, PlayerSide side, boolean horizontalMode) {
        return new TetrisPlayerState(
                playerName,
                side,
                TetrisBoard.createDefault(horizontalMode),
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
                spawnPosition(spawnShape, gravityDirection, board),
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

        return lockActivePiece(gravityDirection);
    }

    public TetrisPlayerState rotateClockwise() {
        if (!isPlaying() || activePiece == null) {
            return this;
        }

        if (effects.hasRotationDelayEffect()) {
            if (!effects.canAcceptRotationInput()) {
                return this;
            }
            return withEffects(effects.scheduleRotationLag(TetrisEffectState.ROTATION_LAG_TICKS));
        }

        return rotateImmediate();
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
        if (nextBoardObject == null || nextBoardObject.isExpired()) {
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
        return isPlaying() && boardObject == null && object != null && !object.isExpired()
                && canUseObjectPosition(board, activePiece, object);
    }

    public boolean canSpawnObject(TetrisBoardObject object, GravityDirection gravityDirection) {
        return canPlaceObject(object)
                && hasObjectSupport(object.position(), gravityDirection)
                && hasClearObjectApproach(object.position(), gravityDirection);
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
        TetrisEffectState previousEffects = effects;
        TetrisEffectState nextEffects = effects.tick();
        TetrisPlayerState next = new TetrisPlayerState(
                playerName,
                side,
                board,
                activePiece,
                score,
                status,
                finalScore,
                boardObject == null ? null : boardObject.tick(),
                nextEffects,
                queuedShapes);

        if (previousEffects.shouldApplyDelayedRotation()) {
            return next.rotateImmediate();
        }

        return next;
    }

    public TetrisPlayerState queueShape(PieceShape shape) {
        if (shape == null) {
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
                boardObject,
                effects,
                Stream.concat(queuedShapes.stream(), Stream.of(shape)).toList());
    }

    /** Prepends {@code shape} to the front of the spawn queue so it is the very next piece. */
    public TetrisPlayerState queueShapeFirst(PieceShape shape) {
        if (shape == null) {
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
                boardObject,
                effects,
                Stream.concat(Stream.of(shape), queuedShapes.stream()).toList());
    }

    public boolean hasQueuedShape() {
        return !queuedShapes.isEmpty();
    }

    public TetrisPlayerState lockActivePiece(GravityDirection gravityDirection) {
        if (activePiece == null || !board.canPlace(activePiece)) {
            return this;
        }

        TetrisBoard lockedBoard = board.lockPiece(activePiece);
        GravityDirection direction = gravityDirection == null ? side.gravityDirection() : gravityDirection;
        int clearedLines;
        TetrisBoard clearedBoard;

        if (direction.isHorizontal()) {
            List<Integer> fullColumns = lockedBoard.fullColumns();
            clearedBoard = lockedBoard.clearColumns(fullColumns);
            clearedLines = fullColumns.size();
        } else {
            List<Integer> fullRows = lockedBoard.fullRows();
            clearedBoard = lockedBoard.clearRows(fullRows);
            clearedLines = fullRows.size();
        }

        return new TetrisPlayerState(
                playerName,
                side,
                clearedBoard,
                null,
                score + clearedLines,
                status,
                finalScore,
                boardObject,
                effects,
                queuedShapes);
    }

    public TetrisPlayerState addTopRows(int count) {
        int n = Math.max(0, count);
        if (n == 0) return this;
        TetrisBoard newBoard = board.addRowsAtTop(n);
        TetrisPiece newPiece = activePiece == null ? null
                : activePiece.withPosition(new BoardPosition(
                        activePiece.position().row() + n,
                        activePiece.position().column()));
        TetrisBoardObject newObject = boardObject == null ? null
                : new TetrisBoardObject(
                        boardObject.type(),
                        new BoardPosition(boardObject.position().row() + n, boardObject.position().column()),
                        boardObject.lifetimeTicks());
        return new TetrisPlayerState(playerName, side, newBoard, newPiece, score, status, finalScore, newObject, effects, queuedShapes);
    }

    public TetrisPlayerState addBottomRows(int count) {
        int n = Math.max(0, count);
        if (n == 0) return this;
        TetrisBoard newBoard = board.addRowsAtBottom(n);
        return new TetrisPlayerState(playerName, side, newBoard, activePiece, score, status, finalScore, boardObject, effects, queuedShapes);
    }

    public TetrisPlayerState removeTopRows(int count) {
        int n = Math.min(count, board.rows() - TetrisBoard.MIN_ROWS);
        if (n <= 0) return this;
        TetrisBoard newBoard = board.removeRowsFromTop(n);
        TetrisPiece newPiece = null;
        if (activePiece != null) {
            int newRow = activePiece.position().row() - n;
            if (newRow >= 0) {
                TetrisPiece shifted = activePiece.withPosition(
                        new BoardPosition(newRow, activePiece.position().column()));
                newPiece = newBoard.canPlace(shifted) ? shifted : null;
            }
        }
        TetrisBoardObject newObject = null;
        if (boardObject != null) {
            int newRow = boardObject.position().row() - n;
            BoardPosition newPos = new BoardPosition(newRow, boardObject.position().column());
            if (newRow >= 0 && newBoard.isInside(newPos)) {
                newObject = new TetrisBoardObject(boardObject.type(), newPos, boardObject.lifetimeTicks());
            }
        }
        return new TetrisPlayerState(playerName, side, newBoard, newPiece, score, status, finalScore, newObject, effects, queuedShapes);
    }

    public TetrisPlayerState removeBottomRows(int count) {
        int n = Math.min(count, board.rows() - TetrisBoard.MIN_ROWS);
        if (n <= 0) return this;
        TetrisBoard newBoard = board.removeRowsFromBottom(n);
        TetrisPiece newPiece = null;
        if (activePiece != null) {
            newPiece = newBoard.canPlace(activePiece) ? activePiece : null;
        }
        TetrisBoardObject newObject = null;
        if (boardObject != null && newBoard.isInside(boardObject.position())) {
            newObject = boardObject;
        }
        return new TetrisPlayerState(playerName, side, newBoard, newPiece, score, status, finalScore, newObject, effects, queuedShapes);
    }

    public TetrisPlayerState addLeftColumns(int count) {
        return shiftForColumnChange(board.addColumnsAtLeft(count), count);
    }

    public TetrisPlayerState addRightColumns(int count) {
        return new TetrisPlayerState(
                playerName,
                side,
                board.addColumnsAtRight(count),
                activePiece,
                score,
                status,
                finalScore,
                boardObject,
                effects,
                queuedShapes);
    }

    public TetrisPlayerState removeLeftColumns(int count) {
        int removed = Math.min(count, board.columns() - TetrisBoard.MIN_COLUMNS);
        if (removed <= 0) {
            return this;
        }
        return shiftForColumnChange(board.removeColumnsFromLeft(removed), -removed);
    }

    public TetrisPlayerState removeRightColumns(int count) {
        int removed = Math.min(count, board.columns() - TetrisBoard.MIN_COLUMNS);
        if (removed <= 0) {
            return this;
        }

        TetrisBoard newBoard = board.removeColumnsFromRight(removed);
        TetrisPiece newPiece = activePiece != null && newBoard.canPlace(activePiece) ? activePiece : null;
        TetrisBoardObject newObject = boardObject != null && newBoard.isInside(boardObject.position())
                ? boardObject
                : null;
        return new TetrisPlayerState(
                playerName,
                side,
                newBoard,
                newPiece,
                score,
                status,
                finalScore,
                newObject,
                effects,
                queuedShapes);
    }

    private TetrisPlayerState shiftForColumnChange(TetrisBoard newBoard, int columnDelta) {
        if (columnDelta == 0) {
            return this;
        }

        TetrisPiece newPiece = null;
        if (activePiece != null) {
            int newColumn = activePiece.position().column() + columnDelta;
            if (newColumn >= 0) {
                TetrisPiece shifted = activePiece.withPosition(
                        new BoardPosition(activePiece.position().row(), newColumn));
                newPiece = newBoard.canPlace(shifted) ? shifted : null;
            }
        }

        TetrisBoardObject newObject = null;
        if (boardObject != null) {
            int newColumn = boardObject.position().column() + columnDelta;
            BoardPosition newPosition = new BoardPosition(boardObject.position().row(), newColumn);
            if (newColumn >= 0 && newBoard.isInside(newPosition)) {
                newObject = new TetrisBoardObject(
                        boardObject.type(),
                        newPosition,
                        boardObject.lifetimeTicks());
            }
        }

        return new TetrisPlayerState(
                playerName,
                side,
                newBoard,
                newPiece,
                score,
                status,
                finalScore,
                newObject,
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

    private static BoardPosition spawnPosition(
            PieceShape shape,
            GravityDirection gravityDirection,
            TetrisBoard board) {
        GravityDirection direction = gravityDirection == null ? GravityDirection.DOWN : gravityDirection;
        int boardRows = board.rows();
        int boardColumns = board.columns();
        int row = switch (direction) {
            case DOWN -> 0;
            case UP -> boardRows - shape.height();
            case LEFT, RIGHT -> (boardRows - shape.height()) / 2;
        };
        int column = switch (direction) {
            case DOWN, UP -> (boardColumns - shape.width()) / 2;
            case RIGHT -> 0;
            case LEFT -> boardColumns - shape.width();
        };

        return new BoardPosition(row, column);
    }

    private TetrisPlayerState rotateImmediate() {
        if (!isPlaying() || activePiece == null) {
            return this;
        }

        TetrisPiece rotated = activePiece.withRotation(activePiece.rotation().clockwise());
        return board.canPlace(rotated) ? withActivePiece(rotated) : this;
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

    private static boolean canKeepObject(TetrisBoard board, TetrisBoardObject object) {
        return object != null && !object.isExpired() && canUseObjectPosition(board, null, object);
    }

    private boolean hasObjectSupport(BoardPosition position, GravityDirection gravityDirection) {
        GravityDirection direction = gravityDirection == null ? side.gravityDirection() : gravityDirection;
        BoardPosition supportPosition = new BoardPosition(
                position.row() + direction.rowStep(),
                position.column() + direction.columnStep());

        return !board.isInside(supportPosition) || !board.isEmpty(supportPosition);
    }

    private boolean hasClearObjectApproach(BoardPosition position, GravityDirection gravityDirection) {
        GravityDirection direction = gravityDirection == null ? side.gravityDirection() : gravityDirection;
        BoardPosition approachPosition = new BoardPosition(
                position.row() - direction.rowStep(),
                position.column() - direction.columnStep());

        while (board.isInside(approachPosition)) {
            if (!board.isEmpty(approachPosition)) {
                return false;
            }
            approachPosition = new BoardPosition(
                    approachPosition.row() - direction.rowStep(),
                    approachPosition.column() - direction.columnStep());
        }

        return true;
    }
}
