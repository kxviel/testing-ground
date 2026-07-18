package seda_project.control_alt_defeat.gamebox.model.tetris.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignO;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.materialdesign2.MaterialDesignU;

public enum TetrisItemType {
    SPEED_UP_OPPONENT(MaterialDesignR.RABBIT, MaterialDesignS.SWORD_CROSS),
    SLOW_SELF(MaterialDesignT.TURTLE, MaterialDesignA.ACCOUNT),
    ROTATION_DELAY_OPPONENT(MaterialDesignO.ORBIT, MaterialDesignS.SWORD_CROSS),
    ROTATION_DELAY_SELF(MaterialDesignO.ORBIT, MaterialDesignA.ACCOUNT),
    SLOW_OPPONENT(MaterialDesignT.TURTLE, MaterialDesignS.SWORD_CROSS),
    RADIUS_BOMB(MaterialDesignB.BOMB, null),
    COLUMN_BOMB(MaterialDesignF.FLASH, null),
    TELEPORT(MaterialDesignU.UFO, null),
    BOARD_SWAP(MaterialDesignS.SWAP_HORIZONTAL_BOLD, null),
    FALLING_PIECE_SWAP(MaterialDesignS.SHUFFLE_VARIANT, null);

    private final Ikon icon;
    private final Ikon actorOverlay;

    TetrisItemType(Ikon icon, Ikon actorOverlay) {
        this.icon = icon;
        this.actorOverlay = actorOverlay;
    }

    public Ikon icon() {
        return icon;
    }

    public Ikon actorOverlay() {
        return actorOverlay;
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
