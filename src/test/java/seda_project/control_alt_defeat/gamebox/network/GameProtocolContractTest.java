package seda_project.control_alt_defeat.gamebox.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMove;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessProtocol;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryProtocol;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisProtocol;

class GameProtocolContractTest {

    @Test
    void snapshotCodecRoundTripsUnicodeWhitespaceAndSeparators() {
        String original = "Mönk: 神\nline two | ~ ;";

        assertEquals(original, SnapshotCodec.decode(SnapshotCodec.encode(original)));
        assertEquals("", SnapshotCodec.decode(SnapshotCodec.encode(null)));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.decode("%%%"));
    }

    @Test
    void snapshotCodecParsersAreStrictAndOfferDocumentedIntegerFallback() {
        assertEquals(-42, SnapshotCodec.parseInt("-42"));
        assertEquals(7, SnapshotCodec.parseInt("not-a-number", 7));
        assertEquals(7, SnapshotCodec.parseInt(null, 7));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.parseInt(" "));
        assertTrue(SnapshotCodec.parseBoolean("true"));
        assertFalse(SnapshotCodec.parseBoolean("false"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.parseBoolean("TRUE"));
    }

    @Test
    void networkMessagePreservesMaximumFieldCountAndArbitraryFieldText() {
        String[] fields = IntStream.range(0, NetworkMessage.MAX_FIELDS)
                .mapToObj(index -> "field:" + index + "\nü")
                .toArray(String[]::new);

        String message = NetworkMessage.make("STATE_2", fields);

        assertEquals("STATE_2", NetworkMessage.type(message));
        assertEquals(List.of(fields), NetworkMessage.fields(message));
        assertTrue(NetworkMessage.isType(message, "STATE_2"));
        assertEquals("", NetworkMessage.make("STATE", new String[NetworkMessage.MAX_FIELDS + 1]));
        assertEquals("", NetworkMessage.type("lowercase"));
    }

    @Test
    void memoryProtocolHelpersRoundTripEveryMessageShape() {
        assertMessage(MemoryProtocol.join("Alice: 神"), MemoryProtocol.JOIN, "Alice: 神");
        assertMessage(MemoryProtocol.start("Host", "Joiner", "snapshot|data"),
                MemoryProtocol.START, "Host", "Joiner", "snapshot|data");
        assertMessage(MemoryProtocol.flip(44), MemoryProtocol.FLIP, "44");
        assertMessage(MemoryProtocol.state("state"), MemoryProtocol.STATE, "state");
        assertMessage(MemoryProtocol.restartRequest("Alice"), MemoryProtocol.RESTART_REQUEST, "Alice");
        assertMessage(MemoryProtocol.restartState("state"), MemoryProtocol.RESTART_STATE, "state");
        assertMessage(MemoryProtocol.quit("Alice"), MemoryProtocol.QUIT, "Alice");
        assertMessage(MemoryProtocol.error("bad: input"), MemoryProtocol.ERROR, "bad: input");
    }

    @Test
    void tetrisProtocolCarriesConfigurationSideAndCommands() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"));
        String start = TetrisProtocol.start("Host", "Joiner", config);
        assertMessage(start, TetrisProtocol.START, "Host", "Joiner", config.serialize());
        assertMessage(TetrisProtocol.input(PlayerSide.TOP, TetrisProtocol.ROTATE),
                TetrisProtocol.INPUT, "TOP", TetrisProtocol.ROTATE);
        assertMessage(TetrisProtocol.input(null, TetrisProtocol.SOFT_DROP),
                TetrisProtocol.INPUT, "", TetrisProtocol.SOFT_DROP);
        assertMessage(TetrisProtocol.state("state"), TetrisProtocol.STATE, "state");
        assertMessage(TetrisProtocol.restartRequest("Player"), TetrisProtocol.RESTART_REQUEST, "Player");
        assertMessage(TetrisProtocol.restartState("state"), TetrisProtocol.RESTART_STATE, "state");
        assertMessage(TetrisProtocol.quit("Player"), TetrisProtocol.QUIT, "Player");
        assertMessage(TetrisProtocol.error("problem"), TetrisProtocol.ERROR, "problem");
    }

    @Test
    void hexProtocolRoundTripsNormalPromotionAndEnPassantMoves() {
        HexMove normal = new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b2"), null, false);
        HexMove promotion = new HexMove(
                HexCoordinate.of("f10"), HexCoordinate.of("f11"), HexPieceType.QUEEN, false);
        HexMove enPassant = new HexMove(HexCoordinate.of("f5"), HexCoordinate.of("g6"), null, true);

        assertEquals(normal, parseMoveMessage(HexChessProtocol.move(normal)));
        assertEquals(promotion, parseMoveMessage(HexChessProtocol.move(promotion)));
        assertEquals(enPassant, parseMoveMessage(HexChessProtocol.move(enPassant)));

        HexChessGameSetup setup = HexChessGameSetup.local();
        assertMessage(HexChessProtocol.start(setup, "snapshot"), HexChessProtocol.START,
                setup.whiteName(), setup.blackName(), "snapshot");
        assertMessage(HexChessProtocol.simple(HexChessProtocol.DRAW_OFFER), HexChessProtocol.DRAW_OFFER);
    }

    @Test
    void hexMoveParserRejectsMalformedOrForbiddenPromotionPayloads() {
        assertNull(HexChessProtocol.parseMove(null));
        assertNull(HexChessProtocol.parseMove(List.of("a1", "a2", "", "false", "extra")));
        assertNull(HexChessProtocol.parseMove(List.of("off", "a2", "", "false")));
        assertNull(HexChessProtocol.parseMove(List.of("a1", "a2", "KING", "false")));
        assertNull(HexChessProtocol.parseMove(List.of("a1", "a2", "QUEEN", "yes")));
    }

    private static HexMove parseMoveMessage(String message) {
        assertEquals(HexChessProtocol.MOVE, HexChessProtocol.type(message));
        return HexChessProtocol.parseMove(HexChessProtocol.fields(message));
    }

    private static void assertMessage(String message, String type, String... fields) {
        assertFalse(message.isBlank());
        assertEquals(type, NetworkMessage.type(message));
        assertEquals(List.of(fields), NetworkMessage.fields(message));
    }
}
