package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        double whiteScore,
        double blackScore) {

    public HexGameState {
        board = board == null ? HexBoard.standard() : board;
        turn = turn == null ? HexPieceColor.WHITE : turn;
        status = status == null ? HexGameStatus.RUNNING : status;
        statusMessage = statusMessage == null ? "" : statusMessage;
        repetitionCounts = repetitionCounts == null ? Map.of() : Map.copyOf(repetitionCounts);
    }

    public static HexGameState standard() {
        return create(HexBoard.standard(), HexPieceColor.WHITE);
    }

    public static HexGameState create(HexBoard board, HexPieceColor startingTurn) {
        HexBoard safeBoard = board == null ? HexBoard.standard() : board;
        HexPieceColor safeTurn = startingTurn == null ? HexPieceColor.WHITE : startingTurn;
        Map<String, Integer> repetitions = Map.of(HexPositionKey.from(safeBoard, safeTurn, null), 1);
        HexGameResolution resolution = HexGameEndDetector.evaluate(safeBoard, safeTurn, null, 0, repetitions);

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
                0,
                0);
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

        return HexLegalMoveValidator.legalMoves(board, color, enPassantTarget);
    }

    public List<HexMove> legalMovesFrom(HexCoordinate from) {
        Optional<HexPiece> piece = board.pieceAt(from);

        if (piece.isEmpty() || piece.get().color() != turn || !isActive()) {
            return List.of();
        }

        return HexLegalMoveValidator.legalMovesFrom(board, from, enPassantTarget);
    }

    public HexGameState play(HexMove requestedMove) {
        if (!isActive() || requestedMove == null) {
            return this;
        }

        Optional<HexMove> legalMove = legalMovesFrom(requestedMove.from())
                .stream()
                .filter(move -> HexMoveRules.sameMoveIntent(move, requestedMove))
                .findFirst();

        if (legalMove.isEmpty()) {
            return withMessage("Illegal move: " + requestedMove.notation());
        }

        return applyLegalMove(legalMove.get());
    }

    public HexGameState offerDraw() {
        return offerDraw(turn);
    }

    public HexGameState offerDraw(HexPieceColor player) {
        if (!isActive()) {
            return this;
        }

        HexPieceColor offerBy = player == null ? turn : player;
        return new HexGameState(
                board,
                turn,
                status,
                offerBy.displayName() + " offered a draw.",
                lastMove,
                enPassantTarget,
                offerBy,
                halfMoveClock,
                repetitionCounts,
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

        return new HexGameState(
                board,
                turn,
                status,
                "Draw offer declined.",
                lastMove,
                enPassantTarget,
                null,
                halfMoveClock,
                repetitionCounts,
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
        return new HexGameState(
                board,
                turn,
                HexGameStatus.RESIGNED,
                loser.displayName() + " resigned. " + winner.displayName() + " wins.",
                lastMove,
                enPassantTarget,
                null,
                halfMoveClock,
                repetitionCounts,
                winner == HexPieceColor.WHITE ? 1 : 0,
                winner == HexPieceColor.BLACK ? 1 : 0);
    }

    public boolean isInCheck(HexPieceColor color) {
        return HexLegalMoveValidator.isInCheck(board, color);
    }

    public boolean hasDrawOfferForTurn() {
        return hasDrawOfferFor(turn);
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

        Map<String, Integer> nextRepetitions = incrementRepetition(
                repetitionCounts,
                HexPositionKey.from(nextBoard, nextTurn, nextEnPassantTarget));
        HexMoveRecord nextLastMove = new HexMoveRecord(move, movingPiece, capturedPiece, capturedAt);
        HexPieceColor nextDrawOfferBy = drawOfferBy != null && movingPiece.color() == drawOfferBy
                ? drawOfferBy
                : null;

        return resolveAfterMove(
                nextBoard,
                nextTurn,
                nextLastMove,
                nextEnPassantTarget,
                nextHalfMoveClock,
                nextRepetitions,
                nextDrawOfferBy);
    }

    private HexGameState resolveAfterMove(
            HexBoard nextBoard,
            HexPieceColor nextTurn,
            HexMoveRecord nextLastMove,
            HexCoordinate nextEnPassantTarget,
            int nextHalfMoveClock,
            Map<String, Integer> nextRepetitions,
            HexPieceColor nextDrawOfferBy) {
        HexGameResolution resolution = HexGameEndDetector.evaluate(
                nextBoard,
                nextTurn,
                nextEnPassantTarget,
                nextHalfMoveClock,
                nextRepetitions);

        if (resolution.terminal()) {
            return new HexGameState(
                    nextBoard,
                    nextTurn,
                    resolution.status(),
                    resolution.message(),
                    nextLastMove,
                    nextEnPassantTarget,
                    null,
                    nextHalfMoveClock,
                    nextRepetitions,
                    resolution.whiteScore(),
                    resolution.blackScore());
        }

        return new HexGameState(
                nextBoard,
                nextTurn,
                resolution.status(),
                resolution.message(),
                nextLastMove,
                nextEnPassantTarget,
                nextDrawOfferBy,
                nextHalfMoveClock,
                nextRepetitions,
                whiteScore,
                blackScore);
    }

    private HexGameState drawn(String message) {
        return drawn(board, turn, lastMove, enPassantTarget, halfMoveClock, repetitionCounts, message);
    }

    private HexGameState drawn(
            HexBoard nextBoard,
            HexPieceColor nextTurn,
            HexMoveRecord nextLastMove,
            HexCoordinate nextEnPassantTarget,
            int nextHalfMoveClock,
            Map<String, Integer> nextRepetitions,
            String message) {
        return new HexGameState(
                nextBoard,
                nextTurn,
                HexGameStatus.DRAW,
                message,
                nextLastMove,
                nextEnPassantTarget,
                null,
                nextHalfMoveClock,
                nextRepetitions,
                0.5,
                0.5);
    }

    private HexGameState withMessage(String message) {
        return new HexGameState(
                board,
                turn,
                status,
                message,
                lastMove,
                enPassantTarget,
                drawOfferBy,
                halfMoveClock,
                repetitionCounts,
                whiteScore,
                blackScore);
    }

    private static Map<String, Integer> incrementRepetition(Map<String, Integer> counts, String key) {
        Map<String, Integer> next = new LinkedHashMap<>(counts);
        next.merge(key, 1, Integer::sum);
        return Map.copyOf(next);
    }
}
