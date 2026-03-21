package org.saintqd.vineriumdiscordaddon.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;
import org.saintqd.vineriumlib.utils.VinUtils;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.*;

public class SessionManager {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    // UUID и данные о сессии (IP-адрес,время_окончания)
    private final HashMap<UUID,String> sessions = new HashMap<>();
    // UUID и данные о сессии (IP-адрес,время_окончания)
    private final HashMap<UUID,String> completedSessions = new HashMap<>();
    // UUID и IP-адрес
    private final HashMap<UUID,String> premiumUsers = new HashMap<>();

    public HashMap<UUID, String> getSessions() {
        return sessions;
    }

    public HashMap<UUID, String> getCompletedSessions() {
        return completedSessions;
    }

    public HashMap<UUID,String> getPremiumUsers() {
        return premiumUsers;
    }

    public void loadSessions(VineriumDiscordAddon plugin) {
        sessions.clear();
        completedSessions.clear();
        File sessionsFile = new File(plugin.getDataFolder().getPath() + File.separator + "CompletedSessions.yml");
        File parent = sessionsFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            VinUtils.sendDebugMessage(0,"<red>Couldn't create sessions file!");
            return;
        }
        YamlConfiguration sessionsYaml = YamlConfiguration.loadConfiguration(sessionsFile);
        ConfigurationSection sessionsConfig = sessionsYaml.getConfigurationSection("CompletedSessions");
        if (sessionsConfig == null)
            return;
        for (String uuidString : sessionsConfig.getKeys(false)) {
            completedSessions.put(UUID.fromString(uuidString),sessionsConfig.getString(uuidString));
        }
    }

    public void saveSessions(VineriumDiscordAddon plugin) {
        File sessionsFile = new File(plugin.getDataFolder().getPath() + File.separator + "CompletedSessions.yml");
        YamlConfiguration sessionsYaml = YamlConfiguration.loadConfiguration(sessionsFile);
        sessionsYaml.set("CompletedSessions",null);
        long currentTime = Instant.now().toEpochMilli();
        try {
            if (!sessionsFile.exists() && !sessionsFile.createNewFile())
                VinUtils.sendDebugMessage(0,"<red>Couldn't save sessions file to "+ sessionsFile +"!");
            completedSessions.values().removeIf(data -> {
                String[] sessionData = data.split(",");
                long expiryTime = Long.parseLong(sessionData[1]);
                return expiryTime < currentTime;
            });
            for (UUID uuid : completedSessions.keySet()) {
                sessionsYaml.set("CompletedSessions."+uuid.toString(),completedSessions.get(uuid));
            }
            sessionsYaml.save(sessionsFile);
        } catch (IOException e) {
            VinUtils.sendDebugMessage(0,"<red>Couldn't save sessions file to "+ sessionsFile +"!");
        }
    }
}
