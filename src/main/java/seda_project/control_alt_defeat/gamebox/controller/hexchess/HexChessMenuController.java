package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.util.Router;

public class HexChessMenuController {

    @FXML
    private TextField whiteNameField;
    @FXML
    private TextField blackNameField;
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
    private void onCustomSetup() {
        statusLabel.setText("Custom setup waits for the exact custom-piece and validation rules.");
    }

    @FXML
    private void onLanMode() {
        statusLabel.setText("LAN mode will use a separate Hexagon Chess protocol; not wired into Memory or Zetris.");
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
}
