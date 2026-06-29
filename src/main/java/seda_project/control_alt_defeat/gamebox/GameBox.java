package seda_project.control_alt_defeat.gamebox;

import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.util.WindowManager;

public class GameBox extends Application {
    private static final Logger logger = LoggerFactory.getLogger(GameBox.class);
    private static final String INTER_FONT = "/fonts/InterVariable.ttf";

    @Override
    public void start(Stage stage) throws IOException {
        loadFonts();
        stage.setTitle("GameBox: ZeroRuntimeWarranty");

        final var fxmlUrl = GameBox.class.getResource("/GameChoice.fxml");
        final var loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        WindowManager.setScene(stage, root);
        stage.setOnCloseRequest(e -> cleanExit());
        stage.show();
        WindowManager.applyCurrentSettings(stage);
        logger.debug("Startup completed");
    }

    private void loadFonts() {
        var fontUrl = GameBox.class.getResource(INTER_FONT);
        if (fontUrl == null || Font.loadFont(fontUrl.toExternalForm(), 12) == null) {
            System.err.println("Inter font missing; using system fallback.");
        }
    }

    public static void cleanExit() {
        logger.debug("Shutting down");
        Platform.exit();
        System.exit(0);
    }
}
