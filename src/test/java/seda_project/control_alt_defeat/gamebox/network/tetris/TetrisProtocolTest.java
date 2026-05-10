package seda_project.control_alt_defeat.gamebox.network.tetris;

import org.junit.jupiter.api.Test;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TetrisProtocolTest {

    @Test
    void protocolKeepsColonTextInsideFields() {
        String message = TetrisProtocol.join("Player:Two");

        assertTrue(TetrisProtocol.isType(message, TetrisProtocol.JOIN));
        assertEquals(List.of("Player:Two"), TetrisProtocol.fields(message));
    }

    @Test
    void stateSnapshotRoundTripsBoardScoreAndActivePiece() {
        TetrisBoard board = new TetrisBoard()
                .withCell(new BoardPosition(19, 1), TetrisCell.FILLED);
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.T),
                new BoardPosition(3, 4),
                Rotation.RIGHT);
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                board,
                piece,
                2,
                PlayerStatus.PLAYING,
                null);
        TetrisPlayerState top = TetrisPlayerState.create("Top", PlayerSide.TOP).lost();
        TetrisGameState state = new TetrisGameState(
                bottom,
                top,
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        TetrisGameState copy = TetrisStateSnapshot.deserialize(
                TetrisStateSnapshot.serialize(state),
                TetrisGameConfig.defaultConfig());

        assertEquals("Bottom", copy.bottomPlayer().playerName());
        assertEquals(2, copy.bottomPlayer().score());
        assertEquals(TetrisCell.FILLED, copy.bottomPlayer().board().cellAt(new BoardPosition(19, 1)));
        assertEquals(Rotation.RIGHT, copy.bottomPlayer().activePiece().rotation());
        assertEquals(PlayerStatus.LOST, copy.topPlayer().status());
        assertEquals(TetrisGameStatus.RUNNING, copy.status());
    }
}
