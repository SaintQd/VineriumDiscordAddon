package org.saintqd.vineriumdiscordaddon.listeners;

import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.ButtonClickEvent;

public interface ButtonListener {

    void accept(ButtonClickEvent event, Long id, String buttonId);
}
