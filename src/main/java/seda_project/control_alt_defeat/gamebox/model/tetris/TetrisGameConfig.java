package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record TetrisGameConfig(
        List<String> pieces,
        List<PieceShape> customPieces,
        int gravityMillis,
        boolean dualPieces,
        boolean horizontalMode) {

    public static final int DEFAULT_GRAVITY_MILLIS = 550;
    public static final int GRAVITY_RAMP_INTERVAL_MILLIS = 15_000;
    public static final int GRAVITY_RAMP_STEP_MILLIS = 20;
    public static final int MIN_DYNAMIC_GRAVITY_MILLIS = 80;
    private static final int MIN_GRAVITY_MILLIS = 180;
    private static final int MAX_GRAVITY_MILLIS = 1_100;

    public TetrisGameConfig {
        pieces = normalizePieces(pieces);
        customPieces = customPieces == null ? List.of() : List.copyOf(customPieces);
        gravityMillis = clampGravity(gravityMillis);
    }

    public TetrisGameConfig(List<String> pieces, List<PieceShape> customPieces) {
        this(pieces, customPieces, DEFAULT_GRAVITY_MILLIS, false, false);
    }

    public TetrisGameConfig(List<String> pieces) {
        this(pieces, List.of(), DEFAULT_GRAVITY_MILLIS, false, false);
    }

    public static TetrisGameConfig defaultConfig() {
        return new TetrisGameConfig(List.of("Standard"), List.of(), DEFAULT_GRAVITY_MILLIS, false, false);
    }

    public boolean hasPieces() {
        return pieces != null && !pieces.isEmpty();
    }

    public String displayText() {
        String text = String.join(", ", pieces);

        if (customPieces.isEmpty()) {
            return text + optionsText();
        }

        return text + " (" + customPieces.size() + " custom)" + optionsText();
    }

    public List<PieceShape> availableShapes() {
        List<PieceShape> shapes = selectedShapes();

        if (!dualPieces) {
            return horizontalMode
                    ? shapes.stream().map(PieceShape::rotateClockwise90).toList()
                    : shapes;
        }

        return IntStream.range(0, shapes.size())
                .mapToObj(index -> combineDualShapes(
                        shapes.get(index),
                        shapes.get((index + 1) % shapes.size()),
                        horizontalMode))
                .toList();
    }

    public GravityDirection gravityDirection(PlayerSide side) {
        if (!horizontalMode) {
            return GravityDirection.DOWN;
        }

        return side == PlayerSide.TOP ? GravityDirection.LEFT : GravityDirection.RIGHT;
    }

    public int gravityMillisAtElapsed(int elapsedMillis) {
        int safeElapsedMillis = Math.max(0, elapsedMillis);
        int rampSteps = safeElapsedMillis / GRAVITY_RAMP_INTERVAL_MILLIS;
        int rampedGravity = gravityMillis - (rampSteps * GRAVITY_RAMP_STEP_MILLIS);
        return Math.max(MIN_DYNAMIC_GRAVITY_MILLIS, rampedGravity);
    }

    public String serialize() {
        String pieceText = String.join(",", pieces);
        String customText = customPieces.stream()
                .map(this::serializeShape)
                .collect(Collectors.joining(";"));
        String optionsText = gravityMillis + "," + dualPieces + "," + horizontalMode;

        return pieceText + "~" + customText + "~" + optionsText;
    }

    public static TetrisGameConfig deserialize(String value) {
        if (value == null || value.isBlank()) {
            return defaultConfig();
        }

        String[] sections = value.split("~", -1);
        List<String> pieces = normalizePieces(Arrays.stream(sections[0].split(","))
                .map(String::trim)
                .filter(piece -> !piece.isEmpty())
                .toList());
        List<PieceShape> customPieces = sections.length >= 2 ? parseCustomPieces(sections[1]) : List.of();
        ConfigOptions options = sections.length >= 3 ? parseOptions(sections[2]) : ConfigOptions.defaults();

        return pieces.isEmpty()
                ? defaultConfig()
                : new TetrisGameConfig(
                        pieces,
                        customPieces,
                        options.gravityMillis(),
                        options.dualPieces(),
                        options.horizontalMode());
    }

    private static List<String> normalizePieces(List<String> pieces) {
        if (pieces == null) {
            return List.of();
        }

        return pieces.stream()
                .map(piece -> piece == null ? "" : piece.trim())
                .filter(piece -> piece.equals("Standard") || piece.equals("Custom"))
                .distinct()
                .toList();
    }

    private String optionsText() {
        List<String> options = Stream.of(
                        "Speed " + gravityMillis + "ms",
                        dualPieces ? "dual" : "",
                        horizontalMode ? "horizontal" : "")
                .filter(option -> !option.isBlank())
                .toList();

        return options.isEmpty() ? "" : " - " + String.join(", ", options);
    }

    private static PieceShape combineDualShapes(PieceShape first, PieceShape second, boolean horizontalMode) {
        int boardColumns = horizontalMode ? TetrisBoard.HORIZONTAL_COLUMNS : TetrisBoard.DEFAULT_COLUMNS;
        int offset = Math.min(boardColumns - second.width(), first.width() + 1);
        if (offset <= first.cells().stream().mapToInt(BoardPosition::column).max().orElse(0)) {
            offset = first.width();
        }

        int safeOffset = Math.max(0, offset);
        List<BoardPosition> cells = Stream.concat(
                        first.cells().stream(),
                        second.cells().stream()
                                .map(cell -> new BoardPosition(cell.row(), cell.column() + safeOffset)))
                .distinct()
                .toList();

        PieceShape combined = new PieceShape(
                PieceType.CUSTOM,
                "Dual " + first.name() + "+" + second.name(),
                cells);
        return horizontalMode ? combined.rotateClockwise90() : combined;
    }

    private String serializeShape(PieceShape shape) {
        return shape.cells().stream()
                .map(cell -> cell.row() + "." + cell.column())
                .collect(Collectors.joining("_"));
    }

    private static List<PieceShape> parseCustomPieces(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<List<BoardPosition>> shapes = Arrays.stream(value.split(";"))
                .map(TetrisGameConfig::parseCells)
                .filter(cells -> !cells.isEmpty())
                .toList();

        return IntStream.range(0, shapes.size())
                .mapToObj(index -> CustomPieceBuilder.build("Custom " + (index + 1), shapes.get(index)))
                .toList();
    }

    private List<PieceShape> selectedShapes() {
        List<PieceShape> selected = Stream.concat(
                        pieces.contains("Standard") ? PieceShape.standardShapes().stream() : Stream.empty(),
                        pieces.contains("Custom") ? customPieces.stream() : Stream.empty())
                .toList();

        return selected.isEmpty() ? PieceShape.standardShapes() : selected;
    }

    private static List<BoardPosition> parseCells(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split("_"))
                .map(cellValue -> cellValue.split("\\.", 2))
                .filter(parts -> parts.length == 2)
                .map(parts -> parseBoardPosition(parts[0], parts[1]))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static ConfigOptions parseOptions(String value) {
        if (value == null || value.isBlank()) {
            return ConfigOptions.defaults();
        }

        String[] values = value.split(",", -1);
        return new ConfigOptions(
                values.length >= 1 ? parseInt(values[0], DEFAULT_GRAVITY_MILLIS) : DEFAULT_GRAVITY_MILLIS,
                values.length >= 2 && Boolean.parseBoolean(values[1]),
                values.length >= 3 && Boolean.parseBoolean(values[2]));
    }

    private static int clampGravity(int value) {
        return Math.min(MAX_GRAVITY_MILLIS, Math.max(MIN_GRAVITY_MILLIS, value));
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static BoardPosition parseBoardPosition(String row, String column) {
        try {
            return new BoardPosition(Integer.parseInt(row), Integer.parseInt(column));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ConfigOptions(int gravityMillis, boolean dualPieces, boolean horizontalMode) {
        private static ConfigOptions defaults() {
            return new ConfigOptions(DEFAULT_GRAVITY_MILLIS, false, false);
        }
    }
}
