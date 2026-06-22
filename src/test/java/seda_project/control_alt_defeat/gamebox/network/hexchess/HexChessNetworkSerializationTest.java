package seda_project.control_alt_defeat.gamebox.network.hexchess;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMove;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexChessNetworkSerializationTest {

    @Test
    void snapshotRoundTripPreservesRuleState() {
        HexGameState state = HexGameState.standard()
                .offerDraw()
                .play(new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b3")));

        HexGameState copy = HexChessStateSnapshot.deserialize(HexChessStateSnapshot.serialize(state));

        assertEquals(state.board(), copy.board());
        assertEquals(state.turn(), copy.turn());
        assertEquals(state.status(), copy.status());
        assertEquals(state.lastMove(), copy.lastMove());
        assertEquals(state.repetitionCounts(), copy.repetitionCounts());
        assertEquals(state.doubleMoveEligibleSquares(), copy.doubleMoveEligibleSquares());
    }

    @Test
    void malformedSnapshotIsRejectedInsteadOfResettingToStandardBoard() {
        assertThrows(IllegalArgumentException.class, () -> HexChessStateSnapshot.deserialize("bad"));
    }

    @Test
    void malformedLastMoveIsRejectedInsteadOfBeingDropped() {
        HexGameState state = HexGameState.standard()
                .play(new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b3")));
        String[] fields = HexChessStateSnapshot.serialize(state).split("~", -1);
        fields[4] = "b1,b3";

        assertThrows(IllegalArgumentException.class, () -> HexChessStateSnapshot.deserialize(String.join("~", fields)));
    }

    @Test
    void malformedMovePayloadIsRejected() {
        assertNull(HexChessProtocol.parseMove(HexChessProtocol.fields(moveMessage("b1", "b3", "", "maybe"))));
        assertNull(HexChessProtocol.parseMove(HexChessProtocol.fields(moveMessage("b1", "b3", "KING", "false"))));
    }

    @Test
    void validPromotionMovePayloadParses() {
        HexMove move = HexChessProtocol.parseMove(HexChessProtocol.fields(moveMessage(
                "b6",
                "b7",
                HexPieceType.QUEEN.name(),
                "false")));

        assertEquals(HexCoordinate.of("b6"), move.from());
        assertEquals(HexCoordinate.of("b7"), move.to());
        assertEquals(HexPieceType.QUEEN, move.promotion());
    }

    @Test
    void tcpTransportCarriesHexChessProtocolMessagesBothWays() throws Exception {
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        CountDownLatch serverAccepted = new CountDownLatch(1);
        CountDownLatch serverReceived = new CountDownLatch(1);
        CountDownLatch clientReceived = new CountDownLatch(1);
        AtomicReference<String> serverMessage = new AtomicReference<>();
        AtomicReference<String> clientMessage = new AtomicReference<>();
        AtomicReference<Exception> serverError = new AtomicReference<>();

        try {
            server.listen(0, message -> {
                serverMessage.set(message);
                serverReceived.countDown();
            }, () -> {
            });

            Thread acceptThread = new Thread(() -> {
                try {
                    server.waitForClient();
                } catch (Exception e) {
                    serverError.set(e);
                } finally {
                    serverAccepted.countDown();
                }
            }, "hexchess-test-server-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            client.connect("127.0.0.1", server.localPort(), message -> {
                clientMessage.set(message);
                clientReceived.countDown();
            }, () -> {
            });
            assertTrue(serverAccepted.await(5, TimeUnit.SECONDS));
            assertNull(serverError.get());

            String join = HexChessProtocol.join("Black");
            client.send(join);
            assertTrue(serverReceived.await(5, TimeUnit.SECONDS));
            assertEquals(join, serverMessage.get());

            String state = HexChessProtocol.state(HexChessStateSnapshot.serialize(HexGameState.standard()));
            server.send(state);
            assertTrue(clientReceived.await(5, TimeUnit.SECONDS));
            assertEquals(state, clientMessage.get());
        } finally {
            client.close();
            server.close();
        }
    }

    private String moveMessage(String from, String to, String promotion, String enPassant) {
        return HexChessProtocol.MOVE + ":"
                + encode(from) + ":"
                + encode(to) + ":"
                + encode(promotion) + ":"
                + encode(enPassant);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
