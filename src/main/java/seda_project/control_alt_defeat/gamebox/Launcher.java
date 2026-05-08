package seda_project.control_alt_defeat.gamebox;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher {

    private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {
        logger.debug("Starting application");
        Application.launch(GameBox.class, args);
    }
}
