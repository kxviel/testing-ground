package seda_project.control_alt_defeat.gamebox.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessStateSnapshot;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryStateSnapshot;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisStateSnapshot;

class NetworkSnapshotHardeningTest {

    private static final int MAX_TETRIS_SNAPSHOT_DIMENSION = 64;

    @Test
    void memorySnapshotRejectsImpossibleDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> MemoryStateSnapshot.deserialize("0|0|0|0|0|0|0|false|"));
    }

    @Test
    void memoryModelIgnoresOutOfRangeSelection() {
        GameModel model = new GameModel(2, 1, 1, 2, List.of("A", "A"), 0);

        assertEquals(GameModel.SelectResult.IGNORED, model.selectCard(-1));
        assertEquals(GameModel.SelectResult.IGNORED, model.selectCard(2));
    }

    @Test
    void memorySnapshotRoundTripsPrimeBoardWithUnusedCells() {
        GameModel original = new GameModel(new BoardVariant(23, 1, "Prime Board"));

        GameModel restored = MemoryStateSnapshot.deserialize(MemoryStateSnapshot.serialize(original));

        assertEquals(5, restored.getRows());
        assertEquals(5, restored.getCols());
        assertEquals(23, restored.getCards().size());
    }

    @Test
    void tetrisConfigFallsBackWhenCustomPayloadIsMalformed() {
        TetrisGameConfig config = TetrisGameConfig.deserialize("Custom~0.0_2.2~550,false,false");

        assertEquals(List.of("Standard"), config.pieces());
        assertFalse(config.availableShapes().isEmpty());
    }

    @Test
    void tetrisSnapshotClampsHostileBoardDimensionsBeforeAllocation() {
        TetrisGameState state = assertDoesNotThrow(() -> TetrisStateSnapshot.deserialize(
                snapshotWithBoard("2147483647x-2147483648:"),
                TetrisGameConfig.defaultConfig()));

        assertTrue(state.bottomPlayer().board().rows() <= MAX_TETRIS_SNAPSHOT_DIMENSION);
        assertTrue(state.bottomPlayer().board().columns() <= MAX_TETRIS_SNAPSHOT_DIMENSION);
    }

    @Test
    void hexSnapshotRejectsNonFiniteScores() {
        String[] fields = HexChessStateSnapshot.serialize(HexGameState.standard()).split("~", -1);
        fields[8] = "NaN";

        assertThrows(IllegalArgumentException.class,
                () -> HexChessStateSnapshot.deserialize(String.join("~", fields)));
    }

    private static String snapshotWithBoard(String board) {
        String player = String.join(";",
                SnapshotCodec.encode("Player"),
                "0",
                PlayerStatus.PLAYING.name(),
                "-",
                board,
                "",
                SnapshotCodec.encode("-"),
                "-",
                "100,0,0,0",
                "-");

        return String.join("|",
                SnapshotCodec.encode(TetrisGameConfig.defaultConfig().serialize()),
                TetrisGameStatus.RUNNING.name(),
                SnapshotCodec.encode(player),
                SnapshotCodec.encode(player));
    }
}
