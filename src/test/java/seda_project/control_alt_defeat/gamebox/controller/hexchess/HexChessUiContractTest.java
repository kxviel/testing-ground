package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoard;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPiece;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveViewport;

class HexChessUiContractTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(latch::countDown);
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void liveMatchUsesPlayerAwareTurnsAndOnlyShowsSpecialStatusMessages() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(
                    HexChessUiContractTest.class.getResource("/hexchess/HexChessGame.fxml"));
            Parent root = loader.load();
            layout(root, 1920, 1000);

            HexChessGameController controller = loader.getController();
            controller.setRouteData(new HexChessGameSetup("Alice", "Bob", HexGameMode.LOCAL));
            root.applyCss();
            root.layout();

            Label turn = (Label) loader.getNamespace().get("turnLabel");
            Label turnDetail = (Label) loader.getNamespace().get("turnDetailLabel");
            Label status = (Label) loader.getNamespace().get("statusLabel");
            Label lastMove = (Label) loader.getNamespace().get("lastMoveLabel");
            VBox guide = (VBox) loader.getNamespace().get("boardGuideSection");
            Canvas canvas = (Canvas) loader.getNamespace().get("boardCanvas");
            ScrollPane info = (ScrollPane) loader.getNamespace().get("infoScrollPane");

            assertEquals("Alice", turn.getText());
            assertEquals("White to move", turnDetail.getText());
            assertEquals("", status.getText());
            assertFalse(status.isVisible());
            assertFalse(status.isManaged());
            assertEquals("No moves yet", lastMove.getText());
            assertTrue(guide.isVisible());
            assertTrue(guide.lookupAll(".board-guide-legal").size() == 1);
            assertTrue(guide.lookupAll(".board-guide-last-move").size() == 1);
            assertTrue(guide.lookupAll(".board-guide-check").size() == 1);
            assertTrue(guide.lookupAll(".board-guide-promotion").size() == 1);
            double fittedHexSize = Math.min(
                    canvas.getWidth() / HexChessCanvasBoard.boardWidth(1.0),
                    canvas.getHeight() / HexChessCanvasBoard.boardHeight(1.0));
            double boardLeft = canvas.getLayoutX()
                    + (canvas.getWidth() - HexChessCanvasBoard.boardWidth(fittedHexSize)) / 2.0;
            assertTrue(guide.getBoundsInParent().getMaxX() + 8.0 <= boardLeft,
                    "The board guide must remain outside the playable cells");
            assertFalse(info.lookupAll(".scroll-bar").stream()
                    .filter(ScrollBar.class::isInstance)
                    .map(ScrollBar.class::cast)
                    .anyMatch(bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL
                            && bar.isVisible()),
                    "The gameplay sidebar should not need vertical scrolling at 1920x1000");

            HexBoard checkBoard = HexBoard.empty()
                    .withPiece(HexCoordinate.of("f6"),
                            new HexPiece(HexPieceColor.WHITE, HexPieceType.KING))
                    .withPiece(HexCoordinate.of("g6"),
                            new HexPiece(HexPieceColor.BLACK, HexPieceType.QUEEN))
                    .withPiece(HexCoordinate.of("g10"),
                            new HexPiece(HexPieceColor.BLACK, HexPieceType.KING));
            controller.setRouteData(new HexChessGameSetup(
                    "Alice",
                    "Bob",
                    HexGameMode.LOCAL,
                    checkBoard,
                    HexPieceColor.WHITE,
                    true));

            assertEquals("Alice", turn.getText());
            assertEquals("White to move", turnDetail.getText());
            assertEquals("White is in check.", status.getText());
            assertTrue(status.isVisible());
            assertTrue(status.isManaged());
        });
    }

    @Test
    void customBuilderUsesClearLabelsAndSafeControlStates() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(
                    HexChessUiContractTest.class.getResource("/hexchess/HexChessSetup.fxml"));
            Parent root = loader.load();
            layout(root, 1366, 768);

            Label instruction = (Label) loader.getNamespace().get("setupInstructionLabel");
            Label pieceColor = (Label) loader.getNamespace().get("pieceColorLabel");
            Label pieceType = (Label) loader.getNamespace().get("pieceTypeLabel");
            Label selected = (Label) loader.getNamespace().get("selectedLabel");
            Label sideToMove = (Label) loader.getNamespace().get("sideToMoveLabel");
            Label validation = (Label) loader.getNamespace().get("validationLabel");
            Button remove = (Button) loader.getNamespace().get("removePieceButton");
            Button standard = (Button) loader.getNamespace().get("loadStandardButton");
            Button clear = (Button) loader.getNamespace().get("clearBoardButton");
            @SuppressWarnings("unchecked")
            ComboBox<HexPieceColor> color = (ComboBox<HexPieceColor>) loader.getNamespace().get("colorChoiceBox");
            @SuppressWarnings("unchecked")
            ComboBox<HexPieceType> type = (ComboBox<HexPieceType>) loader.getNamespace().get("typeChoiceBox");
            @SuppressWarnings("unchecked")
            ComboBox<HexPieceColor> turn = (ComboBox<HexPieceColor>) loader.getNamespace().get("turnChoiceBox");

            assertEquals(
                    "Select a piece and click a cell to place it. Right-click a cell or use Remove Piece to erase it.",
                    instruction.getText());
            assertTrue(instruction.isWrapText());
            assertEquals("Piece color", pieceColor.getText());
            assertEquals("Piece type", pieceType.getText());
            assertEquals("Selected cell: none", selected.getText());
            assertEquals("Side to move", sideToMove.getText());
            assertEquals("Load Standard Position", standard.getText());
            assertEquals("Clear Board", clear.getText());
            assertTrue(remove.isDisabled());
            assertEquals("White", color.getConverter().toString(HexPieceColor.WHITE));
            assertEquals("Black", turn.getConverter().toString(HexPieceColor.BLACK));
            assertEquals("King", type.getConverter().toString(HexPieceType.KING));
            assertEquals("Knight", type.getConverter().toString(HexPieceType.KNIGHT));
            assertTrue(validation.getMaxWidth() > 1_000_000.0);
            assertTrue(validation.isWrapText());
        });
    }

    @Test
    void legalMoveMarkerAddsAColorIndependentCenterDot() throws Exception {
        runOnFxThread(() -> {
            Canvas canvas = new Canvas();
            HexChessCanvasBoard board = new HexChessCanvasBoard(20.0, 400.0, 400.0, 10.0, 25.0);
            board.attach(canvas, ignored -> { });
            board.drawLegalMoveMarker(
                    canvas.getGraphicsContext2D(),
                    HexCoordinate.of("f6"),
                    false);

            WritableImage image = canvas.snapshot(null, null);
            Color actual = image.getPixelReader().getColor(200, 200);
            Color expected = Color.web("#176b45");
            assertEquals(expected.getRed(), actual.getRed(), 0.02);
            assertEquals(expected.getGreen(), actual.getGreen(), 0.02);
            assertEquals(expected.getBlue(), actual.getBlue(), 0.02);
            assertTrue(actual.getOpacity() > 0.98);
        });
    }

    private static void layout(Parent content, double width, double height) {
        ResponsiveViewport viewport = new ResponsiveViewport(content);
        Scene scene = new Scene(viewport, width, height);
        scene.getStylesheets().add(
                HexChessUiContractTest.class.getResource("/Theme.css").toExternalForm());
        viewport.resize(width, height);
        for (int i = 0; i < 4; i++) {
            viewport.applyCss();
            viewport.layout();
            content.applyCss();
            content.layout();
        }
    }

    private static void runOnFxThread(ThrowingRunnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
