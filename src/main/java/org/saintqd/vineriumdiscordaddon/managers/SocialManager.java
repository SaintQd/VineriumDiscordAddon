package org.saintqd.vineriumdiscordaddon.managers;

import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.ButtonClickEvent;
import org.saintqd.vineriumdiscordaddon.listeners.ButtonListener;
import org.saintqd.vineriumdiscordaddon.listeners.MessageListener;
import org.saintqd.vineriumdiscordaddon.social.AbstractSocial;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class SocialManager {

    private final LinkedList<AbstractSocial> socialList;
    private final LinkedList<MessageListener> messageEvents = new LinkedList<>();
    private final HashMap<String, ButtonListener> buttonEvents = new HashMap<>();
    private final HashMap<String, String> buttonIdMap = new HashMap<>();

    public SocialManager(AbstractSocial.Constructor... socialList) {
        this.socialList = new LinkedList<>();
        for (AbstractSocial.Constructor function : socialList) {
            AbstractSocial social = function.newInstance(this::onMessageReceived, this::onButtonClicked);
            social.registerListener();
            this.socialList.add(social);
        }
    }

    public void unregister() {
        for (AbstractSocial social : socialList)
            social.unregisterListener();
    }

    public void registerKeyboard(List<List<AbstractSocial.ButtonItem>> keyboard) {
        for (List<AbstractSocial.ButtonItem> items : keyboard) {
            for (AbstractSocial.ButtonItem item : items) {
                this.buttonIdMap.put(item.value(), item.id());
            }
        }
    }

    private void onMessageReceived(ButtonClickEvent event, Long id, String message) {
        String buttonId = this.buttonIdMap.get(message);
        if (buttonId != null) {
            this.onButtonClicked(event,id, buttonId);
        }

        this.messageEvents.forEach(messageEvent -> messageEvent.accept(event,id, message));
    }

    private void onButtonClicked(ButtonClickEvent event, Long id, String buttonId) {
        ButtonListener buttonListener = this.buttonEvents.get(buttonId);
        if (buttonListener != null) {
            buttonListener.accept(event, id,buttonId);
        }
    }

    public void addMessageEvent(MessageListener event) {
        this.messageEvents.add(event);
    }

    public void addButtonEvent(String id, ButtonListener event) {
        this.buttonEvents.put(id, event);
    }

    public void removeButtonEvent(String id) {
        this.buttonEvents.remove(id);
    }

    public LinkedList<AbstractSocial> getSocialList() {
        return socialList;
    }

    public void broadcastMessage(Long id, String message,
                                 List<List<AbstractSocial.ButtonItem>> item, AbstractSocial.ButtonVisibility visibility) {
        this.socialList.forEach(e -> e.sendMessage(id, message, item, visibility));
    }
}
