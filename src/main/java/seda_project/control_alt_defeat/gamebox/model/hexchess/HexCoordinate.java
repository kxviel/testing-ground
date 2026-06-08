package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Objects;

public record HexCoordinate(char file, int rank) implements Comparable<HexCoordinate> {

    public HexCoordinate {
        file = Character.toLowerCase(file);

        if (rank < 1) {
            throw new IllegalArgumentException("Rank must be positive.");
        }
    }

    public static HexCoordinate of(String notation) {
        Objects.requireNonNull(notation, "notation");

        String value = notation.trim().toLowerCase();
        if (value.length() < 2) {
            throw new IllegalArgumentException("Invalid coordinate: " + notation);
        }

        char file = value.charAt(0);
        int rank = Integer.parseInt(value.substring(1));
        HexCoordinate coordinate = new HexCoordinate(file, rank);

        if (!HexBoardGeometry.isValid(coordinate)) {
            throw new IllegalArgumentException("Coordinate is outside the board: " + notation);
        }

        return coordinate;
    }

    public String notation() {
        return String.valueOf(file) + rank;
    }

    @Override
    public int compareTo(HexCoordinate other) {
        int fileCompare = Integer.compare(
                HexBoardGeometry.fileIndex(file),
                HexBoardGeometry.fileIndex(other.file));

        return fileCompare != 0 ? fileCompare : Integer.compare(rank, other.rank);
    }

    @Override
    public String toString() {
        return notation();
    }
}
