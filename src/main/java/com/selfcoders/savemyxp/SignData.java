package com.selfcoders.savemyxp;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SignData {
    private final SaveMyXP plugin;
    private final Location location;
    private final ConfigurationSection configSection;

    public SignData(SaveMyXP plugin, Location location) {
        this.plugin = plugin;
        this.location = location;
        this.configSection = getConfigSection("signs." + getConfigSectionPath(location));
    }

    public void set(Player player, int xp) {
        configSection.set("player", player.getUniqueId().toString());
        configSection.set("xp", xp);
    }

    public void remove() {
        plugin.getConfig().set("signs." + getConfigSectionPath(location), null);
    }

    public void addXP(int xp) {
        configSection.set("xp", configSection.getInt("xp") + xp);
    }

    public UUID getUUID() {
        String uuid = configSection.getString("player");
        if (uuid == null) {
            return null;
        }

        return UUID.fromString(uuid);
    }

    public int getXP() {
        return configSection.getInt("xp");
    }

    private ConfigurationSection getConfigSection(String path) {
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection configSection = config.getConfigurationSection(path);
        if (configSection == null) {
            configSection = config.createSection(path);
        }

        return configSection;
    }

    private String getConfigSectionPath(Location location) {
        return location.getWorld().getName() + "," + (int) location.getX() + "," + (int) location.getY() + "," + (int) location.getZ();
    }
}