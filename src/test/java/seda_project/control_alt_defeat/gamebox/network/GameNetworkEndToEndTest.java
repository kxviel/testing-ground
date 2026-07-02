package seda_project.control_alt_defeat.gamebox.network;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMove;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessProtocol;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessStateSnapshot;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryProtocol;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryStateSnapshot;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisProtocol;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisStateSnapshot;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameNetworkEndToEndTest {

    @Test
    void memoryProtocolRoundTripsOverTcpTransport() throws Exception {
        GameModel model = new GameModel(2, 2, 2, 2, List.of("A:B", "A:B", "C", "C"), 0);

        try (TransportPair transport = connectedPair()) {
            String join = MemoryProtocol.join("Player:Two");
            transport.client().send(join);

            String hostMessage = await(transport.serverMessages());
            assertEquals(join, hostMessage);
            assertTrue(MemoryProtocol.isType(hostMessage, MemoryProtocol.JOIN));
            assertEquals(List.of("Player:Two"), MemoryProtocol.fields(hostMessage));

            String start = MemoryProtocol.start("Player:One", "Player:Two", MemoryStateSnapshot.serialize(model));
            transport.server().send(start);

            String joinerMessage = await(transport.clientMessages());
            assertEquals(start, joinerMessage);
            List<String> startFields = MemoryProtocol.fields(joinerMessage);
            assertEquals("Player:One", startFields.getFirst());
            assertEquals("Player:Two", startFields.get(1));
            assertEquals(model.getSymbolOrder(), MemoryStateSnapshot.deserialize(startFields.get(2)).getSymbolOrder());

            String flip = MemoryProtocol.flip(1);
            transport.client().send(flip);

            hostMessage = await(transport.serverMessages());
            assertEquals(flip, hostMessage);
            assertTrue(MemoryProtocol.isType(hostMessage, MemoryProtocol.FLIP));
            assertEquals(List.of("1"), MemoryProtocol.fields(hostMessage));

            model.selectCard(0);
            String state = MemoryProtocol.state(MemoryStateSnapshot.serialize(model));
            transport.server().send(state);

            joinerMessage = await(transport.clientMessages());
            assertEquals(state, joinerMessage);
            assertTrue(MemoryProtocol.isType(joinerMessage, MemoryProtocol.STATE));
            assertTrue(MemoryStateSnapshot.deserialize(MemoryProtocol.fields(joinerMessage).getFirst())
                    .getCard(0).isFaceUp());

            String restart = MemoryProtocol.restartRequest("Player:Two");
            transport.client().send(restart);

            hostMessage = await(transport.serverMessages());
            assertEquals(restart, hostMessage);
            assertEquals(List.of("Player:Two"), MemoryProtocol.fields(hostMessage));
        }
    }

    @Test
    void tetrisProtocolRoundTripsStartInputStateAndQuitOverTcpTransport() throws Exception {
        TetrisGameConfig config = TetrisGameConfig.defaultConfig();

        try (TransportPair transport = connectedPair()) {
            String join = TetrisProtocol.join("Player:Two");
            transport.client().send(join);

            String hostMessage = await(transport.serverMessages());
            assertEquals(join, hostMessage);
            assertTrue(TetrisProtocol.isType(hostMessage, TetrisProtocol.JOIN));
            assertEquals(List.of("Player:Two"), TetrisProtocol.fields(hostMessage));

            String start = TetrisProtocol.start("Player:One", "Player:Two", config);
            transport.server().send(start);

            String joinerMessage = await(transport.clientMessages());
            assertEquals(start, joinerMessage);
            List<String> startFields = TetrisProtocol.fields(joinerMessage);
            assertEquals("Player:One", startFields.getFirst());
            assertEquals("Player:Two", startFields.get(1));
            assertTrue(TetrisGameConfig.deserialize(startFields.get(2)).hasPieces());

            String input = TetrisProtocol.input(PlayerSide.TOP, TetrisProtocol.MOVE_LEFT);
            transport.client().send(input);

            hostMessage = await(transport.serverMessages());
            assertEquals(input, hostMessage);
            assertEquals(List.of(PlayerSide.TOP.name(), TetrisProtocol.MOVE_LEFT), TetrisProtocol.fields(hostMessage));

            TetrisGameState state = TetrisGameState.create(
                    TetrisGameSetup.local("Player 1", "Player 2", config)).running();
            String stateMessage = TetrisProtocol.state(TetrisStateSnapshot.serialize(state));
            transport.server().send(stateMessage);

            joinerMessage = await(transport.clientMessages());
            assertEquals(stateMessage, joinerMessage);
            TetrisGameState copy = TetrisStateSnapshot.deserialize(
                    TetrisProtocol.fields(joinerMessage).getFirst(),
                    config);
            assertEquals(TetrisGameStatus.RUNNING, copy.status());
            assertEquals("Player 1", copy.bottomPlayer().playerName());
            assertEquals("Player 2", copy.topPlayer().playerName());

            String quit = TetrisProtocol.quit("Player:Two");
            transport.client().send(quit);

            hostMessage = await(transport.serverMessages());
            assertEquals(quit, hostMessage);
            assertEquals(List.of("Player:Two"), TetrisProtocol.fields(hostMessage));
        }
    }

    @Test
    void hexChessProtocolRoundTripsStartMoveStateAndQuitOverTcpTransport() throws Exception {
        HexChessGameSetup setup = new HexChessGameSetup("Player:One", "Player:Two", HexGameMode.NETWORK_HOST);
        HexGameState state = HexGameState.standard()
                .play(new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b3")));

        try (TransportPair transport = connectedPair()) {
            String join = HexChessProtocol.join("Player:Two");
            transport.client().send(join);

            String hostMessage = await(transport.serverMessages());
            assertEquals(join, hostMessage);
            assertTrue(HexChessProtocol.isType(hostMessage, HexChessProtocol.JOIN));
            assertEquals(List.of("Player:Two"), HexChessProtocol.fields(hostMessage));

            String start = HexChessProtocol.start(setup, HexChessStateSnapshot.serialize(HexGameState.standard()));
            transport.server().send(start);

            String joinerMessage = await(transport.clientMessages());
            assertEquals(start, joinerMessage);
            List<String> startFields = HexChessProtocol.fields(joinerMessage);
            assertEquals("Player:One", startFields.getFirst());
            assertEquals("Player:Two", startFields.get(1));
            assertEquals(HexGameState.standard().board(), HexChessStateSnapshot.deserialize(startFields.get(2)).board());

            HexMove move = new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b3"));
            String moveMessage = HexChessProtocol.move(move);
            transport.client().send(moveMessage);

            hostMessage = await(transport.serverMessages());
            assertEquals(moveMessage, hostMessage);
            assertEquals(move, HexChessProtocol.parseMove(HexChessProtocol.fields(hostMessage)));

            String stateMessage = HexChessProtocol.state(HexChessStateSnapshot.serialize(state));
            transport.server().send(stateMessage);

            joinerMessage = await(transport.clientMessages());
            assertEquals(stateMessage, joinerMessage);
            HexGameState copy = HexChessStateSnapshot.deserialize(HexChessProtocol.fields(joinerMessage).getFirst());
            assertEquals(state.board(), copy.board());
            assertEquals(state.lastMove(), copy.lastMove());

            String quit = HexChessProtocol.simple(HexChessProtocol.QUIT);
            transport.client().send(quit);

            hostMessage = await(transport.serverMessages());
            assertEquals(quit, hostMessage);
            assertTrue(HexChessProtocol.isType(hostMessage, HexChessProtocol.QUIT));
        }
    }

    private static TransportPair connectedPair() throws Exception {
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        BlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
        BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();
        CountDownLatch serverAccepted = new CountDownLatch(1);
        AtomicReference<Exception> serverError = new AtomicReference<>();

        server.listen(0, serverMessages::add, () -> {
        });

        Thread acceptThread = new Thread(() -> {
            try {
                server.waitForClient();
            } catch (Exception e) {
                serverError.set(e);
            } finally {
                serverAccepted.countDown();
            }
        }, "game-network-e2e-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        client.connect("127.0.0.1", server.localPort(), clientMessages::add, () -> {
        });
        assertTrue(serverAccepted.await(5, TimeUnit.SECONDS));
        assertNull(serverError.get());

        return new TransportPair(server, client, serverMessages, clientMessages);
    }

    private static String await(BlockingQueue<String> messages) throws InterruptedException {
        String message = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "expected a network message");
        return message;
    }

    private record TransportPair(
            GameServer server,
            GameClient client,
            BlockingQueue<String> serverMessages,
            BlockingQueue<String> clientMessages) implements AutoCloseable {

        @Override
        public void close() {
            client.close();
            server.close();
        }
    }
}
