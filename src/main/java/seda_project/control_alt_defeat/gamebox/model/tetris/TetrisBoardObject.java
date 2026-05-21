package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.Objects;

public record TetrisBoardObject(TetrisItemType type, BoardPosition position) {

    public TetrisBoardObject {
        type = type == null ? TetrisItemType.TELEPORT_SWAP : type;
        Objects.requireNonNull(position, "position");
    }
}
