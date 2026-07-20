package seda_project.control_alt_defeat.gamebox.model.tetris.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignO;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.materialdesign2.MaterialDesignU;

public enum TetrisItemType {
    SPEED_UP_OPPONENT(MaterialDesignR.RABBIT),
    SLOW_SELF(MaterialDesignT.TURTLE),
    ROTATION_DELAY_OPPONENT(MaterialDesignO.ORBIT),
    ROTATION_DELAY_SELF(MaterialDesignO.ORBIT),
    SLOW_OPPONENT(MaterialDesignT.TURTLE),
    RADIUS_BOMB(MaterialDesignB.BOMB),
    COLUMN_BOMB(MaterialDesignF.FLASH),
    TELEPORT(MaterialDesignU.UFO),
    BOARD_SWAP(MaterialDesignS.SWAP_HORIZONTAL_BOLD),
    FALLING_PIECE_SWAP(MaterialDesignS.SHUFFLE_VARIANT);

    private final Ikon icon;

    TetrisItemType(Ikon icon) {
        this.icon = icon;
    }

    public Ikon icon() {
        return icon;
    }

    /**
     * Kept as a source-compatible no-op for callers of the former overlay API.
     * Role distinction is now rendered as a colored badge ring.
     */
    @Deprecated
    public Ikon actorOverlay() {
        return null;
    }

    public boolean requiresActiveOpponent() {
        return switch (this) {
            case SPEED_UP_OPPONENT,
                 ROTATION_DELAY_OPPONENT,
                 SLOW_OPPONENT,
                 TELEPORT,
                 BOARD_SWAP,
                 FALLING_PIECE_SWAP -> true;
            case SLOW_SELF,
                 ROTATION_DELAY_SELF,
                 RADIUS_BOMB,
                 COLUMN_BOMB -> false;
        };
    }

    public static EnumSet<TetrisItemType> eligibleForOpponentState(boolean opponentPlaying) {
        return Arrays.stream(values())
                .filter(type -> opponentPlaying || !type.requiresActiveOpponent())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TetrisItemType.class)));
    }
}
