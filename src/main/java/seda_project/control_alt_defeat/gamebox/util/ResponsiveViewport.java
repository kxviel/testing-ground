package seda_project.control_alt_defeat.gamebox.util;

import java.util.Objects;

import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;

/**
 * Keeps the application's logical layout large enough to remain usable while
 * uniformly scaling it down when the physical viewport is smaller.
 */
public final class ResponsiveViewport extends Pane {

    public static final double DESIGN_WIDTH = 1_366.0;
    public static final double DESIGN_HEIGHT = 768.0;

    private final ContentHost contentHost;
    private final Scale viewportScale = new Scale(1.0, 1.0, 0.0, 0.0);

    public ResponsiveViewport(Parent content) {
        Objects.requireNonNull(content, "content");
        if (!content.isResizable()) {
            throw new IllegalArgumentException("content must be resizable");
        }

        contentHost = new ContentHost(content);
        contentHost.getTransforms().add(viewportScale);
        getChildren().add(contentHost);
        getStyleClass().add("responsive-scene-viewport");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        setMinSize(0.0, 0.0);
        setPrefSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public static double fitScale(double availableWidth, double availableHeight) {
        if (!Double.isFinite(availableWidth)
                || !Double.isFinite(availableHeight)
                || availableWidth <= 0.0
                || availableHeight <= 0.0) {
            return 1.0;
        }

        return Math.min(1.0, Math.min(
                availableWidth / DESIGN_WIDTH,
                availableHeight / DESIGN_HEIGHT));
    }

    @Override
    protected void layoutChildren() {
        double availableWidth = getWidth();
        double availableHeight = getHeight();
        double scale = fitScale(availableWidth, availableHeight);
        double logicalWidth = availableWidth / scale;
        double logicalHeight = availableHeight / scale;

        viewportScale.setX(scale);
        viewportScale.setY(scale);
        contentHost.resizeRelocate(0.0, 0.0, logicalWidth, logicalHeight);
    }

    private static final class ContentHost extends Pane {
        private final Parent content;

        private ContentHost(Parent content) {
            this.content = content;
            getChildren().add(content);
        }

        @Override
        protected void layoutChildren() {
            content.resizeRelocate(0.0, 0.0, getWidth(), getHeight());
        }
    }
}
