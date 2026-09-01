package org.saintqd.vineriumdiscordaddon.social;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.ButtonClickEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.components.ActionRow;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.components.Button;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.components.ButtonStyle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;
import org.saintqd.vineriumdiscordaddon.listeners.AuthListener;
import org.saintqd.vineriumdiscordaddon.listeners.ButtonListener;
import org.saintqd.vineriumdiscordaddon.listeners.MessageListener;
import org.saintqd.vineriumlib.VineriumLib;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class DiscordSocial extends AbstractSocial {

    Listener listener = null;

    public DiscordSocial(MessageListener onMessageReceived, ButtonListener onButtonClicked) {
        super(onMessageReceived, onButtonClicked);
    }

    public void registerListener() {
        listener = new Listener(DiscordSRV.getPlugin().getJda(),this::proceedMessage,this::proceedButton);
        DiscordSRV.getPlugin().getJda().addEventListener(listener);
    }

    public void unregisterListener() {
        if (listener != null)
            DiscordSRV.getPlugin().getJda().removeEventListener(listener);
    }

    @Override
    public void sendMessage(Long id, String content, List<List<ButtonItem>> buttons, ButtonVisibility visibility) {
        User user = DiscordSRV.getPlugin().getJda().retrieveUserById(id).complete();
        if (user == null) {
            return;
        }

        List<ActionRow> actionRowList = buttons.stream().map(row ->
                ActionRow.of(row.stream().map(e -> {
                    ButtonStyle style = switch (e.color()) {
                        case RED -> ButtonStyle.DANGER;
                        case GREEN -> ButtonStyle.SUCCESS;
                        case LINK -> ButtonStyle.LINK;
                        case PRIMARY -> ButtonStyle.PRIMARY;
                        default -> ButtonStyle.SECONDARY;
                    };
                    return Button.of(style, e.id(), e.value());
                }).collect(Collectors.toList()))
        ).collect(Collectors.toList());

        String channelName = VineriumDiscordAddon.inst().getConfig().getString("Session.ChannelName","DIRECT");
        long timeUntilDeletion = VineriumDiscordAddon.inst().getConfig().getLong("Buttons.TimeUntilDeletion",180L);

        if (channelName.equals("DIRECT")) {
            user.openPrivateChannel()
                    .submit()
                    .thenAccept(privateChannel -> privateChannel
                            .sendMessage(content)
                            .setActionRows(actionRowList)
                            .submit()
                            .exceptionally(e -> {
                                if (VineriumLib.inst().getDebugLevel() > 0) {
                                    e.printStackTrace(); // printStackTrace is necessary there
                                }
                                return null;
                            }))
                    .exceptionally(e -> {
                        if (VineriumLib.inst().getDebugLevel() > 0) {
                            e.printStackTrace(); // printStackTrace is necessary there
                        }
                        return null;
                    });
        }
        else {
            String channelStringId = DiscordSRV.getPlugin().getChannels().get(channelName);
            long channelId = Long.parseLong(channelStringId);
            TextChannel textChannel = DiscordSRV.getPlugin().getJda().getTextChannelById(channelId);
            if (textChannel == null) return;

            textChannel.sendMessage(content)
                    .setActionRows(actionRowList)
                    .queue(message -> message.delete().queueAfter(timeUntilDeletion, TimeUnit.SECONDS));
        }
    }

    @Override
    public void sendMessage(Player player, String content, List<List<ButtonItem>> buttons, ButtonVisibility visibility) {
        sendMessage(player.getUniqueId(), content,buttons,visibility);
    }

    @Override
    public void sendMessage(UUID uuid, String content, List<List<ButtonItem>> buttons, ButtonVisibility visibility) {
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordIdBypassCache(uuid);
        if (discordId == null)
            discordId = "0";
        this.sendMessage(Long.parseLong(discordId),content, buttons, visibility);
    }

    @Override
    public boolean canSend(Player player) {
        return DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId()) != null;
    }

    private static class Listener extends ListenerAdapter {

        private final List<Role> requiredRoles;
        private final MessageListener onMessageReceived;
        private final ButtonListener onButtonClicked;

        Listener(JDA jda, MessageListener onMessageReceived, ButtonListener onButtonClicked) {
            this.requiredRoles = new ArrayList<>(VineriumDiscordAddon.inst().getConfig()
                    .getStringList("RequiredRoles").stream().map(jda::getRoleById).toList());
            this.requiredRoles.removeIf(Objects::isNull);
            this.onMessageReceived = onMessageReceived;
            this.onButtonClicked = onButtonClicked;
        }

        /*
        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            User user = event.getAuthor();
            if (user.getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
                return;
            }

            for (Role role : this.requiredRoles) {
                Member member = role.getGuild().retrieveMember(user).complete();
                if (member == null || !member.getRoles().contains(role)) {
                    String no_roles_message = VineriumLib.inst().getLangManager()
                            .getLangLines(VineriumDiscordAddon.inst()).getOrDefault("no_roles_message","no_roles_message");
                    user.openPrivateChannel()
                            .submit()
                            .thenAccept(privateChannel -> privateChannel.sendMessage(no_roles_message).queue());
                    return;
                }
            }

            this.onMessageReceived.accept(null,event.getAuthor().getIdLong(), event.getMessage().getContentRaw());
        }*/

        @Override
        public void onButtonClick(@NotNull ButtonClickEvent event) {
            if (event.getButton() == null
                    || !AuthListener.checkButtonAllegiance(event.getButton())
                    || event.isAcknowledged())
                return;
            this.onButtonClicked.accept(event, event.getUser().getIdLong(), event.getButton().getId());
        }

    }

    private static final class RoleAction {
        private final RoleActionType action;
        private final Role role;

        private RoleAction(String[] serializedAction) {
            this(RoleActionType.valueOf(serializedAction[0].toUpperCase(Locale.ROOT)), serializedAction[1]);
        }

        private RoleAction(RoleActionType action, String roleId) {
            this(action, DiscordSRV.getPlugin().getJda().getRoleById(roleId));
        }

        private RoleAction(RoleActionType action, Role role) {
            this.action = action;
            this.role = role;
        }

        public void doAction(Long id) {
            this.action.doAction(this.role, id);
        }
    }

    private enum RoleActionType {
        ADD_ROLE((role, id) -> role.getGuild().addRoleToMember(id, role).queue()),
        REMOVE_ROLE((role, id) -> role.getGuild().removeRoleFromMember(id, role).queue());

        private final BiConsumer<Role, Long> doAction;

        RoleActionType(BiConsumer<Role, Long> doAction) {
            this.doAction = doAction;
        }

        public void doAction(Role role, long userId) {
            this.doAction.accept(role, userId);
        }
    }

}
