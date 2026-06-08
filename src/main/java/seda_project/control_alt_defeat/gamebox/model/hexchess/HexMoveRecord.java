package seda_project.control_alt_defeat.gamebox.model.hexchess;

public record HexMoveRecord(
        HexMove move,
        HexPiece movedPiece,
        HexPiece capturedPiece,
        HexCoordinate capturedAt) {

    public boolean isCapture() {
        return capturedPiece != null;
    }
}
