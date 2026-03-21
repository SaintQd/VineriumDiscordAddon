package org.saintqd.vineriumdiscordaddon;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import net.kyori.adventure.key.Key;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.saintqd.vineriumdiscordaddon.commands.DiscordSlashCommands;
import org.saintqd.vineriumdiscordaddon.commands.VineriumDiscordAddonCommands;
import org.saintqd.vineriumdiscordaddon.listeners.AuthListener;
import org.saintqd.vineriumdiscordaddon.listeners.DiscordSRVListener;
import org.saintqd.vineriumdiscordaddon.managers.MessageFormatManager;
import org.saintqd.vineriumdiscordaddon.social.DiscordSocial;
import org.saintqd.vineriumdiscordaddon.managers.RoleManager;
import org.saintqd.vineriumdiscordaddon.managers.SessionManager;
import org.saintqd.vineriumdiscordaddon.managers.SocialManager;
import org.saintqd.vineriumdiscordaddon.utils.GeoIp;
import org.saintqd.vineriumlib.VineriumLib;
import org.saintqd.vineriumlib.utils.ResourceUtils;
import org.saintqd.vineriumlib.utils.VinUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class VineriumDiscordAddon extends JavaPlugin {

    private static VineriumDiscordAddon plugin;
    private SocialManager socialManager;
    private SessionManager sessionManager;
    private RoleManager roleManager;
    private MessageFormatManager messageFormatManager;
    private DiscordSRVListener discordSRVListener;
    private GeoIp geoIp;

    private BukkitTask saveTask = null;
    private BukkitTask roleTask = null;

    public static VineriumDiscordAddon inst() {
        return plugin;
    }

    @Override
    public void onLoad() {
        plugin = this;
    }

    @Override
    public void onEnable() {
        try {
            ResourceUtils.fetchAllResources(this,getFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.geoIp = new GeoIp();
        geoIp.fetch();
        this.sessionManager = new SessionManager();
        this.socialManager = new SocialManager(DiscordSocial::new);
        this.roleManager = new RoleManager();
        this.messageFormatManager = new MessageFormatManager();
        roleManager.loadTempRoleData(this);

        loadData();

        VineriumDiscordAddonCommands.setupCommands(this);
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);

        discordSRVListener = new DiscordSRVListener();
        DiscordSRV.api.subscribe(discordSRVListener);

        DiscordSRV.api.addSlashCommandProvider(new DiscordSlashCommands());
        DiscordSRV.api.updateSlashCommands();
    }

    @Override
    public void onDisable() {
        VinUtils.updateJarFile(this,this.getFile());

        if (discordSRVListener != null)
            DiscordSRV.api.unsubscribe(discordSRVListener);

        if (VineriumDiscordAddon.inst().getConfig().getBoolean("Session.DeleteMessagesOnDisable",true)) {
            String channelName = VineriumDiscordAddon.inst().getConfig().getString("Session.ChannelName", "DIRECT");
            String channelStringId = DiscordSRV.getPlugin().getChannels().get(channelName);
            long channelId = Long.parseLong(channelStringId);
            TextChannel textChannel = DiscordSRV.getPlugin().getJda().getTextChannelById(channelId);
            if (textChannel != null) {
                textChannel.purgeMessages(textChannel.getIterableHistory().complete());
            }
        }
        saveData();
        socialManager.unregister();
    }

    public void loadData() {
        // Данные о временных ролях не сбрасываются при перезагрузке, поскольку это критическая информация
        roleManager.checkTempRoles();

        reloadConfig();

        String selectedLang = getConfig().getString("Language");
        HashMap<Key,String> langLines = VineriumLib.inst().getLangManager().loadLanguageFile(this,
                plugin.getDataFolder().getPath() + File.separator + "lang" + File.separator + selectedLang + ".yml");
        VineriumLib.inst().getLangManager().registerLangLines(langLines);

        sessionManager.loadSessions(this);

        messageFormatManager.unregisterMessageFormats(this);
        messageFormatManager.registerMessageFormats(this);

        //Создаем задачу регулярного сохранения данных раз в полчаса
        if (saveTask != null)
            saveTask.cancel();
        saveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, this::saveData, 36000L, 36000L);
        //Создаем задачу проверки временных ролей раз в 10 минут
        if (roleTask != null)
            roleTask.cancel();
        roleTask = getServer().getScheduler().runTaskTimerAsynchronously(this, roleManager::checkTempRoles, 12000L, 12000L);
    }

    public void saveData() {
        VinUtils.sendDebugMessage(0,"Saving session data...");
        sessionManager.saveSessions(this);
        if (!sessionManager.getCompletedSessions().isEmpty())
            VinUtils.sendDebugMessage(0,"Saved "+sessionManager.getCompletedSessions().size()+" sessions.");
        roleManager.saveTempRoleData(this);
        if (!roleManager.getTempAddedRoleData().isEmpty())
            VinUtils.sendDebugMessage(0,"Saved "+roleManager.getTempAddedRoleData().size()+" temp role data.");
    }

    public SocialManager getSocialManager() {
        return socialManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public RoleManager getRoleManager() {
        return roleManager;
    }

    public GeoIp getGeoIp() {
        return geoIp;
    }

    public MessageFormatManager getMessageFormatManager() {
        return messageFormatManager;
    }
}
