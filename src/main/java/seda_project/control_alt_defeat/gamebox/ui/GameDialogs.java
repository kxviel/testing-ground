package seda_project.control_alt_defeat.gamebox.ui;

import java.net.URL;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.stage.Window;

/** Creates consistently styled, application-owned dialogs for every game. */
public final class GameDialogs {

    private static final String THEME_STYLESHEET = "/Theme.css";
    private static final String DIALOG_STYLESHEET = "/Dialog.css";

    private GameDialogs() {
    }

    public static boolean confirm(Node ownerNode, String title, String message) {
        return createConfirmation(ownerNode, title, message)
                .showAndWait()
                .filter(ButtonType.YES::equals)
                .isPresent();
    }

    public static void warning(Node ownerNode, String title, String message) {
        createWarning(ownerNode, title, message).showAndWait();
    }

    static Alert createWarning(Node ownerNode, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setGraphic(icon("!", "game-dialog-warning-icon"));
        style(alert, ownerNode);
        Button ok = button(alert, ButtonType.OK);
        if (ok != null) {
            ok.getStyleClass().add("game-dialog-primary");
        }
        return alert;
    }

    public static void style(Dialog<?> dialog, Node ownerNode) {
        if (dialog == null) {
            return;
        }

        Window owner = ownerWindow(ownerNode);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        DialogPane pane = dialog.getDialogPane();
        if (!pane.getStyleClass().contains("game-dialog")) {
            pane.getStyleClass().add("game-dialog");
        }
        addStylesheet(pane, THEME_STYLESHEET);
        addStylesheet(pane, DIALOG_STYLESHEET);
    }

    static Alert createConfirmation(Node ownerNode, String title, String message) {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                message,
                ButtonType.YES,
                ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setGraphic(icon("?", "game-dialog-question-icon"));
        style(alert, ownerNode);

        Button yes = button(alert, ButtonType.YES);
        Button no = button(alert, ButtonType.NO);
        if (yes != null) {
            yes.setDefaultButton(false);
            yes.getStyleClass().add("game-dialog-primary");
        }
        if (no != null) {
            no.setDefaultButton(true);
            no.getStyleClass().add("game-dialog-secondary");
        }
        return alert;
    }

    private static Label icon(String text, String styleClass) {
        Label icon = new Label(text);
        icon.getStyleClass().addAll("game-dialog-icon", styleClass);
        return icon;
    }

    private static Button button(Dialog<?> dialog, ButtonType type) {
        Node node = dialog.getDialogPane().lookupButton(type);
        return node instanceof Button button ? button : null;
    }

    private static Window ownerWindow(Node ownerNode) {
        return ownerNode == null || ownerNode.getScene() == null
                ? null
                : ownerNode.getScene().getWindow();
    }

    private static void addStylesheet(DialogPane pane, String resource) {
        URL stylesheet = GameDialogs.class.getResource(resource);
        if (stylesheet == null) {
            throw new IllegalStateException("Missing dialog stylesheet: " + resource);
        }
        String externalForm = stylesheet.toExternalForm();
        if (!pane.getStylesheets().contains(externalForm)) {
            pane.getStylesheets().add(externalForm);
        }
    }
}
