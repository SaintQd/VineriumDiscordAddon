package org.saintqd.vineriumdiscordaddon.managers;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.objects.MessageFormat;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.TimeUtil;
import github.scarsz.discordsrv.util.WebhookUtil;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;
import org.saintqd.vineriumlib.VineriumLib;
import org.saintqd.vineriumlib.managers.VaultManager;
import org.saintqd.vineriumlib.utils.VinUtils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class MessageFormatManager {

    private final HashMap<NamespacedKey, MessageFormat> messageFormats = new HashMap<>();

    public void registerMessageFormats(Plugin plugin) {
        File formatsFile = new File(plugin.getDataPath() + File.separator + "MessageFormats.yml");
        try {
            if (!formatsFile.exists() && !formatsFile.createNewFile()) {
                return;
            }
        } catch (IOException e) {
            VinUtils.sendDebugMessage(0,"<red>Couldn't message formats file of "+formatsFile+"!");
        }
        YamlConfiguration formatsYaml = YamlConfiguration.loadConfiguration(formatsFile);
        ConfigurationSection messageFormatsConfig = formatsYaml.getConfigurationSection("MessageFormats");
        if (messageFormatsConfig != null) {
            for (String messageKey : messageFormatsConfig.getKeys(false)) {
                MessageFormat message = createMessageFromConfiguration(messageKey,messageFormatsConfig.getConfigurationSection(messageKey));
                messageFormats.put(new NamespacedKey(plugin,messageKey.toLowerCase()),message);
            }
        }
    }

    public void unregisterMessageFormats(Plugin plugin) {
        messageFormats.keySet().removeIf(key -> key.namespace().equals(plugin.getName().toLowerCase()));
    }

    public HashMap<NamespacedKey, MessageFormat> getMessageFormats() {
        return messageFormats;
    }

    public static MessageFormat createMessageFromConfiguration(String messageKey, ConfigurationSection config) {
        MessageFormat messageFormat = new MessageFormat();
        if (config.contains("Embed") && config.getBoolean("Embed.Enabled",true)) {
            String hexColor = config.getString("Embed.Color",null);
            if (hexColor != null) {
                String hex = hexColor.trim();
                if (!hex.startsWith("#")) {
                    hex = "#" + hex;
                }
                if (hex.length() == 7) {
                    messageFormat.setColorRaw(Integer.valueOf(hex.substring(1, 7), 16));
                } else {
                    DiscordSRV.debug("Invalid color hex: " + hex + " (in " + messageKey + ".Embed.Color)");
                }
            } else {
                int colorRaw = config.getInt("Embed.Color",-1);
                if (colorRaw != -1)
                    messageFormat.setColorRaw(colorRaw);
            }
            String embedData = "";
            if (config.contains("Embed.Author")) {
                embedData = config.getString("Embed.Author.Name","");
                if (!embedData.isEmpty())
                    messageFormat.setAuthorName(embedData);
                embedData = config.getString("Embed.Author.Url","");
                if (!embedData.isEmpty())
                    messageFormat.setAuthorUrl(embedData);
                embedData = config.getString("Embed.Author.ImageUrl","");
                if (!embedData.isEmpty())
                    messageFormat.setAuthorImageUrl(embedData);
            }
            embedData = config.getString("Embed.ThumbnailUrl","");
            if (!embedData.isEmpty())
                messageFormat.setThumbnailUrl(embedData);
            embedData = config.getString("Embed.Title.Text","");
            if (!embedData.isEmpty())
                messageFormat.setTitle(embedData);
            embedData = config.getString("Embed.Title.Url","");
            if (!embedData.isEmpty())
                messageFormat.setTitleUrl(embedData);
            embedData = config.getString("Embed.Description","");
            if (!embedData.isEmpty())
                messageFormat.setDescription(embedData);
            List<String> fields = config.getStringList("Embed.Fields");
            if (!fields.isEmpty()) {
                List<MessageEmbed.Field> fieldsList = new ArrayList<>();
                for (String s : fields) {
                    if (s.contains(";")) {
                        String[] parts = s.split(";");
                        if (parts.length >= 2) {
                            boolean inline = parts.length < 3 || Boolean.parseBoolean(parts[2]);
                            fieldsList.add(new MessageEmbed.Field(parts[0], parts[1], inline, true));
                        }
                    } else {
                        boolean inline = Boolean.parseBoolean(s);
                        fieldsList.add(new MessageEmbed.Field("\u200e", "\u200e", inline, true));
                    }
                }
                messageFormat.setFields(fieldsList);
            }
            embedData = config.getString("Embed.ImageUrl","");
            if (!embedData.isEmpty())
                messageFormat.setImageUrl(embedData);
            if (config.contains("Embed.Footer")) {
                embedData = config.getString("Embed.Footer.Text","");
                if (!embedData.isEmpty())
                    messageFormat.setFooterText(embedData);
                embedData = config.getString("Embed.Footer.IconUrl","");
                if (!embedData.isEmpty())
                    messageFormat.setFooterIconUrl(embedData);
            }
            if (config.getBoolean("Embed.Timestamp",false)) {
                messageFormat.setTimestamp((new Date()).toInstant());
            }
        }
        if (config.contains("Webhook") && config.getBoolean("Webhook.Enabled",false)) {
            messageFormat.setUseWebhooks(true);
            String webhookData = config.getString("Webhook.AvatarUrl","");
            if (!webhookData.isEmpty())
                messageFormat.setWebhookAvatarUrl(webhookData);
            webhookData = config.getString("Webhook.Name","");
            if (!webhookData.isEmpty())
                messageFormat.setWebhookName(webhookData);
        }
        String content = config.getString("Content","");
        if (!content.isEmpty()) {
            messageFormat.setContent(content);
        }
        return messageFormat.isAnyContent() ? messageFormat : null;
    }

    public static void runMessageAsync(String channelType, OfflinePlayer actorPlayer, MessageFormat messageFormat, String... args) {
        TextChannel destinationChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(channelType);
        if (destinationChannel == null)
            destinationChannel = DiscordSRV.getPlugin().getJda().getTextChannelById(channelType);
        TextChannel finalDestinationChannel = destinationChannel;
        Message discordMessage = createEmbed(finalDestinationChannel, actorPlayer, messageFormat, args);
        if (discordMessage != null) {
            DiscordUtil.queueMessage(finalDestinationChannel, discordMessage, true);
        }
    }

    public static Message createEmbed(TextChannel channel, OfflinePlayer actorPlayer, MessageFormat messageFormat, String... args) {
        if (messageFormat == null) return null;
        String avatarUrl = actorPlayer != null && actorPlayer.hasPlayedBefore()
                ? DiscordSRV.getAvatarUrl(actorPlayer.getName(),actorPlayer.getUniqueId())
                : "";
        String actorPlayerName = actorPlayer != null && actorPlayer.hasPlayedBefore()
                ? actorPlayer.getName()
                : "null";
        String botAvatarUrl = DiscordUtil.getJda().getSelfUser().getEffectiveAvatarUrl();
        String botName = DiscordSRV.getPlugin().getMainGuild() != null ? DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName() : DiscordUtil.getJda().getSelfUser().getName();
        BiFunction<String, Boolean, String> translator = (content, needsEscape) -> {
            if (content == null) {
                return null;
            } else {
                Date date = new Date(Instant.now().toEpochMilli());
                DateFormat formatter = new SimpleDateFormat("dd.MM.yyyy, HH:mm:ss");
                String dateFormatted = formatter.format(date);

                content = content.replaceAll("%time%|%date%", TimeUtil.timeStamp())
                        .replace("%username%", needsEscape ? DiscordUtil.escapeMarkdown(actorPlayerName) : actorPlayerName)
                        .replace("%displayname%", needsEscape ? DiscordUtil.escapeMarkdown(actorPlayerName) : actorPlayerName)
                        .replace("%usernamenoescapes%", actorPlayerName)
                        .replace("%displaynamenoescapes%", actorPlayerName)
                        .replace("%embedavatarurl%", avatarUrl)
                        .replace("%botavatarurl%", botAvatarUrl)
                        .replace("%botname%", botName);
                int index = 1;
                for (String arg : args) {
                    content = content.replace("{time}",dateFormatted);
                    content = content.replace("{"+index+"}",arg);
                    index++;
                }
                if (channel != null) {
                    content = DiscordUtil.translateEmotes(content, channel.getGuild());
                }
                content = PlaceholderUtil.replacePlaceholdersToDiscord(content, actorPlayer);
                return content;
            }
        };
        return DiscordSRV.translateMessage(messageFormat, translator);
    }

    public void parsePlayerList(String channelName, Message originalMessage) {
        NamespacedKey listKey = NamespacedKey.fromString("vineriumdiscordaddon:player_list");
        if (!messageFormats.containsKey(listKey)) return;

        VaultManager vaultManager = VineriumLib.inst().getVaultManager();
        if (vaultManager == null || vaultManager.getPermissionProvider() == null) return;

        HashMap<String, SortedSet<String>> playersPerGroup = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String primaryGroup = vaultManager.getPermissionProvider().getPrimaryGroup(null,player);
            SortedSet<String> playersInGroup = playersPerGroup.getOrDefault(primaryGroup,new TreeSet<>());
            playersInGroup.add(player.getName());
            playersPerGroup.put(primaryGroup,playersInGroup);
        }
        StringBuilder embedDesc = new StringBuilder();
        embedDesc.append(VineriumLib.inst().getLangManager()
                .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"list_embed_total_players"),
                        "list_embed_total_players").replace("{1}",Integer.toString(Bukkit.getOnlinePlayers().size())));
        embedDesc.append(System.lineSeparator()).append(System.lineSeparator());
        for (String groupInfo : VineriumDiscordAddon.inst().getConfig().getStringList("PlayerListMessage.Groups")) {
            String[] groupData = groupInfo.split(",");
            String groupName = groupData[0];
            String groupDisplay = groupData.length > 1 ? groupData[1] : groupName;
            if (playersPerGroup.containsKey(groupName)) {
                embedDesc.append(groupDisplay).append(System.lineSeparator());
                embedDesc.append("`").append(String.join(", ",playersPerGroup.get(groupName))).append("`");
                embedDesc.append(System.lineSeparator()).append(System.lineSeparator());
            }
        }
        String finalDesc = embedDesc.toString();
        int timeToDelete = VineriumDiscordAddon.inst().getConfig().getInt("PlayerListMessage.DeleteAfter",30000);
        Bukkit.getScheduler().runTaskAsynchronously(VineriumDiscordAddon.inst(),() -> {
            TextChannel destinationChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(channelName);
            if (destinationChannel == null)
                destinationChannel = DiscordSRV.getPlugin().getJda().getTextChannelById(channelName);
            TextChannel finalDestinationChannel = destinationChannel;
            if (finalDestinationChannel == null)
                return;
            Message discordMessage = createEmbed(finalDestinationChannel, null,
                    VineriumDiscordAddon.inst().getMessageFormatManager().getMessageFormats().get(listKey),finalDesc);
            finalDestinationChannel.sendMessage(discordMessage).queue(message -> message.delete().queueAfter(timeToDelete, TimeUnit.MILLISECONDS));
            originalMessage.delete().queueAfter(timeToDelete, TimeUnit.MILLISECONDS);
        });
    }

}
