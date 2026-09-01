package org.saintqd.vineriumdiscordaddon.listeners;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.*;
import github.scarsz.discordsrv.dependencies.jda.api.entities.ISnowflake;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.intellij.lang.annotations.RegExp;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;
import org.saintqd.vineriumdiscordaddon.commands.DiscordSlashCommands;
import org.saintqd.vineriumlib.VineriumLib;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordSRVListener {

    @Subscribe
    public void onDiscordReady(DiscordReadyEvent event) {
        try {
            DiscordSRV.api.addSlashCommandProvider(new DiscordSlashCommands());
            DiscordSRV.api.updateSlashCommands();
        } catch (Exception e) {
            VineriumDiscordAddon.inst().getLogger().warning("Failed to register slash commands: "+e.getMessage());
        }
    }

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
    public void onMessageSend(DiscordGuildMessagePreProcessEvent event) {
        String discordId = event.getAuthor().getId();
        if (VineriumDiscordAddon.inst().getConfig().getBoolean("MuteOfllinePlayers.Enabled",false)) {
            List<String> memberRoles = event.getMember().getRoles().stream().map(ISnowflake::getId).toList();
            if (Collections.disjoint(VineriumDiscordAddon.inst().getConfig().getStringList("MuteOfllinePlayers.BypassRoles"), memberRoles)) {

                long currentTime = Instant.now().toEpochMilli();
                long muteTime = VineriumDiscordAddon.inst().getConfig().getLong("MuteOfllinePlayers.MuteTime",1800000L);
                String roleId = VineriumDiscordAddon.inst().getConfig().getString("MuteOfllinePlayers.MuteRoleId","1453045832202063902");
                Role role = roleId.matches("\\d+")
                        ? DiscordSRV.getPlugin().getJda().getRoleById(roleId)
                        : DiscordSRV.getPlugin().getJda().getRolesByName(roleId,true).getFirst();

                UUID playerUuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getAuthor().getId());
                if (playerUuid == null)
                    playerUuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuidBypassCache(event.getAuthor().getId());
                if (playerUuid != null) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
                    long maxLastSeenTime = VineriumDiscordAddon.inst().getConfig().getLong("MuteOfllinePlayers.MaxLastSeenTime",604800000L);
                    if (offlinePlayer.getLastSeen() > 0 && offlinePlayer.getLastSeen() + maxLastSeenTime < System.currentTimeMillis()) {
                        event.setCancelled(true);
                        if (role != null) {
                            HashMap<String,Long> tempRoleData = VineriumDiscordAddon.inst().getRoleManager().getTempAddedRoleData().getOrDefault(discordId,new HashMap<>());
                            tempRoleData.put(roleId,currentTime + muteTime);
                            VineriumDiscordAddon.inst().getRoleManager().getTempAddedRoleData().put(discordId,tempRoleData);
                            Instant instant = Instant.ofEpochMilli(currentTime + muteTime);
                            ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
                            String tempRoleUntilString = zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                            Bukkit.getScheduler().runTaskAsynchronously(VineriumDiscordAddon.inst(), () ->
                                    DiscordSRV.getPlugin().getMainGuild().addRoleToMember(discordId, role).queue(success ->
                                            Bukkit.getScheduler().runTask(VineriumDiscordAddon.inst(), () -> {
                                                Bukkit.getConsoleSender().sendMessage(VineriumLib.inst().getLangManager().
                                                        parseLangString(VineriumDiscordAddon.inst(),"add_role_command_success",role.getId(),offlinePlayer.getName()));
                                                Bukkit.getConsoleSender().sendMessage(VineriumLib.inst().getLangManager().
                                                        parseLangString(VineriumDiscordAddon.inst(),"add_role_until_time",tempRoleUntilString));
                                                event.getMessage().delete().queue();
                                                event.getAuthor().openPrivateChannel().queue(privateChannel -> {
                                                    String afkMessage = VineriumLib.inst().getLangManager()
                                                            .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),
                                                                    "afk_mute_message"),"afk_mute_message");
                                                    privateChannel.sendMessage(afkMessage).queue();
                                                });
                                            })));
                        }
                        else {
                            VineriumDiscordAddon.inst().getLogger().warning("MuteOfllinePlayers: Role with id or name "+roleId+" does not exist.");
                        }
                    }
                }
                else if (VineriumDiscordAddon.inst().getConfig().getBoolean("MuteOfllinePlayers.DeleteMessageIfPlayerNotFound",true)) {
                    event.setCancelled(true);
                    event.getMessage().delete().queue();
                    event.getAuthor().openPrivateChannel().queue(privateChannel -> {
                        String afkMessage = VineriumLib.inst().getLangManager()
                                .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),
                                        "afk_mute_player_not_found_message"),"afk_mute_player_not_found_message");
                        privateChannel.sendMessage(afkMessage).queue();
                    });
                }
                else
                    VineriumDiscordAddon.inst().getLogger().warning("Tried to mute user "+event.getAuthor().getId()+", but OfflinePlayer was not found!");
            }
        }
        if (VineriumDiscordAddon.inst().getConfig().getBoolean("PlayerListMessage.Enabled",false)
                && VineriumDiscordAddon.inst().getConfig().getStringList("PlayerListMessage.Formats").contains(event.getMessage().getContentRaw())) {
            if (!DiscordSRV.getPlugin().getChannels().containsValue(event.getChannel().getId()))
                return;
            event.setCancelled(true);
            VineriumDiscordAddon.inst().getMessageFormatManager().parsePlayerList(event.getChannel().getId(), event.getMessage());
        }
    }

    @Subscribe
    public void onReplyMessageSend(DiscordGuildMessagePostProcessEvent event) {
        if (VineriumDiscordAddon.inst().getConfig().getBoolean("ReplyMessageEdit.Enabled",true)) {
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
