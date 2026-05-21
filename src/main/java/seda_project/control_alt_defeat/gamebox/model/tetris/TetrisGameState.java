package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.function.Function;

public record TetrisGameState(
        TetrisPlayerState bottomPlayer,
        TetrisPlayerState topPlayer,
        TetrisGameConfig config,
        TetrisGameStatus status) {

    private static final int OBJECT_EFFECT_TICKS = 60;
    private static final int FAST_GRAVITY_PERCENT = 55;
    private static final int SLOW_GRAVITY_PERCENT = 165;
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

        return new TetrisGameState(
                TetrisPlayerState.create(safeSetup.playerOneName(), PlayerSide.BOTTOM),
                TetrisPlayerState.create(safeSetup.playerTwoName(), PlayerSide.TOP),
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
                    actor.withBoard(actor.board().destroyBelow(object.position())));
            case PORTAL -> actor.activePiece() == null
                    ? next
                    : next.withPlayer(side, actor.withActivePiece(null))
                            .withPlayer(opponentSide, opponent.queueShape(actor.activePiece().shape()));
            case TELEPORT_SWAP -> swapPlayStates(side);
        };
    }

    private TetrisGameState transferClearedRows(PlayerSide side, int clearedRows) {
        if (clearedRows <= 0) {
            return this;
        }

        PlayerSide opponentSide = opponent(side);
        TetrisPlayerState opponent = player(opponentSide);
        int holeColumn = Math.floorMod(player(side).score() + clearedRows, TetrisBoard.COLUMNS);

        return withPlayer(opponentSide, opponent.withBoard(opponent.board().addGarbageLines(clearedRows, holeColumn)));
    }

    private TetrisGameState swapPlayStates(PlayerSide triggeringSide) {
        PlayerSide otherSide = opponent(triggeringSide);
        TetrisPlayerState triggeringPlayer = player(triggeringSide).clearBoardObject();
        TetrisPlayerState otherPlayer = player(otherSide).clearBoardObject();
        TetrisPlayerState swappedTriggeringPlayer = triggeringPlayer.withPlayStateFrom(otherPlayer);
        TetrisPlayerState swappedOtherPlayer = otherPlayer.withPlayStateFrom(triggeringPlayer);

        return withPlayer(triggeringSide, swappedTriggeringPlayer)
                .withPlayer(otherSide, swappedOtherPlayer);
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
