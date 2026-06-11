package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Comparator;
import java.util.List;

public final class HexLegalMoveValidator {

    private HexLegalMoveValidator() {
    }

    public static List<HexMove> legalMoves(
            HexBoard board,
            HexPieceColor color,
            HexCoordinate enPassantTarget) {
        return board.piecesOf(color)
                .flatMap(entry -> legalMovesFrom(board, entry.getKey(), enPassantTarget).stream())
                .toList();
    }

    public static List<HexMove> legalMovesFrom(
            HexBoard board,
            HexCoordinate from,
            HexCoordinate enPassantTarget) {
        HexPiece piece = board.pieceAt(from).orElse(null);
        if (piece == null) {
            return List.of();
        }

        return HexMoveGenerator.pseudoLegalMovesFrom(board, from, enPassantTarget, false)
                .stream()
                .filter(move -> keepsOwnKingSafe(board, move, piece.color()))
                .sorted(Comparator.comparing(move -> move.to().notation()))
                .toList();
    }

    public static boolean isInCheck(HexBoard board, HexPieceColor color) {
        return board.kingPosition(color)
                .filter(king -> isAttacked(board, king, color.opponent()))
                .isPresent();
    }

    public static boolean isAttacked(HexBoard board, HexCoordinate target, HexPieceColor byColor) {
        return board.piecesOf(byColor)
                .flatMap(entry -> HexMoveGenerator.pseudoLegalMovesFrom(board, entry.getKey(), null, true).stream())
                .anyMatch(move -> move.to().equals(target));
    }

    private static boolean keepsOwnKingSafe(HexBoard board, HexMove move, HexPieceColor color) {
        HexCoordinate capturedAt = move.enPassant()
                ? HexMoveRules.enPassantCapturedAt(move, color).orElse(move.to())
                : move.to();
        HexPiece movingPiece = board.pieceAt(move.from()).orElse(null);
        HexPieceType promotion = HexMoveRules.promotionFor(move, movingPiece);
        HexBoard nextBoard = board.applyMove(move, capturedAt, promotion);

        return !isInCheck(nextBoard, color);
    }
}
