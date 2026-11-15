package com.enderlaserquarry.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Messages to the player: action bar, or chat if too long (the action bar doesn't wrap).
 */
public final class PlayerMessages {
    private static final int ACTION_BAR_MAX_LENGTH = 70;

    public static void show(Player player, Component message) {
        player.displayClientMessage(message, message.getString().length() <= ACTION_BAR_MAX_LENGTH);
    }

    public static void showIfAny(Player player, @Nullable Component message) {
        if (message != null) {
            show(player, message);
        }
    }

    private PlayerMessages() {
    }
}
