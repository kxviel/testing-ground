package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.List;

public enum HexDirection {
    NORTH(0, 1),
    NORTH_EAST(1, 0),
    SOUTH_EAST(1, -1),
    SOUTH(0, -1),
    SOUTH_WEST(-1, 0),
    NORTH_WEST(-1, 1),

    DIAGONAL_NORTH_EAST(1, 1),
    DIAGONAL_EAST(2, -1),
    DIAGONAL_SOUTH_EAST(1, -2),
    DIAGONAL_SOUTH_WEST(-1, -1),
    DIAGONAL_WEST(-2, 1),
    DIAGONAL_NORTH_WEST(-1, 2);

    private final int qDelta;
    private final int rDelta;

    HexDirection(int qDelta, int rDelta) {
        this.qDelta = qDelta;
        this.rDelta = rDelta;
    }

    public int qDelta() {
        return qDelta;
    }

    public int rDelta() {
        return rDelta;
    }

    public static List<HexDirection> rookDirections() {
        return List.of(NORTH, NORTH_EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, NORTH_WEST);
    }

    public static List<HexDirection> bishopDirections() {
        return List.of(
                DIAGONAL_NORTH_EAST,
                DIAGONAL_EAST,
                DIAGONAL_SOUTH_EAST,
                DIAGONAL_SOUTH_WEST,
                DIAGONAL_WEST,
                DIAGONAL_NORTH_WEST);
    }

    public static List<HexDirection> queenDirections() {
        return List.of(
                NORTH,
                NORTH_EAST,
                SOUTH_EAST,
                SOUTH,
                SOUTH_WEST,
                NORTH_WEST,
                DIAGONAL_NORTH_EAST,
                DIAGONAL_EAST,
                DIAGONAL_SOUTH_EAST,
                DIAGONAL_SOUTH_WEST,
                DIAGONAL_WEST,
                DIAGONAL_NORTH_WEST);
    }
}
