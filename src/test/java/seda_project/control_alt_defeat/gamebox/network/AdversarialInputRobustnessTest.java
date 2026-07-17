package seda_project.control_alt_defeat.gamebox.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoard;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessStateSnapshot;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryStateSnapshot;
import seda_project.control_alt_defeat.gamebox.util.SafeText;

class AdversarialInputRobustnessTest {

    @Test
    void networkFramingRejectsInvalidTypesTooManyFieldsAndOversizedOutput() {
        assertEquals("", NetworkMessage.type("bad-type:value"));

        String colonFlood = "STATE:" + IntStream.range(0, NetworkMessage.MAX_FIELDS + 1)
                .mapToObj(index -> SnapshotCodec.encode("x"))
                .collect(Collectors.joining(":"));
        assertTrue(NetworkMessage.fields(colonFlood).isEmpty());

        assertEquals("", NetworkMessage.make("STATE", "x".repeat(NetworkMessage.MAX_MESSAGE_CHARS)));
        assertEquals("", NetworkMessage.make("bad-type", "value"));
    }

    @Test
    void oversizedInboundLineDisconnectsWithoutInvokingMessageHandler() throws Exception {
        GameServer server = new GameServer();
        CountDownLatch disconnected = new CountDownLatch(1);
        CountDownLatch accepted = new CountDownLatch(1);
        AtomicBoolean handlerInvoked = new AtomicBoolean();
        server.listen(0, message -> {
            handlerInvoked.set(true);
        }, disconnected::countDown);

        Thread acceptThread = new Thread(() -> {
            try {
                server.waitForClient();
            } catch (IOException ignored) {
            } finally {
                accepted.countDown();
            }
        }, "oversized-frame-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        try (Socket rawClient = new Socket("127.0.0.1", server.localPort());
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(rawClient.getOutputStream(), StandardCharsets.UTF_8), true)) {
            assertTrue(accepted.await(5, TimeUnit.SECONDS));
            writer.println("X".repeat(NetworkMessage.MAX_MESSAGE_CHARS + 1));
            assertTrue(disconnected.await(5, TimeUnit.SECONDS));
            assertFalse(server.isConnected());
            assertFalse(handlerInvoked.get());
        } finally {
            server.close();
        }
    }

    @Test
    void memorySnapshotRejectsOverflowCountsAndUnknownImages() {
        assertThrows(IllegalArgumentException.class,
                () -> MemoryStateSnapshot.deserialize("2147483647|2|1|2|0|0|0|false|"));

        String cards = "0," + SnapshotCodec.encode("not/a/real/image.png") + ",false,false,-1;"
                + "1," + SnapshotCodec.encode("not/a/real/image.png") + ",false,false,-1";
        assertThrows(IllegalArgumentException.class,
                () -> MemoryStateSnapshot.deserialize("2|1|1|2|0|0|0|false|" + cards));

        GameModel model = new GameModel(2, 1, 1, 2, List.of("A", "A"), 0);
        assertDoesNotThrow(() -> model.selectCard(Integer.MAX_VALUE));
    }

    @Test
    void tetrisBoundsPreventHugeAllocationsAndUnboundedEffects() {
        TetrisBoard board = new TetrisBoard(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(TetrisBoard.MAX_ROWS, board.rows());
        assertEquals(TetrisBoard.MAX_COLUMNS, board.columns());

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> board.destroyRadius(new BoardPosition(0, 0), Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> new PieceShape(PieceType.CUSTOM, "bad", List.of(new BoardPosition(-1, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> new PieceShape(PieceType.CUSTOM, "empty", List.of()));

        List<PieceShape> tooManyShapes = IntStream.range(0, 100)
                .mapToObj(index -> PieceShape.standardShape(PieceType.O))
                .toList();
        TetrisGameConfig config = new TetrisGameConfig(List.of("Custom"), tooManyShapes);
        assertEquals(TetrisGameConfig.MAX_CUSTOM_PIECES, config.customPieces().size());
    }

    @Test
    void hexSnapshotRejectsOversizedTextAndRepetitionHistory() {
        String[] fields = HexChessStateSnapshot.serialize(HexGameState.standard()).split("~", -1);
        fields[3] = SnapshotCodec.encode("x".repeat(HexGameState.MAX_STATUS_MESSAGE_CHARS + 1));
        String oversizedStatus = String.join("~", fields);
        assertThrows(IllegalArgumentException.class,
                () -> HexChessStateSnapshot.deserialize(oversizedStatus));

        fields = HexChessStateSnapshot.serialize(HexGameState.standard()).split("~", -1);
        fields[11] = IntStream.range(0, HexGameState.MAX_REPETITION_ENTRIES + 1)
                .mapToObj(index -> SnapshotCodec.encode("position-" + index) + "=1")
                .collect(Collectors.joining(";"));
        String oversizedHistory = String.join("~", fields);
        assertThrows(IllegalArgumentException.class,
                () -> HexChessStateSnapshot.deserialize(oversizedHistory));
    }

    @Test
    void textSanitizerHandlesNegativeLimitsAndControlCharacters() {
        assertEquals("", SafeText.singleLine("abc", "fallback", -1));
        assertEquals("a b", SafeText.singleLine("a\n\tb", "", 20));
    }
}
