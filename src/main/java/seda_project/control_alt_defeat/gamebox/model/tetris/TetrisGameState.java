package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;

import java.util.function.Function;

public record TetrisGameState(
        TetrisPlayerState bottomPlayer,
        TetrisPlayerState topPlayer,
        TetrisGameConfig config,
        TetrisGameStatus status) {

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
        return updatePlayer(side, player -> player.spawnPiece(shape));
    }

    public TetrisGameState spawnPiece(PlayerSide side, PieceShape shape, int colorIndex) {
        return updatePlayer(side, player -> player.spawnPiece(shape, colorIndex));
    }

    public TetrisGameState spawnBug(PlayerSide side, BoardPosition position) {
        return updatePlayer(side, player -> player.withBugPosition(position), false);
    }

    public TetrisGameState moveLeft(PlayerSide side) {
        return updatePlayer(side, TetrisPlayerState::moveLeft);
    }

    public TetrisGameState moveRight(PlayerSide side) {
        return updatePlayer(side, TetrisPlayerState::moveRight);
    }

    public TetrisGameState softDrop(PlayerSide side) {
        return updatePlayer(side, TetrisPlayerState::softDrop);
    }

    public TetrisGameState rotateClockwise(PlayerSide side) {
        return updatePlayer(side, TetrisPlayerState::rotateClockwise);
    }

    public TetrisGameState applyGravity(PlayerSide side) {
        return updatePlayer(side, TetrisPlayerState::applyGravity);
    }

    public TetrisGameState applyGravity() {
        if (status != TetrisGameStatus.RUNNING) {
            return this;
        }

        return new TetrisGameState(
                bottomPlayer.applyGravity(),
                topPlayer.applyGravity(),
                config,
                status).resolveBugHits().finishIfNeeded();
    }

    private TetrisGameState updatePlayer(PlayerSide side, Function<TetrisPlayerState, TetrisPlayerState> update) {
        return updatePlayer(side, update, true);
    }

    private TetrisGameState updatePlayer(
            PlayerSide side,
            Function<TetrisPlayerState, TetrisPlayerState> update,
            boolean resolveBugHits) {
        if (status == TetrisGameStatus.FINISHED) {
            return this;
        }

        TetrisGameState next;
        if (side == PlayerSide.BOTTOM) {
            next = new TetrisGameState(update.apply(bottomPlayer), topPlayer, config, status);
        } else if (side == PlayerSide.TOP) {
            next = new TetrisGameState(bottomPlayer, update.apply(topPlayer), config, status);
        } else {
            return this;
        }

        return (resolveBugHits ? next.resolveBugHits() : next).finishIfNeeded();
    }

    private TetrisGameState resolveBugHits() {
        boolean bottomHit = bottomPlayer.isTouchingBug();
        boolean topHit = topPlayer.isTouchingBug();

        if (!bottomHit && !topHit) {
            return this;
        }

        TetrisPlayerState cleanBottom = bottomHit ? bottomPlayer.clearBug() : bottomPlayer;
        TetrisPlayerState cleanTop = topHit ? topPlayer.clearBug() : topPlayer;

        if (!cleanBottom.isPlaying() || !cleanTop.isPlaying()) {
            return new TetrisGameState(cleanBottom, cleanTop, config, status);
        }

        return new TetrisGameState(
                cleanBottom.withPlayStateFrom(cleanTop),
                cleanTop.withPlayStateFrom(cleanBottom),
                config,
                status);
    }

    private TetrisGameState finishIfNeeded() {
        if (!bottomPlayer.isPlaying() && !topPlayer.isPlaying()) {
            return finished();
        }

        return this;
    }
}
