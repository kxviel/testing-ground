package seda_project.control_alt_defeat.gamebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.Card;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;

class ProjectResourceIntegrityTest {

    private static final List<String> REQUIRED_SCREENS = List.of(
            "/GameChoice.fxml",
            "/memory/MemoryMenu.fxml",
            "/memory/GameBoard.fxml",
            "/tetris/TetrisMenu.fxml",
            "/tetris/TetrisGame.fxml",
            "/hexchess/HexChessMenu.fxml",
            "/hexchess/HexChessGame.fxml",
            "/hexchess/HexChessSetup.fxml");

    @Test
    void allMemorySymbolsHaveDistinctNonEmptyImagePayloads() throws Exception {
        GameModel model = new GameModel(new BoardVariant(1, 45, "All Icons"));
        List<String> symbols = model.getCards().stream().map(Card::getSymbol).distinct().toList();
        Set<String> hashes = new HashSet<>();

        assertEquals(45, symbols.size());
        for (String symbol : symbols) {
            byte[] bytes = read("/icons/" + symbol);
            assertTrue(bytes.length > 100, () -> "Empty or truncated memory icon: " + symbol);
            assertTrue(hashes.add(hex(MessageDigest.getInstance("SHA-256").digest(bytes))),
                    () -> "Duplicate memory icon payload: " + symbol);
        }
        assertEquals(45, hashes.size());
    }

    @Test
    void allChessPieceImagesArePresentPngFiles() throws Exception {
        for (String color : List.of("W", "B")) {
            for (String piece : List.of("King", "Queen", "Rook", "Bishop", "Knight", "Pawn")) {
                String resource = "/icons/chess_pieces/" + color + "_" + piece + ".png";
                byte[] bytes = read(resource);
                assertTrue(bytes.length > 100, () -> "Empty chess image: " + resource);
                assertEquals((byte) 0x89, bytes[0], () -> "Not a PNG: " + resource);
                assertEquals((byte) 'P', bytes[1], () -> "Not a PNG: " + resource);
                assertEquals((byte) 'N', bytes[2], () -> "Not a PNG: " + resource);
                assertEquals((byte) 'G', bytes[3], () -> "Not a PNG: " + resource);
            }
        }
    }

    @Test
    void everyRoutedScreenAndSharedVisualResourceIsPackaged() throws Exception {
        for (String resource : REQUIRED_SCREENS) {
            assertTrue(read(resource).length > 0, () -> "Empty screen resource: " + resource);
        }
        for (String resource : List.of(
                "/Theme.css", "/Dialog.css", "/fonts/SourceSans3-Regular.ttf",
                "/fonts/SourceSans3-Bold.ttf", "/icons/game-icon.png",
                "/sounds/click.wav", "/sounds/game-start.wav")) {
            assertTrue(read(resource).length > 0, () -> "Missing shared resource: " + resource);
        }
    }

    private static byte[] read(String resource) throws Exception {
        try (InputStream stream = ProjectResourceIntegrityTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, () -> "Missing resource: " + resource);
            return stream.readAllBytes();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
