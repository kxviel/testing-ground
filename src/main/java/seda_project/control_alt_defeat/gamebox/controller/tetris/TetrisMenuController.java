package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisLanDiscoveryService;
import seda_project.control_alt_defeat.gamebox.util.Router;

public class TetrisMenuController {

    private enum MenuView {
        MODE_CHOICE,
        LOCAL,
        LAN,
        HOST,
        JOIN
    }

    @FXML
    private VBox modeChoicePane;
    @FXML
    private VBox localPane;
    @FXML
    private VBox lanPane;
    @FXML
    private VBox hostPane;
    @FXML
    private VBox joinPane;

    @FXML
    private Button localModeButton;
    @FXML
    private Button lanModeButton;
    @FXML
    private Button hostRoleButton;
    @FXML
    private Button joinRoleButton;
    @FXML
    private Button startButton;

    @FXML
    private TextField localPlayerOneField;
    @FXML
    private TextField localPlayerTwoField;
    @FXML
    private TextField hostPlayerNameField;
    @FXML
    private TextField joinPlayerNameField;

    @FXML
    private Label hostInfoLabel;
    @FXML
    private Label statusLabel;

    @FXML
    private ListView<TetrisLanDiscoveryService.DiscoveredGame> availableGamesList;

    private MenuView currentView = MenuView.MODE_CHOICE;
    private final TetrisLanDiscoveryService udpDiscovery = new TetrisLanDiscoveryService();

    public record GameSetup(String playerOneName, String playerTwoName) {
    }

    @FXML
    public void initialize() {
        availableGamesList.setPlaceholder(new Label("No LAN games found yet."));
        selectView(MenuView.MODE_CHOICE);
    }

    @FXML
    private void onLocalMode() {
        selectView(MenuView.LOCAL);
    }

    @FXML
    private void onLanMode() {
        selectView(MenuView.LAN);
    }

    @FXML
    private void onHostRole() {
        udpDiscovery.stopListening();
        selectView(MenuView.HOST);
    }

    @FXML
    private void onJoinRole() {
        selectView(MenuView.JOIN);
        startDiscovery();
    }

    @FXML
    private void onRefreshGames() {
        startDiscovery();
        statusLabel.setText("Searching for LAN games...");
    }

    @FXML
    private void onStart(ActionEvent event) {
        switch (currentView) {
            case MODE_CHOICE -> statusLabel.setText("Choose Local or LAN before starting.");
            case LOCAL -> startLocalGame(event);
            case LAN -> statusLabel.setText("Choose Host or Join before starting a LAN game.");
            case HOST -> startHost();
            case JOIN -> joinGame(event);
        }
    }

    @FXML
    private void onBack(ActionEvent event) {
        if (currentView == MenuView.MODE_CHOICE) {
            udpDiscovery.close();
            Router.goTo(event, "/GameChoice.fxml", null);
            return;
        }

        selectView(parentView(currentView));
    }

    private void selectView(MenuView view) {
        if (view != MenuView.JOIN) {
            udpDiscovery.stopListening();
            availableGamesList.getItems().clear();
        }
        if (view != MenuView.HOST) {
            udpDiscovery.stopAdvertising();
        } else {
            hostInfoLabel.setText("Not advertising.");
        }

        currentView = view;
        showOnly(view);
        setSelected(localModeButton, view == MenuView.LOCAL);
        setSelected(lanModeButton, view == MenuView.LAN || view == MenuView.HOST || view == MenuView.JOIN);
        setSelected(hostRoleButton, view == MenuView.HOST);
        setSelected(joinRoleButton, view == MenuView.JOIN);

        startButton.setDisable(view == MenuView.MODE_CHOICE || view == MenuView.LAN);
        startButton.setText(switch (view) {
            case MODE_CHOICE -> "Start";
            case LOCAL -> "Start Local";
            case LAN -> "Start";
            case HOST -> "Start Host";
            case JOIN -> "Join Game";
        });

        statusLabel.setText(switch (view) {
            case MODE_CHOICE -> "Choose Local or LAN to configure the match.";
            case LOCAL -> "Local preview ready.";
            case LAN -> "Choose whether this player will host or join.";
            case HOST -> "Enter your name and start hosting.";
            case JOIN -> "Searching for LAN games.";
        });
    }

    private void showOnly(MenuView view) {
        setShown(modeChoicePane, view == MenuView.MODE_CHOICE);
        setShown(localPane, view == MenuView.LOCAL);
        setShown(lanPane, view == MenuView.LAN);
        setShown(hostPane, view == MenuView.HOST);
        setShown(joinPane, view == MenuView.JOIN);
    }

    private MenuView parentView(MenuView view) {
        return switch (view) {
            case LOCAL, LAN -> MenuView.MODE_CHOICE;
            case HOST, JOIN -> MenuView.LAN;
            case MODE_CHOICE -> MenuView.MODE_CHOICE;
        };
    }

    private void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void setSelected(Button button, boolean selected) {
        if (button == null) {
            return;
        }

        if (selected && !button.getStyleClass().contains("btn-selected")) {
            button.getStyleClass().add("btn-selected");
        } else if (!selected) {
            button.getStyleClass().remove("btn-selected");
        }
    }

    private void startLocalGame(ActionEvent event) {
        String playerOne = trimmed(localPlayerOneField);
        String playerTwo = trimmed(localPlayerTwoField);

        if (playerOne.isEmpty() || playerTwo.isEmpty()) {
            statusLabel.setText("Enter both local player names before starting.");
            return;
        }

        GameSetup setup = new GameSetup(playerOne, playerTwo);
        Router.goTo(event, "/tetris/TetrisGame.fxml", setup);
    }

    private void startHost() {
        String hostName = trimmed(hostPlayerNameField);

        if (hostName.isEmpty()) {
            statusLabel.setText("Enter your host player name before starting.");
            return;
        }

        udpDiscovery.startAdvertising(
                hostName,
                GameServer.DEFAULT_PORT,
                error -> Platform.runLater(() -> statusLabel.setText(error)));

        hostInfoLabel.setText("Advertising as " + hostName + ".");
        statusLabel.setText("Host is visible on LAN.");
    }

    private void joinGame(ActionEvent event) {
        String playerName = trimmed(joinPlayerNameField);
        TetrisLanDiscoveryService.DiscoveredGame selectedGame = availableGamesList.getSelectionModel()
                .getSelectedItem();

        if (playerName.isEmpty()) {
            statusLabel.setText("Enter your player name before joining.");
            return;
        }
        if (selectedGame == null) {
            statusLabel.setText("Select an available game before joining.");
            return;
        }

        udpDiscovery.stopListening();
        GameSetup setup = new GameSetup(playerName, selectedGame.playerName());
        Router.goTo(event, "/tetris/TetrisGame.fxml", setup);
    }

    private void startDiscovery() {
        availableGamesList.getItems().clear();
        udpDiscovery.startListening(
                game -> Platform.runLater(() -> addOrUpdateGame(game)),
                error -> Platform.runLater(() -> statusLabel.setText(error)));
    }

    private void addOrUpdateGame(TetrisLanDiscoveryService.DiscoveredGame game) {
        for (int i = 0; i < availableGamesList.getItems().size(); i++) {
            TetrisLanDiscoveryService.DiscoveredGame current = availableGamesList.getItems().get(i);
            if (current.sessionId().equals(game.sessionId())) {
                availableGamesList.getItems().set(i, game);
                return;
            }
        }

        availableGamesList.getItems().add(game);
        if (availableGamesList.getSelectionModel().isEmpty()) {
            availableGamesList.getSelectionModel().selectFirst();
        }
    }

    private String trimmed(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }
}
