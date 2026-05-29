package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public record TetrisGameState(
        TetrisPlayerState bottomPlayer,
        TetrisPlayerState topPlayer,
        TetrisGameConfig config,
        TetrisGameStatus status) {

    private static final int OBJECT_EFFECT_TICKS = 100;
    private static final int FAST_GRAVITY_PERCENT = 50;
    private static final int SLOW_GRAVITY_PERCENT = 200;
    private static final int EXPLOSION_RADIUS = 3;

    public TetrisGameState {
        bottomPlayer = bottomPlayer == null
                ? TetrisPlayerState.create("Player 1", PlayerSide.BOTTOM)
                : bottomPlayer;
        topPlayer = topPlayer == null
                ? TetrisPlayerState.create("Player 2", PlayerSide.TOP)
                : topPlayer;
        config = config == null ? TetrisGameConfig.defaultConfig() : config;
        status = status == null ? TetrisGameStatus.READY : status;
    }

    public static TetrisGameState create(TetrisGameSetup setup) {
        TetrisGameSetup safeSetup = setup == null
                ? new TetrisGameSetup("Player 1", "Player 2", TetrisGameConfig.defaultConfig())
                : setup;

        boolean horizontalMode = safeSetup.config().horizontalMode();
        return new TetrisGameState(
                TetrisPlayerState.create(safeSetup.playerOneName(), PlayerSide.BOTTOM, horizontalMode),
                TetrisPlayerState.create(safeSetup.playerTwoName(), PlayerSide.TOP, horizontalMode),
                safeSetup.config(),
                TetrisGameStatus.READY);
    }

    public TetrisGameState running() {
        return new TetrisGameState(bottomPlayer, topPlayer, config, TetrisGameStatus.RUNNING);
    }

    public TetrisGameState finished() {
        return new TetrisGameState(bottomPlayer, topPlayer, config, TetrisGameStatus.FINISHED);
    }

    public boolean isFinished() {
        return status == TetrisGameStatus.FINISHED;
    }

    public TetrisGameState spawnPiece(PlayerSide side, PieceShape shape) {
        return updatePlayer(side, player -> player.spawnPiece(shape, -1, config.gravityDirection(side)), false, false);
    }

    public TetrisGameState spawnPiece(PlayerSide side, PieceShape shape, int colorIndex) {
        return updatePlayer(side, player -> player.spawnPiece(shape, colorIndex, config.gravityDirection(side)), false, false);
    }

    public TetrisGameState spawnObject(PlayerSide side, TetrisBoardObject object) {
        return updatePlayer(side, player -> player.withBoardObject(object), false, false);
    }

    public TetrisGameState spawnBug(PlayerSide side, BoardPosition position) {
        return spawnObject(side, new TetrisBoardObject(TetrisItemType.TELEPORT_SWAP, position));
    }

    public TetrisGameState moveLeft(PlayerSide side) {
        return updatePlayer(side, player -> player.moveLeft(config.gravityDirection(side)), true, false);
    }

    public TetrisGameState moveRight(PlayerSide side) {
        return updatePlayer(side, player -> player.moveRight(config.gravityDirection(side)), true, false);
    }

    public TetrisGameState softDrop(PlayerSide side) {
        return applyGravity(side);
    }

    public TetrisGameState rotateClockwise(PlayerSide side) {
        return updatePlayer(side, TetrisPlayerState::rotateClockwise, true, false);
    }

    public TetrisGameState applyGravity(PlayerSide side) {
        return updatePlayer(side, player -> player.applyGravity(config.gravityDirection(side)), true, true);
    }

    public TetrisGameState applyGravity() {
        if (status != TetrisGameStatus.RUNNING) {
            return this;
        }

        return applyGravity(PlayerSide.BOTTOM).applyGravity(PlayerSide.TOP);
    }

    public TetrisGameState tickEffects() {
        if (status != TetrisGameStatus.RUNNING) {
            return this;
        }

        return new TetrisGameState(bottomPlayer.tickEffects(), topPlayer.tickEffects(), config, status);
    }

    private TetrisGameState updatePlayer(
            PlayerSide side,
            Function<TetrisPlayerState, TetrisPlayerState> update,
            boolean resolveObjects,
            boolean transferRows) {
        if (status == TetrisGameStatus.FINISHED) {
            return this;
        }

        TetrisPlayerState previousPlayer = player(side);
        if (previousPlayer == null) {
            return this;
        }

        TetrisPlayerState nextPlayer = update.apply(previousPlayer);
        TetrisGameState next = withPlayer(side, nextPlayer);

        if (transferRows) {
            int clearedRows = Math.max(0, nextPlayer.score() - previousPlayer.score());
            next = next.transferClearedRows(side, clearedRows);
        }

        return (resolveObjects ? next.resolveObjectHits() : next).finishIfNeeded();
    }

    private TetrisGameState resolveObjectHits() {
        TetrisGameState next = this;

        if (next.bottomPlayer.isTouchingObject()) {
            next = next.applyObjectEffect(PlayerSide.BOTTOM, next.bottomPlayer.boardObject());
        }
        if (next.topPlayer.isTouchingObject()) {
            next = next.applyObjectEffect(PlayerSide.TOP, next.topPlayer.boardObject());
        }

        return next;
    }

    private TetrisGameState applyObjectEffect(PlayerSide side, TetrisBoardObject object) {
        if (object == null) {
            return this;
        }

        PlayerSide opponentSide = opponent(side);
        TetrisPlayerState actor = player(side).clearBoardObject();
        TetrisPlayerState opponent = player(opponentSide);
        TetrisGameState next = withPlayer(side, actor);

        if (object.type().requiresActiveOpponent() && !opponent.isPlaying()) {
            return next;
        }

        return switch (object.type()) {
            case SPEED_UP_OPPONENT -> next.withPlayer(
                    opponentSide,
                    opponent.withEffects(opponent.effects().withGravityPercent(FAST_GRAVITY_PERCENT, OBJECT_EFFECT_TICKS)));
            case SLOW_SELF -> next.withPlayer(
                    side,
                    actor.withEffects(actor.effects().withGravityPercent(SLOW_GRAVITY_PERCENT, OBJECT_EFFECT_TICKS)));
            case ROTATION_DELAY_OPPONENT -> next.withPlayer(
                    opponentSide,
                    opponent.withEffects(opponent.effects().withRotationDelay(OBJECT_EFFECT_TICKS)));
            case ROTATION_DELAY_SELF -> next.withPlayer(
                    side,
                    actor.withEffects(actor.effects().withRotationDelay(OBJECT_EFFECT_TICKS)));
            case SLOW_OPPONENT -> next.withPlayer(
                    opponentSide,
                    opponent.withEffects(opponent.effects().withGravityPercent(SLOW_GRAVITY_PERCENT, OBJECT_EFFECT_TICKS)));
            case EXPLODE_RADIUS -> next.withPlayer(
                    side,
                    actor.withBoard(actor.board().destroyRadius(object.position(), EXPLOSION_RADIUS)));
            case EXPLODE_BELOW -> next.withPlayer(
                    side,
                    actor.withBoard(actor.board().destroyAlongGravity(
                            object.position(),
                            config.gravityDirection(side))));
            case PORTAL -> actor.activePiece() == null
                    ? next
                    : next.withPlayer(side, actor.withActivePiece(null))
                            .withPlayer(opponentSide, opponent.queueShapeFirst(actor.activePiece().shape()));
            case TELEPORT_SWAP -> swapPlayStates(side);
            case PIECE_SWAP -> swapActivePieces(next, side);
        };
    }

    private TetrisGameState transferClearedRows(PlayerSide side, int clearedRows) {
        if (clearedRows <= 0) {
            return this;
        }

        PlayerSide opponentSide = opponent(side);
        GravityDirection actorGravity = config.gravityDirection(side);
        GravityDirection opponentGravity = config.gravityDirection(opponentSide);

        TetrisPlayerState actor = player(side);
        TetrisPlayerState rewardedActor = actorGravity.isHorizontal()
                ? rewardHorizontalClear(actor, clearedRows, actorGravity)
                : rewardVerticalClear(actor, clearedRows, actorGravity);

        TetrisPlayerState opp = player(opponentSide);
        TetrisPlayerState penalisedOpp = opponentGravity.isHorizontal()
                ? penalizeHorizontalClear(opp, clearedRows, opponentGravity)
                : penalizeVerticalClear(opp, clearedRows, opponentGravity);

        return withPlayer(side, rewardedActor).withPlayer(opponentSide, penalisedOpp);
    }

    private static TetrisPlayerState rewardVerticalClear(
            TetrisPlayerState actor,
            int clearedRows,
            GravityDirection gravity) {
        return gravity == GravityDirection.UP
                ? actor.addBottomRows(clearedRows)
                : actor.addTopRows(clearedRows);
    }

    private static TetrisPlayerState penalizeVerticalClear(
            TetrisPlayerState opponent,
            int clearedRows,
            GravityDirection gravity) {
        return gravity == GravityDirection.UP
                ? opponent.removeBottomRows(clearedRows)
                : opponent.removeTopRows(clearedRows);
    }

    private static TetrisPlayerState rewardHorizontalClear(
            TetrisPlayerState actor,
            int clearedColumns,
            GravityDirection gravity) {
        return gravity == GravityDirection.LEFT
                ? actor.addRightColumns(clearedColumns)
                : actor.addLeftColumns(clearedColumns);
    }

    private static TetrisPlayerState penalizeHorizontalClear(
            TetrisPlayerState opponent,
            int clearedColumns,
            GravityDirection gravity) {
        return gravity == GravityDirection.LEFT
                ? opponent.removeRightColumns(clearedColumns)
                : opponent.removeLeftColumns(clearedColumns);
    }

    private TetrisGameState swapPlayStates(PlayerSide triggeringSide) {
        PlayerSide otherSide = opponent(triggeringSide);
        TetrisPlayerState triggeringPlayer = player(triggeringSide).clearBoardObject();
        TetrisPlayerState otherPlayer = player(otherSide).clearBoardObject();
        TetrisPlayerState swappedTriggeringPlayer = swapBoardAndPiece(triggeringPlayer, otherPlayer);
        TetrisPlayerState swappedOtherPlayer = swapBoardAndPiece(otherPlayer, triggeringPlayer);

        return withPlayer(triggeringSide, swappedTriggeringPlayer)
                .withPlayer(otherSide, swappedOtherPlayer);
    }

    private TetrisPlayerState swapBoardAndPiece(TetrisPlayerState target, TetrisPlayerState source) {
        TetrisBoard swappedBoard = mirrorBoard(source.board());
        TetrisPiece swappedPiece = mirrorPiece(source.activePiece(), source.board(), swappedBoard);
        return new TetrisPlayerState(
                target.playerName(),
                target.side(),
                swappedBoard,
                swappedPiece,
                target.score(),
                target.status(),
                target.finalScore(),
                null,
                target.effects(),
                target.queuedShapes());
    }

    private TetrisBoard mirrorBoard(TetrisBoard sourceBoard) {
        TetrisCell[][] cells = sourceBoard.cells();
        int[][] colors = sourceBoard.colors();
        TetrisCell[][] mirroredCells = new TetrisCell[sourceBoard.rows()][sourceBoard.columns()];
        int[][] mirroredColors = new int[sourceBoard.rows()][sourceBoard.columns()];

        for (int row = 0; row < sourceBoard.rows(); row++) {
            for (int column = 0; column < sourceBoard.columns(); column++) {
                BoardPosition mirroredPosition = mirrorPosition(
                        new BoardPosition(row, column),
                        sourceBoard.rows(),
                        sourceBoard.columns());
                mirroredCells[mirroredPosition.row()][mirroredPosition.column()] = cells[row][column];
                mirroredColors[mirroredPosition.row()][mirroredPosition.column()] = colors[row][column];
            }
        }

        return new TetrisBoard(mirroredCells, mirroredColors);
    }

    private TetrisPiece mirrorPiece(TetrisPiece sourcePiece, TetrisBoard sourceBoard, TetrisBoard mirroredBoard) {
        if (sourcePiece == null) {
            return null;
        }

        Set<BoardPosition> mirroredCells = sourcePiece.boardCells().stream()
                .map(position -> mirrorPosition(position, sourceBoard.rows(), sourceBoard.columns()))
                .collect(HashSet::new, Set::add, Set::addAll);

        int minRow = mirroredCells.stream().mapToInt(BoardPosition::row).min().orElse(0);
        int minColumn = mirroredCells.stream().mapToInt(BoardPosition::column).min().orElse(0);
        BoardPosition anchor = new BoardPosition(minRow, minColumn);

        for (PieceShape shape : config.availableShapes()) {
            for (Rotation rotation : Rotation.values()) {
                TetrisPiece candidate = new TetrisPiece(shape, anchor, rotation, sourcePiece.colorIndex());
                if (mirroredCells.equals(new HashSet<>(candidate.boardCells())) && mirroredBoard.canPlace(candidate)) {
                    return candidate;
                }
            }
        }

        List<BoardPosition> normalizedCells = mirroredCells.stream()
                .map(position -> new BoardPosition(position.row() - minRow, position.column() - minColumn))
                .toList();
        PieceShape fallbackShape = new PieceShape(PieceType.CUSTOM, sourcePiece.shape().name(), normalizedCells);
        TetrisPiece fallback = new TetrisPiece(fallbackShape, anchor, Rotation.SPAWN, sourcePiece.colorIndex());
        return mirroredBoard.canPlace(fallback) ? fallback : null;
    }

    private BoardPosition mirrorPosition(BoardPosition position, int rows, int columns) {
        if (config.horizontalMode()) {
            return new BoardPosition(position.row(), columns - 1 - position.column());
        }

        return new BoardPosition(rows - 1 - position.row(), position.column());
    }

    private TetrisGameState swapActivePieces(TetrisGameState base, PlayerSide triggeringSide) {
        PlayerSide otherSide = opponent(triggeringSide);
        TetrisPlayerState actor = base.player(triggeringSide);
        TetrisPlayerState other = base.player(otherSide);

        if (actor.activePiece() == null || other.activePiece() == null) {
            return base;
        }

        TetrisPiece actorPiece = actor.activePiece();
        TetrisPiece otherPiece = other.activePiece();

        TetrisPiece newActorPiece = buildSwappedPiece(otherPiece.shape(), actorPiece, otherPiece.colorIndex(), actor.board());
        TetrisPiece newOtherPiece = buildSwappedPiece(actorPiece.shape(), otherPiece, actorPiece.colorIndex(), other.board());

        TetrisPlayerState newActor = newActorPiece != null
                ? actor.withActivePiece(newActorPiece)
                : actor.withActivePiece(null).queueShapeFirst(otherPiece.shape());

        TetrisPlayerState newOther = newOtherPiece != null
                ? other.withActivePiece(newOtherPiece)
                : other.withActivePiece(null).queueShapeFirst(actorPiece.shape());

        return base.withPlayer(triggeringSide, newActor).withPlayer(otherSide, newOther);
    }

    private static TetrisPiece buildSwappedPiece(
            PieceShape newShape,
            TetrisPiece currentPiece,
            int colorIndex,
            TetrisBoard board) {
        TetrisPiece candidate = new TetrisPiece(
                newShape, currentPiece.position(), currentPiece.rotation(), colorIndex);
        if (board.canPlace(candidate)) {
            return candidate;
        }
        TetrisPiece spawnRotation = new TetrisPiece(
                newShape, currentPiece.position(), Rotation.SPAWN, colorIndex);
        if (board.canPlace(spawnRotation)) {
            return spawnRotation;
        }
        return null;
    }

    private TetrisGameState withPlayer(PlayerSide side, TetrisPlayerState player) {
        if (side == PlayerSide.BOTTOM) {
            return new TetrisGameState(player, topPlayer, config, status);
        }
        if (side == PlayerSide.TOP) {
            return new TetrisGameState(bottomPlayer, player, config, status);
        }

        return this;
    }

    private TetrisPlayerState player(PlayerSide side) {
        return switch (side) {
            case BOTTOM -> bottomPlayer;
            case TOP -> topPlayer;
        };
    }

    private static PlayerSide opponent(PlayerSide side) {
        return side == PlayerSide.TOP ? PlayerSide.BOTTOM : PlayerSide.TOP;
    }

    private TetrisGameState finishIfNeeded() {
        if (!bottomPlayer.isPlaying() && !topPlayer.isPlaying()) {
            return finished();
        }

        return this;
    }
}
