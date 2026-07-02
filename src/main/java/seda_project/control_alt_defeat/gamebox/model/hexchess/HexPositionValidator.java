package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Map;
import java.util.List;
import java.util.stream.Stream;

public final class HexPositionValidator {

    private static final Map<HexPieceType, Integer> STARTING_COUNTS = Map.of(
            HexPieceType.KING, 1,
            HexPieceType.QUEEN, 1,
            HexPieceType.ROOK, 2,
            HexPieceType.BISHOP, 3,
            HexPieceType.KNIGHT, 2,
            HexPieceType.PAWN, HexMoveRules.MAX_PAWNS_PER_SIDE);
    private static final List<HexPieceType> PROMOTION_TYPES = List.of(
            HexPieceType.QUEEN,
            HexPieceType.ROOK,
            HexPieceType.BISHOP,
            HexPieceType.KNIGHT);

    private HexPositionValidator() {
    }

    public static HexPositionValidation validate(HexBoard board, HexPieceColor turn) {
        HexBoard safeBoard = board == null ? HexBoard.empty() : board;
        HexPieceColor safeTurn = turn == null ? HexPieceColor.WHITE : turn;
        List<String> structuralErrors = Stream.of(
                        validateBoardCoordinates(safeBoard),
                        validateKings(safeBoard),
                        validatePawnCounts(safeBoard),
                        validatePawnLocations(safeBoard),
                        validatePieceCounts(safeBoard))
                .flatMap(List::stream)
                .toList();

        if (!structuralErrors.isEmpty()) {
            return HexPositionValidation.invalid(structuralErrors);
        }

        HexGameState state = HexGameState.create(safeBoard, safeTurn, false);
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

    private static List<String> validateBoardCoordinates(HexBoard board) {
        List<String> invalidCoordinates = board.pieces()
                .keySet()
                .stream()
                .filter(coordinate -> !HexBoardGeometry.isValid(coordinate))
                .map(HexCoordinate::notation)
                .toList();

        return invalidCoordinates.isEmpty()
                ? List.of()
                : List.of("Board contains off-board coordinates: " + String.join(", ", invalidCoordinates) + ".");
    }

    private static List<String> validateKings(HexBoard board) {
        long whiteKings = count(board, HexPieceColor.WHITE, HexPieceType.KING);
        long blackKings = count(board, HexPieceColor.BLACK, HexPieceType.KING);
        List<String> kingCountErrors = Stream.of(
                        whiteKings == 1 ? "" : "White needs exactly one king; found " + whiteKings + ".",
                        blackKings == 1 ? "" : "Black needs exactly one king; found " + blackKings + ".")
                .filter(message -> !message.isBlank())
                .toList();

        if (!kingCountErrors.isEmpty()) {
            return kingCountErrors;
        }

        HexCoordinate whiteKing = board.kingPosition(HexPieceColor.WHITE).orElseThrow();
        HexCoordinate blackKing = board.kingPosition(HexPieceColor.BLACK).orElseThrow();
        boolean kingsTouch = HexDirection.queenDirections()
                .stream()
                .map(direction -> HexBoardGeometry.neighbor(whiteKing, direction))
                .flatMap(java.util.Optional::stream)
                .anyMatch(blackKing::equals);

        return kingsTouch
                ? List.of("Kings cannot stand next to each other.")
                : List.of();
    }

    private static List<String> validatePawnCounts(HexBoard board) {
        return Stream.of(HexPieceColor.WHITE, HexPieceColor.BLACK)
                .filter(color -> count(board, color, HexPieceType.PAWN) > HexMoveRules.MAX_PAWNS_PER_SIDE)
                .map(color -> color.displayName() + " has too many pawns; maximum is "
                        + HexMoveRules.MAX_PAWNS_PER_SIDE + ".")
                .toList();
    }

    private static List<String> validatePawnLocations(HexBoard board) {
        return board.pieces()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().type() == HexPieceType.PAWN)
                .filter(entry -> HexBoardGeometry.isPromotionSquare(entry.getKey(), entry.getValue().color()))
                .map(entry -> entry.getValue().color().displayName()
                        + " pawn cannot start on promotion square " + entry.getKey().notation() + ".")
                .toList();
    }

    private static List<String> validatePieceCounts(HexBoard board) {
        return Stream.of(HexPieceColor.WHITE, HexPieceColor.BLACK)
                .flatMap(color -> validatePieceCounts(board, color).stream())
                .toList();
    }

    private static List<String> validatePieceCounts(HexBoard board, HexPieceColor color) {
        List<String> totalCountErrors = count(board, color) > HexMoveRules.MAX_PIECES_PER_SIDE
                ? List.of(color.displayName() + " has too many pieces; maximum is "
                        + HexMoveRules.MAX_PIECES_PER_SIDE + ".")
                : List.of();

        long pawns = count(board, color, HexPieceType.PAWN);
        long missingPawns = Math.max(0, HexMoveRules.MAX_PAWNS_PER_SIDE - pawns);
        List<String> promotedTypeErrors = PROMOTION_TYPES.stream()
                .filter(type -> count(board, color, type) > STARTING_COUNTS.get(type) + missingPawns)
                .map(type -> color.displayName() + " has too many " + displayPlural(type)
                        + "; maximum is " + (STARTING_COUNTS.get(type) + missingPawns)
                        + " with " + pawns + " pawns on the board.")
                .toList();

        long promotedExcess = PROMOTION_TYPES.stream()
                .mapToLong(type -> Math.max(0, count(board, color, type) - STARTING_COUNTS.get(type)))
                .sum();
        List<String> promotedTotalErrors = promotedExcess > missingPawns
                ? List.of(color.displayName() + " has too many promoted pieces; maximum is "
                        + missingPawns + " with " + pawns + " pawns on the board.")
                : List.of();

        return Stream.of(totalCountErrors, promotedTypeErrors, promotedTotalErrors)
                .flatMap(List::stream)
                .toList();
    }

    private static long count(HexBoard board, HexPieceColor color, HexPieceType type) {
        return board.pieces()
                .entrySet()
                .stream()
                .filter(entry -> samePiece(entry, color, type))
                .count();
    }

    private static long count(HexBoard board, HexPieceColor color) {
        return board.piecesOf(color).count();
    }

    private static boolean samePiece(
            Map.Entry<HexCoordinate, HexPiece> entry,
            HexPieceColor color,
            HexPieceType type) {
        return entry.getValue().color() == color && entry.getValue().type() == type;
    }

    private static String displayPlural(HexPieceType type) {
        return switch (type) {
            case QUEEN -> "queens";
            case ROOK -> "rooks";
            case BISHOP -> "bishops";
            case KNIGHT -> "knights";
            default -> type.name().toLowerCase() + "s";
        };
    }
}
