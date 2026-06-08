package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Map;

public final class HexPositionValidator {

    private static final int MAX_PAWNS_PER_SIDE = 9;

    private HexPositionValidator() {
    }

    public static HexPositionValidation validate(HexBoard board, HexPieceColor turn) {
        HexBoard safeBoard = board == null ? HexBoard.empty() : board;
        HexPieceColor safeTurn = turn == null ? HexPieceColor.WHITE : turn;

        HexPositionValidation kingValidation = validateKings(safeBoard);
        if (!kingValidation.isValid()) {
            return kingValidation;
        }

        HexPositionValidation pawnValidation = validatePawnCounts(safeBoard);
        if (!pawnValidation.isValid()) {
            return pawnValidation;
        }

        HexGameState state = HexGameState.create(safeBoard, safeTurn);
        if (state.isInCheck(safeTurn.opponent())) {
            return HexPositionValidation.invalid(
                    safeTurn.opponent().displayName() + " cannot already be in check when "
                            + safeTurn.displayName() + " is to move.");
        }

        if (state.legalMovesForTurn().isEmpty()) {
            return HexPositionValidation.invalid(safeTurn.displayName() + " must have at least one legal move.");
        }

        return HexPositionValidation.valid();
    }

    private static HexPositionValidation validateKings(HexBoard board) {
        long whiteKings = count(board, HexPieceColor.WHITE, HexPieceType.KING);
        long blackKings = count(board, HexPieceColor.BLACK, HexPieceType.KING);

        if (whiteKings != 1 || blackKings != 1) {
            return HexPositionValidation.invalid("Custom positions need exactly one king for each side.");
        }

        HexCoordinate whiteKing = board.kingPosition(HexPieceColor.WHITE).orElseThrow();
        HexCoordinate blackKing = board.kingPosition(HexPieceColor.BLACK).orElseThrow();
        boolean kingsTouch = HexDirection.queenDirections()
                .stream()
                .map(direction -> HexBoardGeometry.neighbor(whiteKing, direction))
                .flatMap(java.util.Optional::stream)
                .anyMatch(blackKing::equals);

        return kingsTouch
                ? HexPositionValidation.invalid("Kings cannot stand next to each other.")
                : HexPositionValidation.valid();
    }

    private static HexPositionValidation validatePawnCounts(HexBoard board) {
        boolean tooManyWhitePawns = count(board, HexPieceColor.WHITE, HexPieceType.PAWN) > MAX_PAWNS_PER_SIDE;
        boolean tooManyBlackPawns = count(board, HexPieceColor.BLACK, HexPieceType.PAWN) > MAX_PAWNS_PER_SIDE;

        return tooManyWhitePawns || tooManyBlackPawns
                ? HexPositionValidation.invalid("A side cannot have more than 9 pawns.")
                : HexPositionValidation.valid();
    }

    private static long count(HexBoard board, HexPieceColor color, HexPieceType type) {
        return board.pieces()
                .entrySet()
                .stream()
                .filter(entry -> samePiece(entry, color, type))
                .count();
    }

    private static boolean samePiece(
            Map.Entry<HexCoordinate, HexPiece> entry,
            HexPieceColor color,
            HexPieceType type) {
        return entry.getValue().color() == color && entry.getValue().type() == type;
    }
}
