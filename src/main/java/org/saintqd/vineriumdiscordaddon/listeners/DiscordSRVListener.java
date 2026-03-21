package org.saintqd.vineriumdiscordaddon.listeners;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.*;
import github.scarsz.discordsrv.dependencies.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.intellij.lang.annotations.RegExp;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordSRVListener {

    @Subscribe
    public void onDiscordMessageReceive(DiscordGuildMessagePreBroadcastEvent event) {
        if (VineriumDiscordAddon.inst().getConfig().getBoolean("MessageFilter.Enabled")) {
            String message = PlainTextComponentSerializer.plainText().serialize(event.getMessage());
            for (String regex : VineriumDiscordAddon.inst().getConfig().getStringList("MessageFilter.Regex")) {
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    event.getRecipients().clear();
                    return;
                }
            }
        }
        event.getRecipients().removeIf(sender -> sender.permissionValue("vineriumdiscord.toggleenabled") == TriState.TRUE);
    }

    @Subscribe
    public void onListMessageSend(DiscordGuildMessagePreProcessEvent event) {
        if (VineriumDiscordAddon.inst().getConfig().getBoolean("PlayerListMessage.Enabled")
                && VineriumDiscordAddon.inst().getConfig().getStringList("PlayerListMessage.Formats").contains(event.getMessage().getContentRaw())) {
            event.setCancelled(true);
            VineriumDiscordAddon.inst().getMessageFormatManager().parsePlayerList(event.getChannel().getId(), event.getMessage());
        }
    }

    @Subscribe
    public void onReplyMessageSend(DiscordGuildMessagePostProcessEvent event) {
        if (VineriumDiscordAddon.inst().getConfig().getBoolean("ReplyMessageEdit.Enabled")) {
            @RegExp String originalRegexReplaceWithEscapes = VineriumDiscordAddon.inst().getConfig().getString("ReplyMessageEdit.Regex","null");
            @RegExp String originalRegexReplace = originalRegexReplaceWithEscapes.replace("\\","");
            String content = PlainTextComponentSerializer.plainText().serialize(event.getMinecraftMessage());
            if (content.contains(originalRegexReplace)) {
                if (event.getMessage().getMessageReference() == null || event.getMessage().getMessageReference().getMessage() == null)
                    return;
                String parsedNickname = event.getMessage().getMessageReference().getMessage().getContentRaw();
                parsedNickname = parsedNickname.split(VineriumDiscordAddon.inst().getConfig().getString("ReplyMessageEdit.NameSubstring","null"))[0];
                String replaceName = VineriumDiscordAddon.inst()
                        .getConfig().getString("ReplyMessageEdit.ReplaceText","null").replace("*",parsedNickname);
                event.setMinecraftMessage(event.getMinecraftMessage()
                        .replaceText(config -> config.match(originalRegexReplaceWithEscapes).replacement(replaceName)));
            }
        }
    }
}
