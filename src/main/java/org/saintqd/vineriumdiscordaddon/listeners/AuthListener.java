package org.saintqd.vineriumdiscordaddon.listeners;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.components.Button;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;
import org.saintqd.vineriumdiscordaddon.managers.MessageFormatManager;
import org.saintqd.vineriumdiscordaddon.social.AbstractSocial;
import org.saintqd.vineriumdiscordaddon.managers.SessionManager;
import org.saintqd.vineriumlib.VineriumLib;

import java.time.Instant;
import java.util.*;

public class AuthListener implements Listener {

    private static final String ASK_NO_BTN = "ask_no" + UUID.randomUUID();
    private static final String ASK_YES_BTN = "ask_yes" + UUID.randomUUID();

    private boolean loginLock = false;

    public static boolean checkButtonAllegiance(Button button) {
        if (button.getId() == null)
            return false;
        return checkButtonAllegiance(button.getId());
    }

    public static boolean checkButtonAllegiance(String buttonId) {
        return buttonId.equals(ASK_YES_BTN) || buttonId.equals(ASK_NO_BTN);
    }

    List<List<AbstractSocial.ButtonItem>> yesNoButtons;

    public AuthListener(VineriumDiscordAddon plugin) {

        String yesValue = VineriumLib.inst().getLangManager()
                .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"button_yes_text"),"button_yes_text");
        String noValue = VineriumLib.inst().getLangManager()
                .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"button_no_text"),"button_no_text");

        SessionManager sessionManager = VineriumDiscordAddon.inst().getSessionManager();

        if (plugin.getConfig().getBoolean("Buttons.EnableNoButton",false)) {
            if (!plugin.getConfig().getBoolean("Buttons.ReverseLayout",false)) {
                this.yesNoButtons = List.of(
                        List.of(
                                new AbstractSocial.ButtonItem(ASK_YES_BTN, yesValue, AbstractSocial.ButtonItem.Color. GREEN),
                                new AbstractSocial.ButtonItem(ASK_NO_BTN, noValue, AbstractSocial.ButtonItem.Color.RED)
                        )
                );
            } else {
                this.yesNoButtons = List.of(
                        List.of(
                                new AbstractSocial.ButtonItem(ASK_NO_BTN, noValue, AbstractSocial.ButtonItem.Color.RED),
                                new AbstractSocial.ButtonItem(ASK_YES_BTN, yesValue, AbstractSocial.ButtonItem.Color.GREEN)
                        )
                );
            }
        }
        else {
            this.yesNoButtons = List.of(
                    List.of(
                            new AbstractSocial.ButtonItem(ASK_YES_BTN, yesValue, AbstractSocial.ButtonItem.Color.GREEN)
                    )
            );
        }
        plugin.getSocialManager().registerKeyboard(this.yesNoButtons);
        plugin.getSocialManager().removeButtonEvent(ASK_NO_BTN);
        plugin.getSocialManager().removeButtonEvent(ASK_YES_BTN);

        plugin.getSocialManager().addButtonEvent(ASK_NO_BTN, (event,id, buttonId) -> {

            UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuidBypassCache(Long.toString(id));
            if (uuid == null) {
                String denyMessage = VineriumLib.inst().getLangManager()
                        .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"button_deny"),"button_deny");
                event.deferReply().setEphemeral(true).queue();
                event.getHook().sendMessage(denyMessage).queue();
                return;
            }

            if (sessionManager.getSessions().containsKey(uuid)) {
                sessionManager.getSessions().remove(uuid);
                String noConfirm = VineriumLib.inst().getLangManager()
                        .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"button_no_confirm"),"button_no_confirm");
                event.deferReply().setEphemeral(true).queue();
                event.getHook().sendMessage(noConfirm).queue();
            }
            else {
                String denyMessage = VineriumLib.inst().getLangManager()
                        .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"button_deny"),"button_deny");
                event.deferReply().setEphemeral(true).queue();
                event.getHook().sendMessage(denyMessage).queue();
            }
        });

        plugin.getSocialManager().addButtonEvent(ASK_YES_BTN, (event,id, buttonId) -> {

            UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuidBypassCache(Long.toString(id));
            if (uuid == null) {
                String denyMessage = VineriumLib.inst().getLangManager()
                        .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"button_deny"),"button_deny");
                event.deferReply().setEphemeral(true).queue();
                event.getHook().sendMessage(denyMessage).queue();
                return;
            }

            if (sessionManager.getSessions().containsKey(uuid)) {
                String[] sessionData = sessionManager.getSessions().get(uuid).split(",");
                String ip = sessionData[0];
                long expireTime = Instant.now().toEpochMilli();
                expireTime += plugin.getConfig().getLong("Session.ExpireTime",86400000L);
                sessionManager.getCompletedSessions().put(uuid,ip+","+expireTime);
                sessionManager.getSessions().remove(uuid);

                String channelName = VineriumDiscordAddon.inst().getConfig().getString("Logging.JoinConfirmMessageChannelName");
                NamespacedKey messageFormat = new NamespacedKey(VineriumDiscordAddon.inst(),"discord_auth_confirm");

                User user = DiscordSRV.getPlugin().getJda().getUserById(id);
                if (user != null) {
                    List<String> args = List.of(user.getAsMention(), ip);
                    MessageFormatManager.runMessageAsync(channelName, null,
                            VineriumDiscordAddon.inst().getMessageFormatManager().getMessageFormats().get(messageFormat), args.toArray(new String[0]));
                }

                String yesConfirm = VineriumLib.inst().getLangManager()
                        .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"button_yes_confirm"),"button_yes_confirm");
                event.deferReply().setEphemeral(true).queue();
                event.getHook().sendMessage(yesConfirm).queue();
            }
            else {
                String denyMessage = VineriumLib.inst().getLangManager()
                        .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"button_deny"),"button_deny");
                event.deferReply().setEphemeral(true).queue();
                event.getHook().sendMessage(denyMessage).queue();
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED)
            return;
        if (!VineriumDiscordAddon.inst().getConfig().getBoolean("Auth.Enabled",true))
            return;
        if (loginLock) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"join_limit_reached"));
            return;
        }

        loginLock = true;
        long currentTime = Instant.now().toEpochMilli();
        String[] hostname = event.getAddress().getHostAddress().split(":");
        String ip = hostname[0];
        SessionManager sessionManager = VineriumDiscordAddon.inst().getSessionManager();
        Component messageComponent = Component.empty();

        boolean allowed = true;
        if (sessionManager.getCompletedSessions().containsKey(event.getUniqueId())) {
            String[] sessionData = sessionManager.getCompletedSessions().get(event.getUniqueId()).split(",");
            long expiryTime = Long.parseLong(sessionData[1]);
            if (!ip.startsWith(sessionData[0])) {
                messageComponent = messageComponent.append(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"notify_ask_validate_new_hostname"));
                messageComponent = messageComponent.appendNewline();
                allowed = false;
            }
            else if (expiryTime < currentTime) {
                messageComponent = messageComponent.append(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"notify_ask_validate_session_expired"));
                messageComponent = messageComponent.appendNewline();
                allowed = false;
            }
        }
        else
            allowed = false;
        if (allowed) {
            long expireTime = currentTime + VineriumDiscordAddon.inst().getConfig().getLong("Session.ExpireTime",86400000L);
            sessionManager.getCompletedSessions().put(event.getUniqueId(),ip+","+expireTime);
        }
        else {

            if (sessionManager.getSessions().containsKey(event.getUniqueId())) {
                String[] sessionData = sessionManager.getSessions().get(event.getUniqueId()).split(",");
                long expiryTime = Long.parseLong(sessionData[1]);
                if (expiryTime > currentTime) {
                    messageComponent = messageComponent.append(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"notify_ask_validate_in_game"));
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,messageComponent);
                    loginLock = false;
                    return;
                }
            }

            String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordIdBypassCache(event.getUniqueId());
            User user = DiscordSRV.getPlugin().getJda().getUserById(discordId);
            if (user == null) {
                messageComponent = VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"notify_ask_validate_no_account");
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,messageComponent);
                loginLock = false;
                return;
            }
            String mentionTag = user.getAsMention();

            long expireTime = currentTime + VineriumDiscordAddon.inst().getConfig().getLong("Session.UnconfirmedExpireTime",180000L);
            sessionManager.getSessions().put(event.getUniqueId(), ip+","+expireTime);

            Component discordMessage = VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"notify_ask_validate",mentionTag, event.getName(), ip,
                    Optional.ofNullable(VineriumDiscordAddon.inst().getGeoIp()).map(nonNullGeo -> nonNullGeo.getLocation(ip)).orElse(""));
            messageComponent = messageComponent.append(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"notify_ask_validate_in_game"));
            String message = PlainTextComponentSerializer.plainText().serialize(discordMessage);
            VineriumDiscordAddon.inst().getSocialManager().broadcastMessage(Long.parseLong(discordId), message,
                    this.yesNoButtons, AbstractSocial.ButtonVisibility.PREFER_INLINE);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,messageComponent);
        }
        loginLock = false;
    }

}
