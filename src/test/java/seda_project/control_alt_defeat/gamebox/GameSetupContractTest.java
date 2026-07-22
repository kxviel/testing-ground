package seda_project.control_alt_defeat.gamebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import seda_project.control_alt_defeat.gamebox.controller.hexchess.HexChessGameRouteData;
import seda_project.control_alt_defeat.gamebox.controller.memory.GameController;
import seda_project.control_alt_defeat.gamebox.controller.memory.MemoryGameRouteData;
import seda_project.control_alt_defeat.gamebox.controller.tetris.TetrisGameRouteData;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoard;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameMode;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.util.SafeText;

class GameSetupContractTest {

    @Test
    void memoryRouteFactoriesAssignRolesAndSanitizeNames() {
        BoardVariant variant = new BoardVariant(3, 5, "Test");
        MemoryGameRouteData local = MemoryGameRouteData.local(variant, " Alice\nOne ", " ");
        assertSame(variant, local.variant());
        assertEquals(GameController.Mode.LOCAL, local.mode());
        assertEquals("Alice One", local.playerOneName());
        assertEquals(SafeText.PLAYER_TWO_NAME, local.playerTwoName());

        Queue<String> pending = new ArrayDeque<>();
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        try {
            MemoryGameRouteData host = MemoryGameRouteData.host(variant, server, pending, "Host");
            assertEquals(GameController.Mode.NETWORK_HOST, host.mode());
            assertSame(server, host.server());
            assertNull(host.client());
            assertSame(pending, host.pendingMessages());

            MemoryGameRouteData join = MemoryGameRouteData.join(client, pending, "Joiner");
            assertEquals(GameController.Mode.NETWORK_CLIENT, join.mode());
            assertSame(client, join.client());
            assertNull(join.server());
            assertEquals("Joiner", join.playerTwoName());
        } finally {
            server.close();
            client.close();
        }
    }

    @Test
    void tetrisSetupFactoriesAssignCorrectLocalSidesAndDefaults() {
        TetrisGameSetup defaults = new TetrisGameSetup(null, null, null, null, PlayerSide.TOP);
        assertEquals(SafeText.PLAYER_ONE_NAME, defaults.playerOneName());
        assertEquals(SafeText.PLAYER_TWO_NAME, defaults.playerTwoName());
        assertEquals(TetrisGameMode.LOCAL, defaults.mode());
        assertNull(defaults.localSide());
        assertTrue(defaults.isLocal());

        TetrisGameConfig config = TetrisGameConfig.defaultConfig();
        TetrisGameSetup host = TetrisGameSetup.host("Host", "Joiner", config);
        TetrisGameSetup join = TetrisGameSetup.join("Host", "Joiner", config);
        assertEquals(PlayerSide.BOTTOM, host.localSide());
        assertEquals(PlayerSide.TOP, join.localSide());
        assertFalse(host.isLocal());
        assertSame(config, host.config());

        GameServer server = new GameServer();
        GameClient client = new GameClient();
        try {
            assertSame(server, TetrisGameRouteData.host(host, server).server());
            assertSame(client, TetrisGameRouteData.join(join, client).client());
            assertNull(TetrisGameRouteData.local(TetrisGameSetup.local("A", "B", config)).server());
        } finally {
            server.close();
            client.close();
        }
    }

    @Test
    void hexSetupDefaultsAndNetworkRoutesPreserveCustomPosition() {
        HexBoard custom = HexBoard.empty();
        HexChessGameSetup setup = new HexChessGameSetup(
                " White\nPlayer ", "", null, custom, HexPieceColor.BLACK, true);
        assertEquals("White Player", setup.whiteName());
        assertEquals(SafeText.PLAYER_TWO_NAME, setup.blackName());
        assertEquals(HexGameMode.LOCAL, setup.mode());
        assertSame(custom, setup.initialBoard());
        assertEquals(HexPieceColor.BLACK, setup.startingTurn());
        assertTrue(setup.customPosition());

        Queue<String> pending = new ArrayDeque<>();
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        try {
            HexChessGameRouteData host = HexChessGameRouteData.host(setup, server, pending);
            HexChessGameRouteData join = HexChessGameRouteData.join(setup, client, pending);
            assertEquals(HexGameMode.NETWORK_HOST, host.setup().mode());
            assertEquals(HexGameMode.NETWORK_CLIENT, join.setup().mode());
            assertTrue(host.setup().customPosition());
            assertSame(custom, join.setup().initialBoard());
            assertSame(server, host.server());
            assertSame(client, join.client());
            assertSame(pending, host.pendingMessages());
        } finally {
            server.close();
            client.close();
        }
    }
}
