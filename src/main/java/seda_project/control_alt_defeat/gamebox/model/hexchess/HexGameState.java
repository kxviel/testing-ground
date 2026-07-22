package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import seda_project.control_alt_defeat.gamebox.util.SafeText;

public record HexGameState(
        HexBoard board,
        HexPieceColor turn,
        HexGameStatus status,
        String statusMessage,
        HexMoveRecord lastMove,
        HexCoordinate enPassantTarget,
        HexPieceColor drawOfferBy,
        int halfMoveClock,
        Map<String, Integer> repetitionCounts,
        Set<HexCoordinate> doubleMoveEligibleSquares,
        double whiteScore,
        double blackScore) {

    public static final int MAX_STATUS_MESSAGE_CHARS = 512;
    public static final int MAX_HALF_MOVE_CLOCK = 10_000;
    public static final int MAX_REPETITION_ENTRIES = 96;
    public static final int MAX_POSITION_KEY_CHARS = 768;
    public static final int MAX_REPETITION_COUNT = 1_000_000;

    public HexGameState {
        board = board == null ? HexBoard.standard() : board;
        turn = turn == null ? HexPieceColor.WHITE : turn;
        status = status == null ? HexGameStatus.RUNNING : status;
        statusMessage = SafeText.singleLine(statusMessage, "", MAX_STATUS_MESSAGE_CHARS);
        halfMoveClock = Math.min(MAX_HALF_MOVE_CLOCK, Math.max(0, halfMoveClock));
        Map<String, Integer> safeRepetitions = new LinkedHashMap<>();
        if (repetitionCounts != null) {
            repetitionCounts.forEach((key, count) -> {
                if (safeRepetitions.size() < MAX_REPETITION_ENTRIES
                        && key != null && !key.isBlank() && key.length() <= MAX_POSITION_KEY_CHARS
                        && count != null && count >= 1) {
                    safeRepetitions.put(key, Math.min(MAX_REPETITION_COUNT, count));
                }
            });
        }
        repetitionCounts = Map.copyOf(safeRepetitions);
        Set<HexCoordinate> safeDoubleMoves = new LinkedHashSet<>();
        if (doubleMoveEligibleSquares != null) {
            doubleMoveEligibleSquares.stream()
                    .filter(HexBoardGeometry::isValid)
                    .limit(HexBoard.MAX_PIECES)
                    .forEach(safeDoubleMoves::add);
        }
        doubleMoveEligibleSquares = Set.copyOf(safeDoubleMoves);
        whiteScore = safeScore(whiteScore);
        blackScore = safeScore(blackScore);
    }

    public static HexGameState standard() {
        return create(HexBoard.standard(), HexPieceColor.WHITE);
    }

    public static HexGameState create(HexBoard board, HexPieceColor startingTurn) {
        return create(board, startingTurn, true);
    }

    public static HexGameState create(HexBoard board, HexPieceColor startingTurn, boolean allowStandardDoubleMoves) {
        HexBoard safeBoard = board == null ? HexBoard.standard() : board;
        HexPieceColor safeTurn = startingTurn == null ? HexPieceColor.WHITE : startingTurn;
        Set<HexCoordinate> doubleMoveEligible = initialDoubleMoveEligibleSquares(
                safeBoard,
                allowStandardDoubleMoves);
        Map<String, Integer> repetitions = Map.of(
                HexPositionKey.from(safeBoard, safeTurn, null, doubleMoveEligible),
                1);
        HexGameResolution resolution = HexGameEndDetector.evaluate(
                safeBoard,
                safeTurn,
                null,
                0,
                repetitions,
                doubleMoveEligible);

        return new HexGameState(
                safeBoard,
                safeTurn,
                resolution.status(),
                resolution.message(),
                null,
                null,
                null,
                0,
                repetitions,
                doubleMoveEligible,
                resolution.whiteScore(),
                resolution.blackScore());
    }

    public boolean isActive() {
        return status == HexGameStatus.RUNNING || status == HexGameStatus.CHECK;
    }

    public List<HexMove> legalMovesForTurn() {
        return legalMoves(turn);
    }

    public List<HexMove> legalMoves(HexPieceColor color) {
        if (!isActive()) {
            return List.of();
        }

        return HexLegalMoveValidator.legalMoves(board, color, enPassantTarget, doubleMoveEligibleSquares);
    }

    public List<HexMove> legalMovesFrom(HexCoordinate from) {
        Optional<HexPiece> piece = board.pieceAt(from);

        if (!isActive() || piece.map(HexPiece::color).filter(turn::equals).isEmpty()) {
            return List.of();
        }

        return HexLegalMoveValidator.legalMovesFrom(board, from, enPassantTarget, doubleMoveEligibleSquares);
    }

    public HexGameState play(HexMove requestedMove) {
        if (!isActive() || requestedMove == null) {
            return this;
        }

        Optional<HexMove> legalMove = legalMovesFrom(requestedMove.from())
                .stream()
                .filter(move -> HexMoveRules.sameMoveIntent(move, requestedMove))
                .findFirst();

        return legalMove
                .map(this::applyLegalMove)
                .orElseGet(() -> withMessage("Illegal move: " + requestedMove.notation()));
    }

    public HexGameState offerDraw() {
        return offerDraw(turn);
    }

    public HexGameState offerDraw(HexPieceColor player) {
        if (!isActive()) {
            return this;
        }

        if (drawOfferBy != null) {
            return withStatusMessage("A draw offer is already pending.");
        }

        HexPieceColor offerBy = player == null ? turn : player;
        return copy(
                board,
                turn,
                status,
                offerBy.displayName() + " offered a draw.",
                lastMove,
                enPassantTarget,
                offerBy,
                halfMoveClock,
                repetitionCounts,
                doubleMoveEligibleSquares,
                whiteScore,
                blackScore);
    }

    public HexGameState acceptDraw() {
        return acceptDraw(turn);
    }

    public HexGameState acceptDraw(HexPieceColor player) {
        HexPieceColor acceptingPlayer = player == null ? turn : player;
        if (!isActive() || drawOfferBy == null) {
            return this;
        }

        if (drawOfferBy == acceptingPlayer) {
            return withMessage("Only the opponent can accept a draw offer.");
        }

        return drawn("Draw offer accepted.");
    }

    public HexGameState declineDraw() {
        return declineDraw(turn);
    }

    public HexGameState declineDraw(HexPieceColor player) {
        HexPieceColor decliningPlayer = player == null ? turn : player;
        if (drawOfferBy == null) {
            return this;
        }

        if (drawOfferBy == decliningPlayer) {
            return withMessage("Only the opponent can decline a draw offer.");
        }

        return copy(
                board,
                turn,
                status,
                "Draw offer declined.",
                lastMove,
                enPassantTarget,
                null,
                halfMoveClock,
                repetitionCounts,
                doubleMoveEligibleSquares,
                whiteScore,
                blackScore);
    }

    public HexGameState resign() {
        return resign(turn);
    }

    public HexGameState resign(HexPieceColor player) {
        if (!isActive()) {
            return this;
        }

        HexPieceColor loser = player == null ? turn : player;
        HexPieceColor winner = loser.opponent();
        return copy(
                board,
                turn,
                HexGameStatus.RESIGNED,
                loser.displayName() + " resigned. " + winner.displayName() + " wins.",
                lastMove,
                enPassantTarget,
                null,
                halfMoveClock,
                repetitionCounts,
                doubleMoveEligibleSquares,
                winner == HexPieceColor.WHITE ? 1 : 0,
                winner == HexPieceColor.BLACK ? 1 : 0);
    }

    public boolean isInCheck(HexPieceColor color) {
        return HexLegalMoveValidator.isInCheck(board, color);
    }

    public boolean hasDrawOfferFor(HexPieceColor player) {
        return drawOfferBy != null && drawOfferBy != player;
    }

    private HexGameState applyLegalMove(HexMove move) {
        HexPiece movingPiece = board.pieceAt(move.from())
                .orElseThrow(() -> new IllegalStateException("Legal move has no moving piece."));
        HexCoordinate capturedAt = move.enPassant()
                ? HexMoveRules.enPassantCapturedAt(move, movingPiece.color()).orElse(move.to())
                : move.to();
        HexPiece capturedPiece = board.pieceAt(capturedAt).orElse(null);
        HexPieceType promotion = HexMoveRules.promotionFor(move, movingPiece);
        HexBoard nextBoard = board.applyMove(move, capturedAt, promotion);
        HexPieceColor nextTurn = turn.opponent();
        HexCoordinate nextEnPassantTarget = HexMoveRules.nextEnPassantTarget(move, movingPiece).orElse(null);
        int nextHalfMoveClock = movingPiece.type() == HexPieceType.PAWN || capturedPiece != null
                ? 0
                : halfMoveClock + 1;

        HexMoveRecord nextLastMove = new HexMoveRecord(move, movingPiece, capturedPiece, capturedAt);
        Set<HexCoordinate> nextDoubleMoveEligibleSquares = updateDoubleMoveEligibility(move, capturedAt);
        Map<String, Integer> nextRepetitions = incrementRepetition(
                repetitionCounts,
                HexPositionKey.from(
                        nextBoard,
                        nextTurn,
                        nextEnPassantTarget,
                        nextDoubleMoveEligibleSquares));
        boolean drawOfferRevokedByMove = drawOfferBy != null
                && movingPiece.color() != drawOfferBy;
        HexPieceColor nextDrawOfferBy = drawOfferRevokedByMove ? null : drawOfferBy;

        return resolveAfterMove(
                nextBoard,
                nextTurn,
                nextLastMove,
                nextEnPassantTarget,
                nextHalfMoveClock,
                nextRepetitions,
                nextDoubleMoveEligibleSquares,
                nextDrawOfferBy,
                drawOfferRevokedByMove);
    }

    private HexGameState resolveAfterMove(
            HexBoard nextBoard,
            HexPieceColor nextTurn,
            HexMoveRecord nextLastMove,
            HexCoordinate nextEnPassantTarget,
            int nextHalfMoveClock,
            Map<String, Integer> nextRepetitions,
            Set<HexCoordinate> nextDoubleMoveEligibleSquares,
            HexPieceColor nextDrawOfferBy,
            boolean drawOfferRevokedByMove) {
        HexGameResolution resolution = HexGameEndDetector.evaluate(
                nextBoard,
                nextTurn,
                nextEnPassantTarget,
                nextHalfMoveClock,
                nextRepetitions,
                nextDoubleMoveEligibleSquares);
        String nextMessage = drawOfferRevokedByMove && !resolution.terminal()
                ? "Draw offer revoked after move. " + resolution.message()
                : resolution.message();

        if (resolution.terminal()) {
            return copy(
                    nextBoard,
                    nextTurn,
                    resolution.status(),
                    nextMessage,
                    nextLastMove,
                    nextEnPassantTarget,
                    null,
                    nextHalfMoveClock,
                    nextRepetitions,
                    nextDoubleMoveEligibleSquares,
                    resolution.whiteScore(),
                    resolution.blackScore());
        }

        return copy(
                nextBoard,
                nextTurn,
                resolution.status(),
                nextMessage,
                nextLastMove,
                nextEnPassantTarget,
                nextDrawOfferBy,
                nextHalfMoveClock,
                nextRepetitions,
                nextDoubleMoveEligibleSquares,
                whiteScore,
                blackScore);
    }

    private HexGameState drawn(String message) {
        return copy(
                board,
                turn,
                HexGameStatus.DRAW,
                message,
                lastMove,
                enPassantTarget,
                null,
                halfMoveClock,
                repetitionCounts,
                doubleMoveEligibleSquares,
                0.5,
                0.5);
    }

    public HexGameState withStatusMessage(String message) {
        return copy(
                board,
                turn,
                status,
                message,
                lastMove,
                enPassantTarget,
                drawOfferBy,
                halfMoveClock,
                repetitionCounts,
                doubleMoveEligibleSquares,
                whiteScore,
                blackScore);
    }

    public HexGameState disconnected(String message) {
        return terminal(HexGameStatus.DISCONNECTED, message);
    }

    public HexGameState failed(String message) {
        return terminal(HexGameStatus.ERROR, message);
    }

    private HexGameState terminal(HexGameStatus nextStatus, String message) {
        return copy(
                board,
                turn,
                nextStatus,
                message,
                lastMove,
                enPassantTarget,
                null,
                halfMoveClock,
                repetitionCounts,
                doubleMoveEligibleSquares,
                whiteScore,
                blackScore);
    }

    private HexGameState withMessage(String message) {
        return withStatusMessage(message);
    }

    private HexGameState copy(
            HexBoard board,
            HexPieceColor turn,
            HexGameStatus status,
            String statusMessage,
            HexMoveRecord lastMove,
            HexCoordinate enPassantTarget,
            HexPieceColor drawOfferBy,
            int halfMoveClock,
            Map<String, Integer> repetitionCounts,
            Set<HexCoordinate> doubleMoveEligibleSquares,
            double whiteScore,
            double blackScore) {
        return new HexGameState(
                board,
                turn,
                status,
                statusMessage,
                lastMove,
                enPassantTarget,
                drawOfferBy,
                halfMoveClock,
                repetitionCounts,
                doubleMoveEligibleSquares,
                whiteScore,
                blackScore);
    }

    private static Map<String, Integer> incrementRepetition(Map<String, Integer> counts, String key) {
        Map<String, Integer> next = new LinkedHashMap<>(counts);
        if (!next.containsKey(key) && next.size() >= MAX_REPETITION_ENTRIES) {
            next.remove(next.keySet().iterator().next());
        }
        next.merge(key, 1, (current, one) -> Math.min(MAX_REPETITION_COUNT, current + one));
        return Map.copyOf(next);
    }

    private Set<HexCoordinate> updateDoubleMoveEligibility(HexMove move, HexCoordinate capturedAt) {
        return HexMoveRules.updateDoubleMoveEligibility(doubleMoveEligibleSquares, move, capturedAt);
    }

    private static Set<HexCoordinate> initialDoubleMoveEligibleSquares(
            HexBoard board,
            boolean allowStandardDoubleMoves) {
        if (!allowStandardDoubleMoves) {
            return Set.of();
        }

        return HexMoveRules.initialDoubleMoveEligibleSquares(board);
    }

    private static double safeScore(double score) {
        return Double.isFinite(score) ? Math.min(1.0, Math.max(0.0, score)) : 0.0;
    }
}
