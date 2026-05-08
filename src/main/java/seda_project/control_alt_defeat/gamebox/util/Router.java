package seda_project.control_alt_defeat.gamebox.util;

import java.io.IOException;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class Router {

    private Router() {
    }

    public static void goTo(ActionEvent event, String route, Object data) {
        try {
            FXMLLoader loader = new FXMLLoader(Router.class.getResource(route));
            Scene scene = new Scene(loader.load());
            Object controller = loader.getController();

            if (data != null && controller instanceof RouteDataReceiver receiver) {
                receiver.setRouteData(data);
            }

            Node source = (Node) event.getSource();
            Stage stage = (Stage) source.getScene().getWindow();
            stage.setScene(scene);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
