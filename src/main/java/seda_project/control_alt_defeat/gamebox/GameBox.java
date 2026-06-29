package seda_project.control_alt_defeat.gamebox;

import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.util.WindowManager;

public class GameBox extends Application {
    private static final Logger logger = LoggerFactory.getLogger(GameBox.class);

    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("GameBox: ZeroRuntimeWarranty");

        final var fxmlUrl = GameBox.class.getResource("/GameChoice.fxml");
        final var loader = new FXMLLoader(fxmlUrl);
        final var scene = WindowManager.createScene(loader.load());

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> cleanExit());

        WindowManager.applyCurrentSettings(stage);

        stage.show();
        logger.debug("Startup completed");
    }

    public static void cleanExit() {
        logger.debug("Shutting down");
        Platform.exit();
        System.exit(0);
    }
}
