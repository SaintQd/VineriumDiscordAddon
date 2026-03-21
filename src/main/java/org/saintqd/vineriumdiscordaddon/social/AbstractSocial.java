package org.saintqd.vineriumdiscordaddon.social;

import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.ButtonClickEvent;
import org.bukkit.entity.Player;
import org.saintqd.vineriumdiscordaddon.listeners.ButtonListener;
import org.saintqd.vineriumdiscordaddon.listeners.MessageListener;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class AbstractSocial {

    private final MessageListener onMessageReceived;
    private final ButtonListener onButtonClicked;

    protected AbstractSocial(MessageListener onMessageReceived, ButtonListener onButtonClicked) {
        this.onMessageReceived = onMessageReceived;
        this.onButtonClicked = onButtonClicked;
    }

    public abstract void registerListener();

    public abstract void unregisterListener();

    protected void proceedMessage(ButtonClickEvent event, Long id, String message) {
        this.onMessageReceived.accept(event,id, message);
    }

    protected void proceedButton(ButtonClickEvent event, Long id, String message) {
        this.onButtonClicked.accept(event, id, message);
    }
    public abstract void sendMessage(Long id, String content, List<List<ButtonItem>> buttons, ButtonVisibility visibility);

    public void sendMessage(Player player, String content) {
        this.sendMessage(player, content, Collections.emptyList(), ButtonVisibility.DEFAULT);
    }

    public abstract void sendMessage(Player player, String content, List<List<ButtonItem>> buttons, ButtonVisibility visibility);

    public abstract void sendMessage(UUID uuid, String content, List<List<ButtonItem>> buttons, ButtonVisibility visibility);

    public abstract boolean canSend(Player player);

    public record ButtonItem(String id, String value, AbstractSocial.ButtonItem.Color color) {

        public enum Color {

                GREEN,
                RED,
                PRIMARY,
                SECONDARY,
                LINK
            }

        }

    public interface Constructor {
        AbstractSocial newInstance(MessageListener onMessageReceived, ButtonListener onButtonClicked);
    }

    public enum ButtonVisibility {

        DEFAULT,
        PREFER_INLINE,
        PREFER_KEYBOARD
    }

}
