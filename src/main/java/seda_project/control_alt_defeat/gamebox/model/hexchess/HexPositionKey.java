package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Map;
import java.util.stream.Collectors;

public final class HexPositionKey {

    private HexPositionKey() {
    }

    public static String from(HexBoard board, HexPieceColor turn, HexCoordinate enPassantTarget) {
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
}
