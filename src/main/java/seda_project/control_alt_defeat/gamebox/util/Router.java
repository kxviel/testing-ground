package seda_project.control_alt_defeat.gamebox.util;

import java.io.IOException;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class Router {

    private Router() {
    }

    public static void goTo(ActionEvent event, String route, Object data) {
        try {
            Node source = (Node) event.getSource();
            Scene currentScene = source.getScene();
            Stage stage = (Stage) currentScene.getWindow();

            FXMLLoader loader = new FXMLLoader(Router.class.getResource(route));
            Parent root = loader.load();
            Scene scene = new Scene(root, currentScene.getWidth(), currentScene.getHeight());
            Object controller = loader.getController();

            if (data != null && controller instanceof RouteDataReceiver receiver) {
                receiver.setRouteData(data);
            }

            stage.setScene(scene);
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
