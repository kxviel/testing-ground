package seda_project.control_alt_defeat.gamebox.util;

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Label;

public final class UiVisibility {

    private UiVisibility() {
    }

    public static void setVisibleManaged(Node node, boolean visible) {
        if (node == null) {
            return;
        }

        node.setVisible(visible);
        node.setManaged(visible);
    }

    public static void bindVisibleWhenTextPresent(Label label) {
        if (label == null) {
            return;
        }

        label.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> label.getText() != null && !label.getText().isBlank(),
                label.textProperty()));
        label.managedProperty().bind(label.visibleProperty());
    }
}
