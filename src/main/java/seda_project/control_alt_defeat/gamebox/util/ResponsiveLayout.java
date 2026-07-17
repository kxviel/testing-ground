package seda_project.control_alt_defeat.gamebox.util;

import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;

/**
 * Applies the shared desktop and compact arrangements used by two-panel screens.
 */
public final class ResponsiveLayout {

    public static final double COMPACT_BREAKPOINT = 1_100.0;
    private static final double COMPACT_GAP = 20.0;
    private static final String COMPACT_STYLE_CLASS = "responsive-stacked";

    private ResponsiveLayout() {
    }

    public static boolean isCompact(double width) {
        return width < COMPACT_BREAKPOINT;
    }

    public static void bindTwoColumnGrid(GridPane grid, double primaryColumnPercent) {
        if (grid == null) {
            throw new IllegalArgumentException("grid must not be null");
        }
        if (primaryColumnPercent <= 0.0 || primaryColumnPercent >= 100.0) {
            throw new IllegalArgumentException("primaryColumnPercent must be between 0 and 100");
        }

        Node primary = findChildInColumn(grid, 0);
        Node secondary = findChildInColumn(grid, 1);
        if (primary == null || secondary == null) {
            throw new IllegalArgumentException("grid must contain children in columns 0 and 1");
        }

        double desktopHorizontalGap = grid.getHgap();
        double desktopVerticalGap = grid.getVgap();
        double desktopPrefHeight = grid.getPrefHeight();
        boolean[] compact = {false};

        Runnable update = () -> {
            boolean shouldCompact = isCompact(grid.getWidth());
            if (compact[0] == shouldCompact && !grid.getColumnConstraints().isEmpty()) {
                return;
            }
            compact[0] = shouldCompact;
            applyLayout(grid, primary, secondary, primaryColumnPercent,
                    desktopHorizontalGap, desktopVerticalGap, desktopPrefHeight, shouldCompact);
        };

        grid.widthProperty().addListener((ignored, previous, current) -> update.run());
        update.run();
    }

    private static Node findChildInColumn(GridPane grid, int column) {
        return grid.getChildren().stream()
                .filter(child -> columnIndex(child) == column)
                .findFirst()
                .orElse(null);
    }

    private static int columnIndex(Node child) {
        Integer column = GridPane.getColumnIndex(child);
        return column == null ? 0 : column;
    }

    private static void applyLayout(GridPane grid,
                                    Node primary,
                                    Node secondary,
                                    double primaryColumnPercent,
                                    double desktopHorizontalGap,
                                    double desktopVerticalGap,
                                    double desktopPrefHeight,
                                    boolean compact) {
        grid.getColumnConstraints().setAll(compact
                ? List.of(column(100.0))
                : List.of(column(primaryColumnPercent), column(100.0 - primaryColumnPercent)));

        GridPane.setConstraints(primary, 0, 0);
        GridPane.setConstraints(secondary, compact ? 0 : 1, compact ? 1 : 0);
        GridPane.setHgrow(primary, Priority.ALWAYS);
        GridPane.setHgrow(secondary, Priority.ALWAYS);
        GridPane.setVgrow(primary, Priority.ALWAYS);
        GridPane.setVgrow(secondary, Priority.ALWAYS);

        setFlexibleWidth(primary);
        setFlexibleWidth(secondary);
        grid.setHgap(compact ? 0.0 : desktopHorizontalGap);
        grid.setVgap(compact ? COMPACT_GAP : desktopVerticalGap);
        grid.setPrefHeight(compact ? Region.USE_COMPUTED_SIZE : desktopPrefHeight);

        if (compact) {
            if (!grid.getStyleClass().contains(COMPACT_STYLE_CLASS)) {
                grid.getStyleClass().add(COMPACT_STYLE_CLASS);
            }
        } else {
            grid.getStyleClass().remove(COMPACT_STYLE_CLASS);
        }
    }

    private static ColumnConstraints column(double percentWidth) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setHgrow(Priority.ALWAYS);
        constraints.setPercentWidth(percentWidth);
        return constraints;
    }

    private static void setFlexibleWidth(Node child) {
        if (child instanceof Region region) {
            region.setMinWidth(0.0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }
}
