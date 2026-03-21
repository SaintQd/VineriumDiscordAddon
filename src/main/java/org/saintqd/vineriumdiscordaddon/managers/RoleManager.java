package org.saintqd.vineriumdiscordaddon.managers;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;
import org.saintqd.vineriumlib.VineriumLib;
import org.saintqd.vineriumlib.utils.VinUtils;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RoleManager {

    private final HashMap<String,HashMap<String,Long>> tempAddedRoleData = new HashMap<>();
    private final HashMap<String,HashMap<String,Long>> tempRemovedRoleData = new HashMap<>();

    public HashMap<String, HashMap<String,Long>> getTempAddedRoleData() {
        return tempAddedRoleData;
    }

    public HashMap<String, HashMap<String, Long>> getTempRemovedRoleData() {
        return tempRemovedRoleData;
    }

    public void loadTempRoleData(VineriumDiscordAddon plugin) {
        tempAddedRoleData.clear();
        tempRemovedRoleData.clear();
        File tempRoleDataFile = new File(plugin.getDataFolder().getPath() + File.separator + "TempRoleData.yml");
        File parent = tempRoleDataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            VinUtils.sendDebugMessage(0,"<red>Couldn't create temp role data file!");
            return;
        }
        YamlConfiguration tempRoleYaml = YamlConfiguration.loadConfiguration(tempRoleDataFile);
        ConfigurationSection tempRoleConfig = tempRoleYaml.getConfigurationSection("TempAddedRoleData");
        if (tempRoleConfig != null) {
            for (String discordId : tempRoleConfig.getKeys(false)) {
                HashMap<String, Long> rolesHashMap = new HashMap<>();
                for (String roleId : tempRoleConfig.getConfigurationSection(discordId).getKeys(false))
                    rolesHashMap.put(roleId, tempRoleConfig.getLong(discordId + "." + roleId));
                tempAddedRoleData.put(discordId, rolesHashMap);
            }
        }
        tempRoleConfig = tempRoleYaml.getConfigurationSection("TempRemovedRoleData");
        if (tempRoleConfig != null) {
            for (String discordId : tempRoleConfig.getKeys(false)) {
                HashMap<String, Long> rolesHashMap = new HashMap<>();
                for (String roleId : tempRoleConfig.getConfigurationSection(discordId).getKeys(false))
                    rolesHashMap.put(roleId, tempRoleConfig.getLong(discordId + "." + roleId));
                tempRemovedRoleData.put(discordId, rolesHashMap);
            }
        }
    }

    public void saveTempRoleData(VineriumDiscordAddon plugin) {
        File tempRoleDataFile = new File(plugin.getDataFolder().getPath() + File.separator + "TempRoleData.yml");
        YamlConfiguration tempRoleYaml = YamlConfiguration.loadConfiguration(tempRoleDataFile);
        tempRoleYaml.set("TempAddedRoleData",null);
        long currentTime = Instant.now().toEpochMilli();
        try {
            if (!tempRoleDataFile.exists() && !tempRoleDataFile.createNewFile())
                VinUtils.sendDebugMessage(0,"<red>Couldn't save temp role data file to "+ tempRoleDataFile +"!");
            for (String discordId : tempAddedRoleData.keySet()) {
                HashMap<String,Long> rolesHashMap = tempAddedRoleData.get(discordId);
                if (rolesHashMap != null) {
                    rolesHashMap.values().removeIf(time -> time < currentTime);
                    for (String roleId : rolesHashMap.keySet())
                        tempRoleYaml.set("TempAddedRoleData." + discordId + "." + roleId, rolesHashMap.get(roleId));
                }
            }
            for (String discordId : tempRemovedRoleData.keySet()) {
                HashMap<String,Long> rolesHashMap = tempRemovedRoleData.get(discordId);
                if (rolesHashMap != null) {
                    rolesHashMap.values().removeIf(time -> time < currentTime);
                    for (String roleId : rolesHashMap.keySet())
                        tempRoleYaml.set("TempRemovedRoleData." + discordId + "." + roleId, rolesHashMap.get(roleId));
                }
            }
            tempRoleYaml.save(tempRoleDataFile);
        } catch (IOException e) {
            VinUtils.sendDebugMessage(0,"<red>Couldn't save temp role data file to "+ tempRoleDataFile +"!");
        }
    }

    public void checkTempRoles() {
        long currentTime = Instant.now().toEpochMilli();
        List<ImmutablePair<String,String>> addedRolesToRemove = new ArrayList<>();
        List<ImmutablePair<String,String>> removedRolesToRemove = new ArrayList<>();
        for (String discordId : tempAddedRoleData.keySet()) {
            HashMap<String,Long> rolesHashMap = tempAddedRoleData.get(discordId);
            if (rolesHashMap != null) {
                rolesHashMap.forEach((roleId, timeUntil) -> {
                    if (timeUntil < currentTime)
                        addedRolesToRemove.add(new ImmutablePair<>(discordId, roleId));
                });
                rolesHashMap.values().removeIf(time -> time < currentTime);
            }
            tempAddedRoleData.put(discordId,rolesHashMap);
        }
        for (String discordId : tempRemovedRoleData.keySet()) {
            HashMap<String,Long> rolesHashMap = tempRemovedRoleData.get(discordId);
            if (rolesHashMap != null) {
                rolesHashMap.forEach((roleId, timeUntil) -> {
                    if (timeUntil < currentTime)
                        removedRolesToRemove.add(new ImmutablePair<>(discordId, roleId));
                });
                rolesHashMap.values().removeIf(time -> time < currentTime);
            }
            tempRemovedRoleData.put(discordId,rolesHashMap);
        }
        Bukkit.getScheduler().runTask(VineriumDiscordAddon.inst(), () -> {
            addedRolesToRemove.forEach(pair -> {
                Bukkit.getConsoleSender().sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"remove_role_command_success",pair.getRight(),pair.getLeft()));
            });
            removedRolesToRemove.forEach(pair -> {
                Bukkit.getConsoleSender().sendMessage(VineriumLib.inst().getLangManager().parseLangString(VineriumDiscordAddon.inst(),"add_role_command_success",pair.getRight(),pair.getLeft()));
            });
        });
        Bukkit.getScheduler().runTaskAsynchronously(VineriumDiscordAddon.inst(), () -> {
            if (!addedRolesToRemove.isEmpty()) {
                addedRolesToRemove.forEach(pair -> {
                    Role role = DiscordSRV.getPlugin().getJda().getRoleById(pair.getRight());
                    if (role != null)
                        DiscordSRV.getPlugin().getMainGuild().removeRoleFromMember(pair.getLeft(), role).queue();
                });
            }
            if (!removedRolesToRemove.isEmpty()) {
                removedRolesToRemove.forEach(pair -> {
                    Role role = DiscordSRV.getPlugin().getJda().getRoleById(pair.getRight());
                    if (role != null)
                        DiscordSRV.getPlugin().getMainGuild().addRoleToMember(pair.getLeft(), role).queue();
                });
            }
        });
    }
}
