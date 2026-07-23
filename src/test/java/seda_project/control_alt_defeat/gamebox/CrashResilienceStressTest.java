package seda_project.control_alt_defeat.gamebox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMove;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoard;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoardObject;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessStateSnapshot;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryStateSnapshot;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisStateSnapshot;

class CrashResilienceStressTest {

    private static final int STRESS_TIMEOUT_SECONDS = 20;

    @Test
    void everyMemoryConfigurationSurvivesRandomAndInvalidSelections() {
        assertTimeoutPreemptively(Duration.ofSeconds(STRESS_TIMEOUT_SECONDS), () -> {
            Random random = new Random(0x4D454D4F52594CL);

            for (int k = 1; k <= BoardVariant.MAX_CARDS; k++) {
                for (BoardVariant variant : BoardVariant.computeVariants(k)) {
                    GameModel model = new GameModel(variant);

                    for (int action = 0; action < 1_000 && !model.isGameOver(); action++) {
                        int cardIndex = random.nextInt(model.getCards().size() + 20) - 10;
                        GameModel.SelectResult result = model.selectCard(cardIndex);
                        if (result == GameModel.SelectResult.RESOLVED_MISMATCH) {
                            model.closeOpenCards();
                        }

                        if (action % 17 == 0) {
                            model = MemoryStateSnapshot.deserialize(MemoryStateSnapshot.serialize(model));
                        }
                    }

                    assertTrue(model.getCards().size() <= BoardVariant.MAX_CARDS);
                    assertTrue(model.getRows() * model.getCols() >= model.getCards().size());
                    model.selectCard(Integer.MIN_VALUE);
                    model.selectCard(Integer.MAX_VALUE);
                }
            }
        });
    }

    @Test
    void tetrisSurvivesThousandsOfActionsEffectsAndLanRoundTrips() {
        assertTimeoutPreemptively(Duration.ofSeconds(STRESS_TIMEOUT_SECONDS), () -> {
            Random random = new Random(0x5A45545249534CL);

            for (boolean horizontal : List.of(false, true)) {
                TetrisGameConfig config = new TetrisGameConfig(
                        List.of("Standard"),
                        List.of(),
                        320,
                        true,
                        horizontal);
                TetrisGameSetup setup = new TetrisGameSetup("Stress One", "Stress Two", config);
                TetrisGameState state = TetrisGameState.create(setup).running();

                for (int action = 0; action < 8_000; action++) {
                    if (state.isFinished()) {
                        state = TetrisGameState.create(setup).running();
                    }

                    PlayerSide side = random.nextBoolean() ? PlayerSide.BOTTOM : PlayerSide.TOP;
                    if (state.player(side).activePiece() == null && state.player(side).isPlaying()) {
                        List<PieceShape> shapes = config.availableShapes();
                        state = state.spawnPiece(side, shapes.get(random.nextInt(shapes.size())));
                    }

                    state = switch (random.nextInt(6)) {
                        case 0 -> state.moveLeft(side);
                        case 1 -> state.moveRight(side);
                        case 2 -> state.rotateClockwise(side);
                        case 3, 4 -> state.applyGravity(side);
                        default -> state.tickEffects();
                    };

                    if (action % 101 == 0 && state.player(side).activePiece() != null) {
                        BoardPosition objectPosition = state.player(side).activePiece().boardCells().stream()
                                .filter(state.player(side).board()::isInside)
                                .findFirst()
                                .orElse(null);
                        if (objectPosition != null) {
                            TetrisItemType type = TetrisItemType.values()[
                                    (action / 101) % TetrisItemType.values().length];
                            state = state.spawnObject(side, new TetrisBoardObject(type, objectPosition))
                                    .rotateClockwise(side);
                        }
                    }

                    if (action % 41 == 0) {
                        state = TetrisStateSnapshot.deserialize(
                                TetrisStateSnapshot.serialize(state, action * 100),
                                config);
                    }

                    assertTrue(state.bottomPlayer().board().rows() >= TetrisBoard.MIN_ROWS);
                    assertTrue(state.bottomPlayer().board().rows() <= TetrisBoard.MAX_ROWS);
                    assertTrue(state.bottomPlayer().board().columns() >= TetrisBoard.MIN_COLUMNS);
                    assertTrue(state.bottomPlayer().board().columns() <= TetrisBoard.MAX_COLUMNS);
                    assertTrue(state.topPlayer().board().rows() >= TetrisBoard.MIN_ROWS);
                    assertTrue(state.topPlayer().board().rows() <= TetrisBoard.MAX_ROWS);
                    assertTrue(state.topPlayer().board().columns() >= TetrisBoard.MIN_COLUMNS);
                    assertTrue(state.topPlayer().board().columns() <= TetrisBoard.MAX_COLUMNS);
                }
            }
        });
    }

    @Test
    void hexChessSurvivesLongRandomLegalGamesInvalidActionsAndLanRoundTrips() {
        assertTimeoutPreemptively(Duration.ofSeconds(STRESS_TIMEOUT_SECONDS), () -> {
            Random random = new Random(0x43484558534147L);
            HexMove impossibleMove = new HexMove(
                    HexCoordinate.of("a1"),
                    HexCoordinate.of("a1"),
                    null,
                    false);

            for (int game = 0; game < 12; game++) {
                HexGameState state = HexGameState.standard();

                for (int ply = 0; ply < 400 && state.isActive(); ply++) {
                    state = state.play(null);
                    state = state.play(impossibleMove);

                    List<HexMove> legalMoves = state.legalMovesForTurn();
                    assertFalse(legalMoves.isEmpty());
                    HexMove move = legalMoves.get(random.nextInt(legalMoves.size()));
                    state = state.play(move);

                    if (ply % 19 == 0 && state.isActive()) {
                        state = state.offerDraw(state.turn());
                        state = state.declineDraw(state.turn().opponent());
                    }

                    String snapshot = HexChessStateSnapshot.serialize(
                            state,
                            "Stress White",
                            "Stress Black");
                    HexChessStateSnapshot.MatchSnapshot restored =
                            HexChessStateSnapshot.deserializeMatch(snapshot);
                    state = restored.gameState();
                    assertNotNull(state.board());
                    assertNotNull(state.status());
                }
            }
        });
    }

    @Test
    void arbitraryMalformedSnapshotsFailFastWithoutHangingTheProcess() {
        assertTimeoutPreemptively(Duration.ofSeconds(STRESS_TIMEOUT_SECONDS), () -> {
            Random random = new Random(0x46555A5A4CL);
            String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                    + "|~;,:_-=%+/\\\n\r\t\u0000\uFFFF";

            for (int sample = 0; sample < 2_000; sample++) {
                int length = random.nextInt(2_049);
                StringBuilder payload = new StringBuilder(length);
                for (int index = 0; index < length; index++) {
                    payload.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
                String fuzz = payload.toString();

                tolerateRejectedSnapshot(() -> MemoryStateSnapshot.deserialize(fuzz));
                tolerateRejectedSnapshot(() -> TetrisStateSnapshot.deserialize(
                        fuzz,
                        TetrisGameConfig.defaultConfig()));
                tolerateRejectedSnapshot(() -> HexChessStateSnapshot.deserialize(fuzz));
            }
        });
    }

    private static void tolerateRejectedSnapshot(Runnable parser) {
        try {
            parser.run();
        } catch (RuntimeException expectedRejection) {
            // Malformed remote state is allowed to be rejected; hangs and VM errors are not.
        }
    }
}
