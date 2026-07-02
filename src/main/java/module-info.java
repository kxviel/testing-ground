module seda.project.control.alt.defeat.gamebox {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires transitive javafx.graphics;
    requires org.slf4j;
    requires ch.qos.logback.classic;

    opens seda_project.control_alt_defeat.gamebox to javafx.fxml;
    opens seda_project.control_alt_defeat.gamebox.controller to javafx.fxml;
    exports seda_project.control_alt_defeat.gamebox to javafx.graphics;
    exports seda_project.control_alt_defeat.gamebox.network;
    exports seda_project.control_alt_defeat.gamebox.network.tetris;
    exports seda_project.control_alt_defeat.gamebox.network.hexchess;
    exports seda_project.control_alt_defeat.gamebox.controller;
    exports seda_project.control_alt_defeat.gamebox.controller.memory;
    opens seda_project.control_alt_defeat.gamebox.controller.memory to javafx.fxml;
    exports seda_project.control_alt_defeat.gamebox.controller.tetris;
    opens seda_project.control_alt_defeat.gamebox.controller.tetris to javafx.fxml;
    exports seda_project.control_alt_defeat.gamebox.controller.hexchess;
    opens seda_project.control_alt_defeat.gamebox.controller.hexchess to javafx.fxml;
    exports seda_project.control_alt_defeat.gamebox.settings;
    exports seda_project.control_alt_defeat.gamebox.model.memory;
    exports seda_project.control_alt_defeat.gamebox.model.tetris;
    exports seda_project.control_alt_defeat.gamebox.model.tetris.enums;
    exports seda_project.control_alt_defeat.gamebox.model.hexchess;
}
