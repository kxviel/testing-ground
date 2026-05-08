package seda_project.control_alt_defeat.gamebox;

import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameBox extends Application {
    private static final Logger logger = LoggerFactory.getLogger(GameBox.class);

    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("GameBox: ZeroRuntimeWarranty");
        stage.centerOnScreen();
        stage.setMaximized(true);

        final var fxmlUrl = GameBox.class.getResource("/GameChoice.fxml");
        final var loader = new FXMLLoader(fxmlUrl);
        final var scene = new Scene(loader.load());

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> cleanExit());
        stage.show();
        logger.debug("Startup completed");
    }

    public static void cleanExit() {
        logger.debug("Shutting down");
        Platform.exit();
        System.exit(0);
    }
}