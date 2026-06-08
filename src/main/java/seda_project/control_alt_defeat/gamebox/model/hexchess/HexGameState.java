package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    private static final int FIFTY_MOVE_RULE_PLY = 100;
    private static final List<HexCoordinate> WHITE_PAWN_STARTS = coordinates(
            "b1", "c2", "d3", "e4", "f5", "g4", "h3", "i2", "k1");
    private static final List<HexCoordinate> BLACK_PAWN_STARTS = coordinates(
            "b7", "c7", "d7", "e7", "f7", "g7", "h7", "i7", "k7");
    private static final List<Jump> WHITE_PAWN_ATTACKS = List.of(new Jump(-1, 2), new Jump(1, 1));
    private static final List<Jump> BLACK_PAWN_ATTACKS = List.of(new Jump(-1, -1), new Jump(1, -2));
    private static final List<Jump> KNIGHT_JUMPS = createKnightJumps();

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
        Map<String, Integer> repetitions = Map.of(positionKey(safeBoard, safeTurn, null), 1);
        boolean check = new HexGameState(
                safeBoard,
                safeTurn,
                HexGameStatus.RUNNING,
                "",
                null,
                null,
                null,
                0,
                repetitions,
                0,
                0).isInCheck(safeTurn);

        return new HexGameState(
                safeBoard,
                safeTurn,
                check ? HexGameStatus.CHECK : HexGameStatus.RUNNING,
                check ? safeTurn.displayName() + " is in check." : safeTurn.displayName() + " to move.",
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

        return legalMovesFor(board, color, enPassantTarget);
    }

    public List<HexMove> legalMovesFrom(HexCoordinate from) {
        Optional<HexPiece> piece = board.pieceAt(from);

        if (piece.isEmpty() || piece.get().color() != turn || !isActive()) {
            return List.of();
        }

        return pseudoMoves(board, from, enPassantTarget, false)
                .stream()
                .filter(move -> keepsOwnKingSafe(board, move, piece.get().color()))
                .sorted(Comparator.comparing(move -> move.to().notation()))
                .toList();
    }

    public HexGameState play(HexMove requestedMove) {
        if (!isActive() || requestedMove == null) {
            return this;
        }

        Optional<HexMove> legalMove = legalMovesFrom(requestedMove.from())
                .stream()
                .filter(move -> sameMoveIntent(move, requestedMove))
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
        if (!isActive() || drawOfferBy == null) {
            return this;
        }

        return drawn("Draw offer accepted.");
    }

    public HexGameState declineDraw() {
        if (drawOfferBy == null) {
            return this;
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
        return isInCheck(board, color);
    }

    public boolean hasDrawOfferForTurn() {
        return drawOfferBy != null && drawOfferBy != turn;
    }

    private HexGameState applyLegalMove(HexMove move) {
        HexPiece movingPiece = board.pieceAt(move.from())
                .orElseThrow(() -> new IllegalStateException("Legal move has no moving piece."));
        HexCoordinate capturedAt = move.enPassant()
                ? enPassantCapturedAt(move, movingPiece.color()).orElse(move.to())
                : move.to();
        HexPiece capturedPiece = board.pieceAt(capturedAt).orElse(null);
        HexPieceType promotion = promotionFor(move, movingPiece);
        HexBoard nextBoard = board.applyMove(move, capturedAt, promotion);
        HexPieceColor nextTurn = turn.opponent();
        HexCoordinate nextEnPassantTarget = nextEnPassantTarget(move, movingPiece).orElse(null);
        int nextHalfMoveClock = movingPiece.type() == HexPieceType.PAWN || capturedPiece != null
                ? 0
                : halfMoveClock + 1;

        Map<String, Integer> nextRepetitions = incrementRepetition(
                repetitionCounts,
                positionKey(nextBoard, nextTurn, nextEnPassantTarget));
        HexMoveRecord nextLastMove = new HexMoveRecord(move, movingPiece, capturedPiece, capturedAt);

        return resolveAfterMove(
                nextBoard,
                nextTurn,
                nextLastMove,
                nextEnPassantTarget,
                nextHalfMoveClock,
                nextRepetitions);
    }

    private HexGameState resolveAfterMove(
            HexBoard nextBoard,
            HexPieceColor nextTurn,
            HexMoveRecord nextLastMove,
            HexCoordinate nextEnPassantTarget,
            int nextHalfMoveClock,
            Map<String, Integer> nextRepetitions) {
        boolean check = isInCheck(nextBoard, nextTurn);
        List<HexMove> nextLegalMoves = legalMovesFor(nextBoard, nextTurn, nextEnPassantTarget);
        HexPieceColor mover = nextTurn.opponent();

        if (check && nextLegalMoves.isEmpty()) {
            return new HexGameState(
                    nextBoard,
                    nextTurn,
                    HexGameStatus.CHECKMATE,
                    "Checkmate. " + mover.displayName() + " wins.",
                    nextLastMove,
                    nextEnPassantTarget,
                    null,
                    nextHalfMoveClock,
                    nextRepetitions,
                    mover == HexPieceColor.WHITE ? 1 : 0,
                    mover == HexPieceColor.BLACK ? 1 : 0);
        }

        if (!check && nextLegalMoves.isEmpty()) {
            return new HexGameState(
                    nextBoard,
                    nextTurn,
                    HexGameStatus.STALEMATE,
                    "Stalemate. " + mover.displayName() + " gets 0.75 points.",
                    nextLastMove,
                    nextEnPassantTarget,
                    null,
                    nextHalfMoveClock,
                    nextRepetitions,
                    mover == HexPieceColor.WHITE ? 0.75 : 0.25,
                    mover == HexPieceColor.BLACK ? 0.75 : 0.25);
        }

        if (nextHalfMoveClock >= FIFTY_MOVE_RULE_PLY) {
            return drawn(nextBoard, nextTurn, nextLastMove, nextEnPassantTarget, nextHalfMoveClock, nextRepetitions,
                    "Draw by 50-move rule.");
        }

        if (nextRepetitions.values().stream().anyMatch(count -> count >= 3)) {
            return drawn(nextBoard, nextTurn, nextLastMove, nextEnPassantTarget, nextHalfMoveClock, nextRepetitions,
                    "Draw by threefold repetition.");
        }

        if (hasKingsOnly(nextBoard)) {
            return drawn(nextBoard, nextTurn, nextLastMove, nextEnPassantTarget, nextHalfMoveClock, nextRepetitions,
                    "Draw by insufficient material.");
        }

        return new HexGameState(
                nextBoard,
                nextTurn,
                check ? HexGameStatus.CHECK : HexGameStatus.RUNNING,
                check ? nextTurn.displayName() + " is in check." : nextTurn.displayName() + " to move.",
                nextLastMove,
                nextEnPassantTarget,
                null,
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

    private static List<HexMove> legalMovesFor(
            HexBoard board,
            HexPieceColor color,
            HexCoordinate enPassantTarget) {
        return board.piecesOf(color)
                .flatMap(entry -> pseudoMoves(board, entry.getKey(), enPassantTarget, false)
                        .stream()
                        .filter(move -> keepsOwnKingSafe(board, move, color)))
                .toList();
    }

    private static boolean keepsOwnKingSafe(HexBoard board, HexMove move, HexPieceColor color) {
        HexCoordinate capturedAt = move.enPassant()
                ? enPassantCapturedAt(move, color).orElse(move.to())
                : move.to();
        HexPiece movingPiece = board.pieceAt(move.from()).orElse(null);
        HexPieceType promotion = promotionFor(move, movingPiece);
        HexBoard nextBoard = board.applyMove(move, capturedAt, promotion);

        return !isInCheck(nextBoard, color);
    }

    private static boolean isInCheck(HexBoard board, HexPieceColor color) {
        return board.kingPosition(color)
                .filter(king -> isAttacked(board, king, color.opponent()))
                .isPresent();
    }

    private static boolean isAttacked(HexBoard board, HexCoordinate target, HexPieceColor byColor) {
        return board.piecesOf(byColor)
                .flatMap(entry -> pseudoMoves(board, entry.getKey(), null, true).stream())
                .anyMatch(move -> move.to().equals(target));
    }

    private static List<HexMove> pseudoMoves(
            HexBoard board,
            HexCoordinate from,
            HexCoordinate enPassantTarget,
            boolean attacksOnly) {
        HexPiece piece = board.pieceAt(from).orElse(null);
        if (piece == null) {
            return List.of();
        }

        return switch (piece.type()) {
            case KING -> stepMoves(board, from, HexDirection.queenDirections(), piece.color(), attacksOnly);
            case QUEEN -> slidingMoves(board, from, HexDirection.queenDirections(), piece.color(), attacksOnly);
            case ROOK -> slidingMoves(board, from, HexDirection.rookDirections(), piece.color(), attacksOnly);
            case BISHOP -> slidingMoves(board, from, HexDirection.bishopDirections(), piece.color(), attacksOnly);
            case KNIGHT -> jumpMoves(board, from, KNIGHT_JUMPS, piece.color(), attacksOnly);
            case PAWN -> pawnMoves(board, from, piece.color(), enPassantTarget, attacksOnly);
            case CUSTOM -> List.of();
        };
    }

    private static List<HexMove> slidingMoves(
            HexBoard board,
            HexCoordinate from,
            List<HexDirection> directions,
            HexPieceColor color,
            boolean attacksOnly) {
        return directions.stream()
                .flatMap(direction -> slidingMovesInDirection(board, from, direction, color, attacksOnly).stream())
                .toList();
    }

    private static List<HexMove> slidingMovesInDirection(
            HexBoard board,
            HexCoordinate from,
            HexDirection direction,
            HexPieceColor color,
            boolean attacksOnly) {
        List<HexMove> moves = new java.util.ArrayList<>();

        for (HexCoordinate target : HexBoardGeometry.ray(from, direction)) {
            Optional<HexPiece> targetPiece = board.pieceAt(target);

            if (targetPiece.isEmpty()) {
                moves.add(new HexMove(from, target));
                continue;
            }

            if (attacksOnly || targetPiece.get().color() != color) {
                moves.add(new HexMove(from, target));
            }
            break;
        }

        return moves;
    }

    private static List<HexMove> stepMoves(
            HexBoard board,
            HexCoordinate from,
            List<HexDirection> directions,
            HexPieceColor color,
            boolean attacksOnly) {
        return directions.stream()
                .map(direction -> HexBoardGeometry.neighbor(from, direction))
                .flatMap(Optional::stream)
                .filter(canLandOn(board, color, attacksOnly))
                .map(target -> new HexMove(from, target))
                .toList();
    }

    private static List<HexMove> jumpMoves(
            HexBoard board,
            HexCoordinate from,
            List<Jump> jumps,
            HexPieceColor color,
            boolean attacksOnly) {
        return jumps.stream()
                .map(jump -> HexBoardGeometry.shift(from, jump.qDelta(), jump.rDelta()))
                .flatMap(Optional::stream)
                .filter(canLandOn(board, color, attacksOnly))
                .map(target -> new HexMove(from, target))
                .toList();
    }

    private static Predicate<HexCoordinate> canLandOn(HexBoard board, HexPieceColor color, boolean attacksOnly) {
        return target -> attacksOnly
                || board.pieceAt(target)
                .map(piece -> piece.color() != color)
                .orElse(true);
    }

    private static List<HexMove> pawnMoves(
            HexBoard board,
            HexCoordinate from,
            HexPieceColor color,
            HexCoordinate enPassantTarget,
            boolean attacksOnly) {
        List<Jump> attacks = color == HexPieceColor.WHITE ? WHITE_PAWN_ATTACKS : BLACK_PAWN_ATTACKS;

        if (attacksOnly) {
            return attacks.stream()
                    .map(jump -> HexBoardGeometry.shift(from, jump.qDelta(), jump.rDelta()))
                    .flatMap(Optional::stream)
                    .map(target -> new HexMove(from, target))
                    .toList();
        }

        Stream<HexMove> captures = attacks.stream()
                .map(jump -> HexBoardGeometry.shift(from, jump.qDelta(), jump.rDelta()))
                .flatMap(Optional::stream)
                .filter(target -> isEnemyPiece(board, target, color)
                        || target.equals(enPassantTarget))
                .map(target -> new HexMove(
                        from,
                        target,
                        promotionAt(target, color).orElse(null),
                        target.equals(enPassantTarget)));

        Stream<HexMove> forwardMoves = forwardPawnMoves(board, from, color);

        return Stream.concat(forwardMoves, captures).toList();
    }

    private static Stream<HexMove> forwardPawnMoves(HexBoard board, HexCoordinate from, HexPieceColor color) {
        HexDirection forward = color == HexPieceColor.WHITE ? HexDirection.NORTH : HexDirection.SOUTH;
        Optional<HexCoordinate> oneStep = HexBoardGeometry.neighbor(from, forward)
                .filter(board::isEmpty);

        Stream<HexMove> singleMove = oneStep.stream()
                .map(target -> new HexMove(from, target, promotionAt(target, color).orElse(null), false));

        Stream<HexMove> doubleMove = oneStep
                .filter(ignored -> isPawnStart(from, color))
                .flatMap(target -> HexBoardGeometry.neighbor(target, forward))
                .filter(board::isEmpty)
                .map(target -> new HexMove(from, target))
                .stream();

        return Stream.concat(singleMove, doubleMove);
    }

    private static Optional<HexPieceType> promotionAt(HexCoordinate target, HexPieceColor color) {
        return HexBoardGeometry.isPromotionSquare(target, color)
                ? Optional.of(HexPieceType.QUEEN)
                : Optional.empty();
    }

    private static boolean isEnemyPiece(HexBoard board, HexCoordinate target, HexPieceColor color) {
        return board.pieceAt(target)
                .map(piece -> piece.color() != color)
                .orElse(false);
    }

    private static boolean isPawnStart(HexCoordinate coordinate, HexPieceColor color) {
        return color == HexPieceColor.WHITE
                ? WHITE_PAWN_STARTS.contains(coordinate)
                : BLACK_PAWN_STARTS.contains(coordinate);
    }

    private static Optional<HexCoordinate> nextEnPassantTarget(HexMove move, HexPiece movingPiece) {
        if (movingPiece.type() != HexPieceType.PAWN) {
            return Optional.empty();
        }

        AxialCoordinate from = HexBoardGeometry.axial(move.from());
        AxialCoordinate to = HexBoardGeometry.axial(move.to());

        if (Math.abs(to.r() - from.r()) != 2 || to.q() != from.q()) {
            return Optional.empty();
        }

        int step = movingPiece.color() == HexPieceColor.WHITE ? 1 : -1;
        return HexBoardGeometry.shift(move.from(), 0, step);
    }

    private static Optional<HexCoordinate> enPassantCapturedAt(HexMove move, HexPieceColor color) {
        int behind = color == HexPieceColor.WHITE ? -1 : 1;
        return HexBoardGeometry.shift(move.to(), 0, behind);
    }

    private static HexPieceType promotionFor(HexMove move, HexPiece piece) {
        if (piece == null || piece.type() != HexPieceType.PAWN) {
            return null;
        }

        return move.promotion();
    }

    private static boolean sameMoveIntent(HexMove legalMove, HexMove requestedMove) {
        return legalMove.from().equals(requestedMove.from())
                && legalMove.to().equals(requestedMove.to())
                && (requestedMove.promotion() == null || requestedMove.promotion() == legalMove.promotion());
    }

    private static boolean hasKingsOnly(HexBoard board) {
        return board.pieces()
                .values()
                .stream()
                .allMatch(piece -> piece.type() == HexPieceType.KING);
    }

    private static Map<String, Integer> incrementRepetition(Map<String, Integer> counts, String key) {
        Map<String, Integer> next = new LinkedHashMap<>(counts);
        next.merge(key, 1, Integer::sum);
        return Map.copyOf(next);
    }

    private static String positionKey(HexBoard board, HexPieceColor turn, HexCoordinate enPassantTarget) {
        String pieces = board.pieces()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().notation()
                        + entry.getValue().color().name().charAt(0)
                        + entry.getValue().type().symbol())
                .collect(Collectors.joining("/"));

        return pieces + "|" + turn.name() + "|" + (enPassantTarget == null ? "-" : enPassantTarget.notation());
    }

    private static List<Jump> createKnightJumps() {
        List<HexDirection> directions = HexDirection.rookDirections();

        return IntStream.range(0, directions.size())
                .boxed()
                .flatMap(index -> {
                    HexDirection forward = directions.get(index);
                    HexDirection left = directions.get((index + 1) % directions.size());
                    HexDirection right = directions.get((index + directions.size() - 1) % directions.size());

                    return Stream.of(
                            new Jump(
                                    2 * forward.qDelta() + left.qDelta(),
                                    2 * forward.rDelta() + left.rDelta()),
                            new Jump(
                                    2 * forward.qDelta() + right.qDelta(),
                                    2 * forward.rDelta() + right.rDelta()));
                })
                .distinct()
                .toList();
    }

    private static List<HexCoordinate> coordinates(String... notations) {
        return Stream.of(notations)
                .map(HexCoordinate::of)
                .toList();
    }
}

record Jump(int qDelta, int rDelta) {
}
