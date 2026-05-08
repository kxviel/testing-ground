module seda.project.control.alt.defeat.gamebox {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;
    requires org.slf4j;
    requires ch.qos.logback.classic;

    opens seda_project.control_alt_defeat.gamebox to javafx.fxml;
    opens seda_project.control_alt_defeat.gamebox.controller to javafx.fxml;
    exports seda_project.control_alt_defeat.gamebox to javafx.graphics;
    exports seda_project.control_alt_defeat.gamebox.network;
    exports seda_project.control_alt_defeat.gamebox.controller;
    exports seda_project.control_alt_defeat.gamebox.controller.memory;
    opens seda_project.control_alt_defeat.gamebox.controller.memory to javafx.fxml;
    exports seda_project.control_alt_defeat.gamebox.model.memory;
}
