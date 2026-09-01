package org.saintqd.vineriumdiscordaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import github.scarsz.discordsrv.util.SchedulerUtil;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;
import org.saintqd.vineriumdiscordaddon.managers.MessageFormatManager;
import org.saintqd.vineriumlib.VineriumLib;
import org.saintqd.vineriumlib.utils.VinUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VineriumDiscordAddonCommands {

    private static final Pattern UUID_REGEX = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    public static void setupCommands(VineriumDiscordAddon plugin) {
        LifecycleEventManager<@NotNull Plugin> manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                    Commands.literal("vindiscord")
                            .executes(commandContext -> {
                                commandContext.getSource().getSender().sendMessage(
                                        VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(), "not_enough_arguments"));
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.literal("reload")
                                    .requires(predicate -> predicate.getSender().hasPermission("vineriumdiscord.admin"))
                                    .executes(ctx -> {
                                        reloadCommand(ctx.getSource().getSender());
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                            .then(Commands.literal("savedata")
                                    .requires(predicate -> predicate.getSender().hasPermission("vineriumdiscord.admin"))
                                    .executes(ctx -> {
                                        saveDataCommand(ctx.getSource().getSender());
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                            .then(Commands.literal("fetchgeoip")
                                    .requires(predicate -> predicate.getSender().hasPermission("vineriumdiscord.admin"))
                                    .executes(ctx -> {
                                        fetchGeoIpCommand(ctx.getSource().getSender());
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                            .then(Commands.literal("linkaccount")
                                    .requires(predicate -> predicate.getSender().hasPermission("vineriumdiscord.admin"))
                                    .then(Commands.argument("discord_id", StringArgumentType.string())
                                            .then(Commands.argument("player_name", StringArgumentType.string())
                                                    .suggests((ctx,builder) -> {
                                                        String partName = builder.getRemaining();
                                                        Bukkit.getOnlinePlayers().forEach(onlinePlayer -> {
                                                            if (onlinePlayer.getName().startsWith(partName))
                                                                builder.suggest(onlinePlayer.getName());
                                                        });
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(ctx -> {
                                                        linkAccountCommand(ctx.getSource().getSender(),
                                                                ctx.getLastChild().getArgument("discord_id", String.class),
                                                                ctx.getArgument("player_name", String.class),
                                                                null);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                                    .then(Commands.argument("executor_discord_id", StringArgumentType.string())
                                                            .executes(ctx -> {
                                                                linkAccountCommand(ctx.getSource().getSender(),
                                                                        ctx.getLastChild().getLastChild().getArgument("discord_id", String.class),
                                                                        ctx.getLastChild().getArgument("player_name", String.class),
                                                                        ctx.getArgument("executor_discord_id", String.class));
                                                                return Command.SINGLE_SUCCESS;
                                                            })
                                                    )
                                            )
                                    )
                            )
                            .then(Commands.literal("relinkaccount")
                                    .requires(predicate -> predicate.getSender().hasPermission("vineriumdiscord.admin"))
                                    .then(Commands.argument("old_player_name", StringArgumentType.string())
                                            .suggests((ctx,builder) -> {
                                                String partName = builder.getRemaining();
                                                Bukkit.getOnlinePlayers().forEach(onlinePlayer -> {
                                                    if (onlinePlayer.getName().startsWith(partName))
                                                        builder.suggest(onlinePlayer.getName());
                                                });
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("new_player_name", StringArgumentType.string())
                                                    .suggests((ctx,builder) -> {
                                                        String partName = builder.getRemaining();
                                                        Bukkit.getOnlinePlayers().forEach(onlinePlayer -> {
                                                            if (onlinePlayer.getName().startsWith(partName))
                                                                builder.suggest(onlinePlayer.getName());
                                                        });
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(ctx -> {
                                                        relinkAccountCommand(ctx.getSource().getSender(),
                                                                ctx.getLastChild().getArgument("old_player_name", String.class),
                                                                ctx.getArgument("new_player_name", String.class),
                                                                null);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                                    .then(Commands.argument("executor_discord_id", StringArgumentType.string())
                                                            .executes(ctx -> {
                                                                relinkAccountCommand(ctx.getSource().getSender(),
                                                                        ctx.getLastChild().getLastChild().getArgument("old_player_name", String.class),
                                                                        ctx.getLastChild().getArgument("new_player_name", String.class),
                                                                        ctx.getArgument("executor_discord_id", String.class));
                                                                return Command.SINGLE_SUCCESS;
                                                            })
                                                    )
                                            )
                                    )
                            )
                            .then(Commands.literal("closesession")
                                    .requires(predicate -> predicate.getSender().hasPermission("vineriumdiscord.admin"))
                                    .then(Commands.argument("player_name", StringArgumentType.string())
                                            .suggests((ctx,builder) -> {
                                                String partName = builder.getRemaining();
                                                Bukkit.getOnlinePlayers().forEach(onlinePlayer -> {
                                                    if (onlinePlayer.getName().startsWith(partName))
                                                        builder.suggest(onlinePlayer.getName());
                                                });
                                                return builder.buildFuture();
                                            })
                                            .executes(ctx -> {
                                                closeSessionCommand(ctx.getSource().getSender(),
                                                        ctx.getLastChild().getArgument("player_name", String.class));
                                                return Command.SINGLE_SUCCESS;
                                            })
                                    )
                            )
                            .then(Commands.literal("addrole")
                                    .then(Commands.argument("player_name", StringArgumentType.string())
                                            .suggests((ctx,builder) -> {
                                                String partName = builder.getRemaining();
                                                Bukkit.getOnlinePlayers().forEach(onlinePlayer -> {
                                                    if (onlinePlayer.getName().startsWith(partName))
                                                        builder.suggest(onlinePlayer.getName());
                                                });
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("role_id", StringArgumentType.string())
                                                    .suggests((ctx,builder) -> {
                                                        String partName = builder.getRemaining();
                                                        DiscordSRV.getPlugin().getJda().getRoles().forEach(role -> {
                                                            if (ctx.getSource().getSender().hasPermission("vineriumdiscord.role."+role.getId())) {
                                                                if (role.getId().startsWith(partName))
                                                                    builder.suggest(role.getId());
                                                                if (role.getName().startsWith(partName))
                                                                    builder.suggest(role.getName());
                                                            }
                                                        });
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(ctx -> {
                                                        addRoleCommand(ctx.getSource().getSender(),
                                                                ctx.getLastChild().getArgument("player_name", String.class),
                                                                ctx.getArgument("role_id", String.class),null);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                                    .then(Commands.argument("time", StringArgumentType.word())
                                                            .suggests((ctx,builder) -> {
                                                                builder.suggest("60000ms");
                                                                builder.suggest("30s");
                                                                builder.suggest("5m");
                                                                builder.suggest("1h");
                                                                builder.suggest("1d");
                                                                return builder.buildFuture();
                                                            })
                                                            .executes(ctx -> {
                                                                addRoleCommand(ctx.getSource().getSender(),
                                                                        ctx.getLastChild().getLastChild().getArgument("player_name", String.class),
                                                                        ctx.getLastChild().getArgument("role_id", String.class),
                                                                        ctx.getArgument("time",String.class));
                                                                return Command.SINGLE_SUCCESS;
                                                            })
                                                    )
                                            )
                                    )
                            )
                            .then(Commands.literal("removerole")
                                    .then(Commands.argument("player_name", StringArgumentType.string())
                                            .suggests((ctx,builder) -> {
                                                String partName = builder.getRemaining();
                                                Bukkit.getOnlinePlayers().forEach(onlinePlayer -> {
                                                    if (onlinePlayer.getName().startsWith(partName))
                                                        builder.suggest(onlinePlayer.getName());
                                                });
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("role_id", StringArgumentType.string())
                                                    .suggests((ctx,builder) -> {
                                                        String partName = builder.getRemaining();
                                                        DiscordSRV.getPlugin().getJda().getRoles().forEach(role -> {
                                                            if (ctx.getSource().getSender().hasPermission("vineriumdiscord.role."+role.getId())) {
                                                                if (role.getId().startsWith(partName))
                                                                    builder.suggest(role.getId());
                                                                if (role.getName().startsWith(partName))
                                                                    builder.suggest(role.getName());
                                                            }
                                                        });
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(ctx -> {
                                                        removeRoleCommand(ctx.getSource().getSender(),
                                                                ctx.getLastChild().getArgument("player_name", String.class),
                                                                ctx.getArgument("role_id", String.class),null);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                                    .then(Commands.argument("time", StringArgumentType.word())
                                                            .suggests((ctx,builder) -> {
                                                                builder.suggest("60000ms");
                                                                builder.suggest("30s");
                                                                builder.suggest("5m");
                                                                builder.suggest("1h");
                                                                builder.suggest("1d");
                                                                return builder.buildFuture();
                                                            })
                                                            .executes(ctx -> {
                                                                removeRoleCommand(ctx.getSource().getSender(),
                                                                        ctx.getLastChild().getLastChild().getArgument("player_name", String.class),
                                                                        ctx.getLastChild().getArgument("role_id", String.class),
                                                                        ctx.getArgument("time",String.class));
                                                                return Command.SINGLE_SUCCESS;
                                                            })
                                                    )
                                            )
                                    )
                            )
                            .then(Commands.literal("checktemproles")
                                    .requires(predicate -> predicate.getSender().hasPermission("vineriumdiscord.admin"))
                                    .executes(ctx -> {
                                        checkTempRolesCommand(ctx.getSource().getSender());
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                            .then(Commands.literal("broadcast")
                                    .requires(ctx -> ctx.getSender().hasPermission("vineriumdiscord.broadcast"))
                                    .then(Commands.argument("channel", StringArgumentType.word())
                                            .suggests((ctx,builder) -> {
                                                DiscordSRV.getPlugin().getChannels().forEach((channel, data) -> builder.suggest(channel));
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("messageFormatName", ArgumentTypes.namespacedKey())
                                                    .suggests((ctx,builder) -> {
                                                        String partName = builder.getRemaining();
                                                        VineriumDiscordAddon.inst().getMessageFormatManager().getMessageFormats().forEach((key, guiName) -> {
                                                            String keyString = key.asString();
                                                            if (keyString.startsWith(partName.toLowerCase()))
                                                                builder.suggest(keyString);
                                                        });
                                                        return builder.buildFuture();
                                                    })
                                                    .then(Commands.argument("actorPlayerName", StringArgumentType.string())
                                                            .suggests((ctx,builder) -> {
                                                                Bukkit.getOnlinePlayers().forEach((player) -> {
                                                                    builder.suggest(player.getName());
                                                                });
                                                                builder.suggest("NONE");
                                                                return builder.buildFuture();
                                                            })
                                                            .then(Commands.argument("args", StringArgumentType.greedyString())
                                                                    .executes(ctx -> {
                                                                        broadcastEmbedMessage(ctx.getSource().getSender(),
                                                                                ctx.getLastChild().getLastChild().getLastChild().getArgument("channel", String.class),
                                                                                ctx.getLastChild().getLastChild().getArgument("messageFormatName", NamespacedKey.class),
                                                                                ctx.getLastChild().getArgument("actorPlayerName", String.class),
                                                                                ctx.getArgument("args", String.class)
                                                                        );
                                                                        return Command.SINGLE_SUCCESS;
                                                                    })
                                                            )
                                                    )
                                            )
                                    )
                            )
                            .then(Commands.literal("togglechat")
                                    .requires(ctx -> VineriumLib.inst().getVaultManager() != null
                                            && ctx.getSender().hasPermission("vineriumdiscord.togglechat"))
                                    .executes(ctx -> {
                                        toggleDiscordMessagesCommand(ctx.getSource().getSender(),null);
                                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                    })
                                    .then(Commands.argument("player", ArgumentTypes.player())
                                            .requires(predicate -> predicate.getSender().hasPermission("vineriumcore.admin"))
                                            .executes(ctx -> {
                                                toggleDiscordMessagesCommand(ctx.getSource().getSender(),ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst());
                                                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                            })
                                    )
                            )
                            .build(),
                    "Основная команда."
            );
        });
    }

    private static void reloadCommand(CommandSender sender) {
        VineriumDiscordAddon.inst().loadData();
        sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"reload_message"));
    }

    private static void saveDataCommand(CommandSender sender) {
        VineriumDiscordAddon.inst().saveData();
        sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"save_data_message"));
    }

    private static void fetchGeoIpCommand(CommandSender sender) {
        VineriumDiscordAddon.inst().getGeoIp().fetch(sender);
    }

    private static void linkAccountCommand(CommandSender sender, String discordId, String playerName, String executorDiscordId) {
        String uuidString = "OfflinePlayer:";
        uuidString = uuidString.concat(playerName);
        UUID testUuid = UUID.nameUUIDFromBytes(uuidString.getBytes(StandardCharsets.UTF_8));

        UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        Player senderPlayer = sender instanceof Player ? (Player) sender : null;
        sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"linking_started",playerName,discordId));
        if (sender != Bukkit.getConsoleSender())
            Bukkit.getConsoleSender().sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"linking_started",playerName,discordId));

        Bukkit.getScheduler().runTaskAsynchronously(VineriumDiscordAddon.inst(), () -> {

            DiscordSRV.getPlugin().getAccountLinkManager().link(discordId,uuid);
            long currentTime = Instant.now().toEpochMilli();
            long expireTime = currentTime + VineriumDiscordAddon.inst().getConfig().getLong("Session.ExpireTime",86400000L);
            VineriumDiscordAddon.inst().getSessionManager().getCompletedSessions().put(uuid,"localhost,"+expireTime);

            String channelName = VineriumDiscordAddon.inst().getConfig().getString("Logging.LinkedMessageChannelName");
            NamespacedKey messageFormat = new NamespacedKey(VineriumDiscordAddon.inst(),"discord_auth_linked");

            User user = DiscordSRV.getPlugin().getJda().getUserById(discordId);
            String userMention = user != null ? user.getAsMention() : "NONE";
            String possibleExecutorId = executorDiscordId != null ? executorDiscordId : "0";
            if (possibleExecutorId.equals("0") && senderPlayer != null) {
                possibleExecutorId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordIdBypassCache(senderPlayer.getUniqueId());
                if (possibleExecutorId == null)
                    possibleExecutorId = "0";
            }
            User executorUser = DiscordSRV.getPlugin().getJda().getUserById(possibleExecutorId);
            String executorUserMention = executorUser != null ? executorUser.getAsMention() : "NONE";

            List<String> args = List.of(playerName,uuid.toString(),userMention,executorUserMention);
            MessageFormatManager.runMessageAsync(channelName, null,
                    VineriumDiscordAddon.inst().getMessageFormatManager().getMessageFormats().get(messageFormat),args.toArray(new String[0]));

            Bukkit.getScheduler().runTask(VineriumDiscordAddon.inst(), () -> {
                sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"linking_completed",discordId,playerName,uuid.toString()));
                if (sender != Bukkit.getConsoleSender())
                    Bukkit.getConsoleSender().sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"linking_completed",discordId,playerName,uuid.toString()));
            });
        });
    }

    private static void relinkAccountCommand(CommandSender sender, String oldPLayerName, String newPlayerName, String executorDiscordId) {
        OfflinePlayer oldOfflinePlayer = Bukkit.getOfflinePlayer(oldPLayerName);
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordIdBypassCache(oldOfflinePlayer.getUniqueId());
        if (discordId == null) {
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"discord_user_not_found",oldPLayerName));
            return;
        }
        linkAccountCommand(sender,discordId,newPlayerName,executorDiscordId);
    }

    private static void closeSessionCommand(CommandSender sender, String playerName) {

        Matcher matcher = UUID_REGEX.matcher(playerName);
        UUID uuid = matcher.matches() ? UUID.fromString(playerName) : Bukkit.getOfflinePlayer(playerName).getUniqueId();

        VineriumDiscordAddon.inst().getSessionManager().getSessions().remove(uuid);
        VineriumDiscordAddon.inst().getSessionManager().getCompletedSessions().remove(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            player.kick(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"session_closed_kick_message"));

        sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"session_closed_command",playerName));
    }

    private static void addRoleCommand(CommandSender sender, String playerName, String roleId, String time) {

        if (!sender.hasPermission("vineriumdiscord.role."+roleId)) {
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"no_role_command_permission_message"));
            return;
        }

        Matcher matcher = UUID_REGEX.matcher(playerName);
        UUID uuid = matcher.matches() ? UUID.fromString(playerName) : Bukkit.getOfflinePlayer(playerName).getUniqueId();

        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordIdBypassCache(uuid);
        if (discordId == null) {
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"discord_user_not_found",playerName));
            return;
        }
        Role role = roleId.matches("\\d+")
                ? DiscordSRV.getPlugin().getJda().getRoleById(roleId)
                : DiscordSRV.getPlugin().getJda().getRolesByName(roleId,true).getFirst();
        if (role == null) {
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"discord_role_not_found",roleId));
            return;
        }

        String tempRoleUntilString;
        if (time != null) {
            long currentTime = Instant.now().toEpochMilli();
            long parsedTime = Long.parseLong(time.replaceAll("\\D", "")) * 1000 * 60;
            if (time.endsWith("ms"))
                parsedTime = parsedTime / 1000 / 60;
            else if (time.endsWith("s"))
                parsedTime = parsedTime / 60;
            else if (time.endsWith("h"))
                parsedTime = parsedTime * 60;
            else if (time.endsWith("d"))
                parsedTime = parsedTime * 60 * 24;
            HashMap<String,Long> tempRoleData = VineriumDiscordAddon.inst().getRoleManager().getTempAddedRoleData().getOrDefault(discordId,new HashMap<>());
            tempRoleData.put(roleId,currentTime + parsedTime);
            VineriumDiscordAddon.inst().getRoleManager().getTempAddedRoleData().put(discordId,tempRoleData);
            Instant instant = Instant.ofEpochMilli(currentTime + parsedTime);
            ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
            tempRoleUntilString = zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } else {
            tempRoleUntilString = null;
        }

        HashMap<String,Long> tempRoleData = VineriumDiscordAddon.inst().getRoleManager().getTempRemovedRoleData().get(discordId);
        if (tempRoleData != null) {
            tempRoleData.remove(roleId);
            VineriumDiscordAddon.inst().getRoleManager().getTempRemovedRoleData().put(discordId,tempRoleData);
        }

        sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"sending_request_message",discordId));
        Bukkit.getScheduler().runTaskAsynchronously(VineriumDiscordAddon.inst(), () -> {
            DiscordSRV.getPlugin().getMainGuild().addRoleToMember(discordId, role).queue(success -> {
                Bukkit.getScheduler().runTask(VineriumDiscordAddon.inst(), () -> {
                    sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"add_role_command_success",role.getId(),playerName));
                    if (tempRoleUntilString != null) {
                        sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"add_role_until_time",tempRoleUntilString));
                    }
                });
            });
        });
    }

    private static void removeRoleCommand(CommandSender sender, String playerName, String roleId, String time) {

        if (!sender.hasPermission("vineriumdiscord.role."+roleId)) {
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"no_role_command_permission_message"));
            return;
        }

        Matcher matcher = UUID_REGEX.matcher(playerName);
        UUID uuid = matcher.matches() ? UUID.fromString(playerName) : Bukkit.getOfflinePlayer(playerName).getUniqueId();

        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordIdBypassCache(uuid);
        if (discordId == null) {
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"discord_user_not_found",playerName));
            return;
        }
        Role role = roleId.matches("\\d+")
                ? DiscordSRV.getPlugin().getJda().getRoleById(roleId)
                : DiscordSRV.getPlugin().getJda().getRolesByName(roleId,true).getFirst();
        if (role == null) {
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"discord_role_not_found",roleId));
            return;
        }

        String tempRoleUntilString;
        if (time != null) {
            long currentTime = Instant.now().toEpochMilli();
            long parsedTime = Long.parseLong(time.replaceAll("\\D", "")) * 1000 * 60;
            if (time.endsWith("ms"))
                parsedTime = parsedTime / 1000 / 60;
            else if (time.endsWith("s"))
                parsedTime = parsedTime / 60;
            else if (time.endsWith("h"))
                parsedTime = parsedTime * 60;
            else if (time.endsWith("d"))
                parsedTime = parsedTime * 60 * 24;
            HashMap<String,Long> tempRoleData = VineriumDiscordAddon.inst().getRoleManager().getTempRemovedRoleData().getOrDefault(discordId,new HashMap<>());
            tempRoleData.put(roleId,currentTime + parsedTime);
            VineriumDiscordAddon.inst().getRoleManager().getTempRemovedRoleData().put(discordId,tempRoleData);
            Instant instant = Instant.ofEpochMilli(currentTime + parsedTime);
            ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
            tempRoleUntilString = zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } else {
            tempRoleUntilString = null;
        }

        HashMap<String,Long> tempRoleData = VineriumDiscordAddon.inst().getRoleManager().getTempAddedRoleData().get(discordId);
        if (tempRoleData != null) {
            tempRoleData.remove(roleId);
            VineriumDiscordAddon.inst().getRoleManager().getTempAddedRoleData().put(discordId,tempRoleData);
        }

        sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"sending_request_message",discordId));
        Bukkit.getScheduler().runTaskAsynchronously(VineriumDiscordAddon.inst(), () -> {
            DiscordSRV.getPlugin().getMainGuild().removeRoleFromMember(discordId, role).queue(success -> {
                Bukkit.getScheduler().runTask(VineriumDiscordAddon.inst(), () -> {
                    sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"remove_role_command_success",role.getId(),playerName));
                    if (tempRoleUntilString != null) {
                        sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"remove_role_until_time",tempRoleUntilString));
                    }
                });
            });
        });
    }

    private static void checkTempRolesCommand(CommandSender sender) {
        VineriumDiscordAddon.inst().getRoleManager().checkTempRoles();
        sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"check_temp_roles_message"));
    }

    private static void broadcastEmbedMessage(CommandSender sender, String channelName, NamespacedKey messageFormat, String actorPlayerName, String args) {

        if (!VineriumDiscordAddon.inst().getMessageFormatManager().getMessageFormats().containsKey(messageFormat)) {
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"command_broadcast_no_message_format"));
            return;
        }

        String[] argsSplit = args.split(";");
        OfflinePlayer actorPlayer = Bukkit.getOfflinePlayer(actorPlayerName);
        SchedulerUtil.runTaskAsynchronously(DiscordSRV.getPlugin(), () ->
                MessageFormatManager.runMessageAsync(channelName, actorPlayer,
                        VineriumDiscordAddon.inst().getMessageFormatManager().getMessageFormats().get(messageFormat),argsSplit));
    }

    private static void toggleDiscordMessagesCommand(CommandSender sender, Player player) {

        player = VinUtils.checkForPlayerPresent(sender,player);
        if (player == null) return;

        TriState permissionState = player.permissionValue("vineriumdiscord.toggleenabled");
        if (permissionState == TriState.FALSE || permissionState == TriState.NOT_SET) {
            VineriumLib.inst().getVaultManager().getPermissionProvider().playerAdd(null,player, "vineriumdiscord.toggleenabled");
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"discord_messages_toggle_off"));
        }
        else {
            VineriumLib.inst().getVaultManager().getPermissionProvider().playerRemove(null,player, "vineriumdiscord.toggleenabled");
            sender.sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"discord_messages_toggle_on"));
        }
    }
}
