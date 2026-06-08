package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public final class HexBoardGeometry {

    public static final int RADIUS = 5;
    public static final List<Character> FILES = List.of('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'k', 'l');

    private static final Map<Character, Integer> FILE_INDEX = IntStream.range(0, FILES.size())
            .boxed()
            .collect(toMap(FILES::get, Function.identity()));

    private static final List<HexCoordinate> COORDINATES = FILES.stream()
            .flatMap(file -> IntStream.rangeClosed(1, fileLength(file))
                    .mapToObj(rank -> new HexCoordinate(file, rank)))
            .sorted()
            .toList();

    private static final Map<HexCoordinate, AxialCoordinate> AXIAL_BY_COORDINATE = COORDINATES.stream()
            .collect(toMap(Function.identity(), HexBoardGeometry::toAxialUnchecked));

    private static final Map<AxialCoordinate, HexCoordinate> COORDINATE_BY_AXIAL = AXIAL_BY_COORDINATE.entrySet()
            .stream()
            .collect(toMap(Map.Entry::getValue, Map.Entry::getKey));

    private HexBoardGeometry() {
    }

    public static List<HexCoordinate> coordinates() {
        return COORDINATES;
    }

    public static boolean isValid(HexCoordinate coordinate) {
        return coordinate != null
                && FILE_INDEX.containsKey(coordinate.file())
                && coordinate.rank() >= 1
                && coordinate.rank() <= fileLength(coordinate.file());
    }

    public static int fileIndex(char file) {
        return Optional.ofNullable(FILE_INDEX.get(Character.toLowerCase(file)))
                .orElseThrow(() -> new IllegalArgumentException("Unknown file: " + file));
    }

    public static int fileLength(char file) {
        int q = fileIndex(file) - RADIUS;
        return maxRankForQ(q);
    }

    public static HexCellTone tone(HexCoordinate coordinate) {
        AxialCoordinate axial = axial(coordinate);
        return switch (Math.floorMod(axial.q() - axial.r(), 3)) {
            case 0 -> HexCellTone.LIGHT;
            case 1 -> HexCellTone.MID;
            default -> HexCellTone.DARK;
        };
    }

    public static Optional<HexCoordinate> neighbor(HexCoordinate coordinate, HexDirection direction) {
        return coordinateAt(axial(coordinate).move(direction));
    }

    public static List<HexCoordinate> ray(HexCoordinate start, HexDirection direction) {
        return Stream.iterate(
                        axial(start).move(direction),
                        HexBoardGeometry::isInside,
                        axial -> axial.move(direction))
                .map(HexBoardGeometry::coordinateAt)
                .flatMap(Optional::stream)
                .toList();
    }

    public static Optional<HexCoordinate> shift(HexCoordinate start, int qDelta, int rDelta) {
        AxialCoordinate axial = axial(start);
        return coordinateAt(new AxialCoordinate(axial.q() + qDelta, axial.r() + rDelta));
    }

    public static HexCoordinate opposite(HexCoordinate coordinate) {
        AxialCoordinate axial = axial(coordinate);
        return coordinateAt(new AxialCoordinate(-axial.q(), -axial.r()))
                .orElseThrow(() -> new IllegalStateException("Opposite coordinate is outside the board."));
    }

    public static int axialQ(HexCoordinate coordinate) {
        return axial(coordinate).q();
    }

    public static int axialR(HexCoordinate coordinate) {
        return axial(coordinate).r();
    }

    public static boolean isPromotionSquare(HexCoordinate coordinate, HexPieceColor color) {
        return color == HexPieceColor.WHITE
                ? coordinate.rank() == fileLength(coordinate.file())
                : coordinate.rank() == 1;
    }

    public static List<HexCoordinate> displayOrder() {
        return COORDINATES.stream()
                .sorted(Comparator.comparingInt((HexCoordinate coordinate) -> -axial(coordinate).r())
                        .thenComparingInt(coordinate -> axial(coordinate).q()))
                .toList();
    }

    static AxialCoordinate axial(HexCoordinate coordinate) {
        AxialCoordinate axial = AXIAL_BY_COORDINATE.get(coordinate);
        if (axial == null) {
            throw new IllegalArgumentException("Coordinate is outside the board: " + coordinate);
        }
        return axial;
    }

    static Optional<HexCoordinate> coordinateAt(AxialCoordinate axial) {
        return Optional.ofNullable(COORDINATE_BY_AXIAL.get(axial));
    }

    private static boolean isInside(AxialCoordinate axial) {
        int s = -axial.q() - axial.r();
        return Math.abs(axial.q()) <= RADIUS
                && Math.abs(axial.r()) <= RADIUS
                && Math.abs(s) <= RADIUS;
    }

    private static AxialCoordinate toAxialUnchecked(HexCoordinate coordinate) {
        int q = fileIndex(coordinate.file()) - RADIUS;
        return new AxialCoordinate(q, minRForQ(q) + coordinate.rank() - 1);
    }

    private static int minRForQ(int q) {
        return Math.max(-RADIUS, -q - RADIUS);
    }

    private static int maxRForQ(int q) {
        return Math.min(RADIUS, -q + RADIUS);
    }

    private static int maxRankForQ(int q) {
        return maxRForQ(q) - minRForQ(q) + 1;
    }
}

record AxialCoordinate(int q, int r) {
    AxialCoordinate move(HexDirection direction) {
        return new AxialCoordinate(q + direction.qDelta(), r + direction.rDelta());
    }
}
