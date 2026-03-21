package org.saintqd.vineriumdiscordaddon.commands;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.commands.PluginSlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommandProvider;
import github.scarsz.discordsrv.dependencies.jda.api.entities.*;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionMapping;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionType;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;
import org.saintqd.vineriumlib.VineriumLib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class DiscordSlashCommands implements SlashCommandProvider {

    @Override
    public Set<PluginSlashCommand> getSlashCommands() {

        Guild guild = DiscordSRV.getPlugin().getMainGuild();

        List<CommandData> commands = new ArrayList<>();
        if (VineriumDiscordAddon.inst().getConfig().getBoolean("Commands.Mute",true)) {
            commands.add(new CommandData("mute", VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_mute_hint")))
                    .addOption(OptionType.USER, "user", VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_mute_hint_user")), true)
                    .addOption(OptionType.STRING, "time", VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_mute_hint_time")), true)
                    .addOption(OptionType.STRING, "reason", VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_mute_hint_reason")), false));
        }
        if (VineriumDiscordAddon.inst().getConfig().getBoolean("Commands.Unmute",true)) {
            commands.add(new CommandData("unmute", VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_unmute_hint")))
                    .addOption(OptionType.USER, "user", VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_mute_hint_user")), true)
                    .addOption(OptionType.STRING, "reason", VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_mute_hint_reason")), false));
        }
        if (VineriumDiscordAddon.inst().getConfig().getBoolean("Commands.Upload",false)) {
            commands.add(new CommandData("upload", VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_upload_hint")))
                    .addOption(OptionType.STRING, "path", VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_upload_hint_path")), true));
        }

        return commands.stream().map(each -> new PluginSlashCommand(VineriumDiscordAddon.inst(),each,guild.getId())).collect(Collectors.toSet());
    }

    @SlashCommand(path = "*")
    public void onSlashCommand(SlashCommandEvent event) {
        Guild guild = DiscordSRV.getPlugin().getMainGuild();
        if (event.getGuild().getIdLong() != guild.getIdLong()) {
            return;
        }
        if (!(event.getChannel() instanceof TextChannel channel)) {
            return;
        }
        String label = event.getName();
        if (!DiscordSRV.getPlugin().getChannels().containsValue(channel.getId())) {
            event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_wrong_channel"))).setEphemeral(true).queue();
            return;
        }
        else {
            switch (label.toLowerCase()) {
                case "mute" -> {
                    boolean hasRole = false;
                    String requiredRole = VineriumDiscordAddon.inst().getConfig().getString("Commands.Mute.RequiredRole", "");
                    if (requiredRole.isEmpty())
                        hasRole = true;
                    else {
                        for (Role role : event.getMember().getRoles()) {
                            if (role.getId().equals(requiredRole)) {
                                hasRole = true;
                                break;
                            }
                        }
                    }
                    if (!hasRole) {
                        event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_no_role"))).setEphemeral(true).queue();
                        return;
                    }
                    User user = event.getOption("user").getAsUser();
                    UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuidBypassCache(user.getId());
                    if (uuid == null) {
                        event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_no_user"))).setEphemeral(true).queue();
                        return;
                    }
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                    if (!offlinePlayer.hasPlayedBefore()) {
                        event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_no_user"))).setEphemeral(true).queue();
                        return;
                    }
                    String playerName = offlinePlayer.getName();
                    String time = event.getOption("time").getAsString();
                    String reason = VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_mute_reason_default"));
                    OptionMapping reasonOption = event.getOption("reason");
                    if (reasonOption != null)
                        reason = reasonOption.getAsString();
                    String command = VineriumDiscordAddon.inst().getConfig().getString("Commands.Mute.Command", "mute {1} {2} {3}")
                            .replace("{1}", playerName).replace("{2}", time).replace("{3}", reason);
                    Bukkit.getScheduler().runTask(VineriumDiscordAddon.inst(), () -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command));
                    String parsedMessage = PlainTextComponentSerializer.plainText().serialize(VineriumLib.inst().getLangManager()
                            .parseLangString(VineriumDiscordAddon.inst(), "command_mute_success", playerName, time, reason));
                    if (reason.contains("-s"))
                        event.reply(parsedMessage).setEphemeral(true).queue();
                    else
                        event.reply(parsedMessage).queue();
                }
                case "unmute" -> {
                    boolean hasRole = false;
                    String requiredRole = VineriumDiscordAddon.inst().getConfig().getString("Commands.Unmute.RequiredRole", "");
                    if (requiredRole.isEmpty())
                        hasRole = true;
                    else {
                        for (Role role : event.getMember().getRoles()) {
                            if (role.getId().equals(requiredRole)) {
                                hasRole = true;
                                break;
                            }
                        }
                    }
                    if (!hasRole) {
                        event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_no_role"))).setEphemeral(true).queue();
                        return;
                    }
                    User user = event.getOption("user").getAsUser();
                    UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuidBypassCache(user.getId());
                    if (uuid == null) {
                        event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_no_user"))).setEphemeral(true).queue();
                        return;
                    }
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                    if (!offlinePlayer.hasPlayedBefore()) {
                        event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_no_user"))).setEphemeral(true).queue();
                        return;
                    }
                    String playerName = offlinePlayer.getName();

                    String reason = VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_mute_reason_default"));
                    OptionMapping reasonOption = event.getOption("reason");
                    if (reasonOption != null)
                        reason = reasonOption.getAsString();
                    String command = VineriumDiscordAddon.inst().getConfig().getString("Commands.Unmute.Command", "unmute {1} {2}")
                            .replace("{1}", playerName).replace("{2}", reason);
                    Bukkit.getScheduler().runTask(VineriumDiscordAddon.inst(), () -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command));
                    String parsedMessage = PlainTextComponentSerializer.plainText().serialize(VineriumLib.inst().getLangManager()
                            .parseLangString(VineriumDiscordAddon.inst(), "command_unmute_success", playerName, reason));
                    if (reason.contains("-s"))
                        event.reply(parsedMessage).setEphemeral(true).queue();
                    else
                        event.reply(parsedMessage).queue();
                }
                case "upload" -> {
                    boolean hasRole = false;
                    String requiredRole = VineriumDiscordAddon.inst().getConfig().getString("Commands.Upload.RequiredRole", "");
                    if (requiredRole.isEmpty())
                        hasRole = true;
                    else {
                        for (Role role : event.getMember().getRoles()) {
                            if (role.getId().equals(requiredRole)) {
                                hasRole = true;
                                break;
                            }
                        }
                    }
                    if (!hasRole) {
                        event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_no_role"))).setEphemeral(true).queue();
                        return;
                    }
                    String path = event.getOption("path").getAsString();
                    boolean pathIsAllowed = false;
                    for (String possiblePath : VineriumDiscordAddon.inst().getConfig().getStringList("Commands.Upload.AllowedPaths")) {
                        if (path.startsWith(possiblePath)) {
                            pathIsAllowed = true;
                            break;
                        }
                    }
                    if (!pathIsAllowed) {
                        event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_upload_path_is_not_allowed"))).setEphemeral(true).queue();
                        return;
                    }
                    List<Message> messages = event.getChannel().getHistory().retrievePast(1).complete();
                    if (!messages.isEmpty()) {
                        Message message = messages.getFirst();
                        List<Message.Attachment> attachments = message.getAttachments();
                        if (!attachments.isEmpty()) {
                            Message.Attachment attachment = attachments.getFirst();
                            attachment.downloadToFile(new File(path));
                            String successText = VineriumLib.inst().getLangManager().getLangLines()
                                    .get(Key.key(VineriumDiscordAddon.inst(), "command_upload_success")).replace("{1}", path);
                            event.reply(successText).setEphemeral(true).queue();
                        }
                        else {
                            event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_upload_no_attachments"))).setEphemeral(true).queue();
                            return;
                        }
                    }
                    else {
                        event.reply(VineriumLib.inst().getLangManager().getLangLines().get(Key.key(VineriumDiscordAddon.inst(), "command_upload_no_messages"))).setEphemeral(true).queue();
                        return;
                    }
                }
            }
        }
    }
}
