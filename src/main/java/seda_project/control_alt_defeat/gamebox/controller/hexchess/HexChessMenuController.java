package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.io.IOException;

public class HexChessMenuController {

    @FXML
    private TextField whiteNameField;
    @FXML
    private TextField blackNameField;
    @FXML
    private TextField lanHostField;
    @FXML
    private Label statusLabel;

    @FXML
    private void onStartLocal(ActionEvent event) {
        Router.goTo(event, "/hexchess/HexChessGame.fxml", setup(HexGameMode.LOCAL));
    }

    @FXML
    private void onStartBot(ActionEvent event) {
        Router.goTo(event, "/hexchess/HexChessGame.fxml", setup(HexGameMode.BOT));
    }

    @FXML
    private void onCustomSetup(ActionEvent event) {
        Router.goTo(event, "/hexchess/HexChessSetup.fxml", setup(HexGameMode.LOCAL));
    }

    @FXML
    private void onHostLan(ActionEvent event) {
        Stage stage = stageFrom(event);
        GameServer server = new GameServer();

        try {
            server.listen(GameServer.DEFAULT_PORT, message -> {
            }, () -> {
            });
        } catch (IOException e) {
            statusLabel.setText("Could not host LAN game: " + e.getMessage());
            return;
        }

        statusLabel.setText("Hosting on " + GameServer.getLocalAddress() + ":" + GameServer.DEFAULT_PORT
                + ". Waiting for joiner...");

        Thread waitThread = new Thread(() -> {
            try {
                server.waitForClient();
                Platform.runLater(() -> Router.goTo(
                        stage,
                        "/hexchess/HexChessGame.fxml",
                        HexChessGameRouteData.host(setup(HexGameMode.NETWORK_HOST), server)));
            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText("LAN host failed: " + e.getMessage()));
                server.close();
            }
        }, "hexchess-host-wait");
        waitThread.setDaemon(true);
        waitThread.start();
    }

    @FXML
    private void onJoinLan(ActionEvent event) {
        String host = lanHostField == null ? "" : lanHostField.getText().trim();
        if (host.isBlank()) {
            statusLabel.setText("Enter the host IP address first.");
            return;
        }

        Stage stage = stageFrom(event);
        statusLabel.setText("Connecting to " + host + "...");

        Thread connectThread = new Thread(() -> {
            GameClient client = new GameClient();
            try {
                client.connect(host, GameServer.DEFAULT_PORT, message -> {
                }, () -> {
                });
                Platform.runLater(() -> Router.goTo(
                        stage,
                        "/hexchess/HexChessGame.fxml",
                        HexChessGameRouteData.join(setup(HexGameMode.NETWORK_CLIENT), client)));
            } catch (IOException e) {
                client.close();
                Platform.runLater(() -> statusLabel.setText("Could not join LAN game: " + e.getMessage()));
            }
        }, "hexchess-client-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @FXML
    private void onBack(ActionEvent event) {
        Router.goTo(event, "/GameChoice.fxml", null);
    }

    private HexChessGameSetup setup(HexGameMode mode) {
        String whiteName = whiteNameField == null ? "White" : whiteNameField.getText();
        String blackName = mode == HexGameMode.BOT
                ? "Bot"
                : blackNameField == null ? "Black" : blackNameField.getText();

        return new HexChessGameSetup(whiteName, blackName, mode);
    }

    private Stage stageFrom(ActionEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }
}
