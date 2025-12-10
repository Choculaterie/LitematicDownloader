package com.choculaterie.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Manages download settings for schematics
 */
public class DownloadSettings {
    private static final String CONFIG_FILE = "litematic-downloader-settings.json";
    private static DownloadSettings INSTANCE;

    private String downloadPath;
    private final Gson gson;

    private DownloadSettings() {
        gson = new GsonBuilder().setPrettyPrinting().create();
        // Default path is "schematics" (relative to game directory)
        downloadPath = "schematics";
        load();
    }

    public static DownloadSettings getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DownloadSettings();
        }
        return INSTANCE;
    }

    /**
     * Get the relative download path (e.g., "schematics")
     */
    public String getDownloadPath() {
        return downloadPath;
    }

    /**
     * Get the absolute download path resolved from game directory
     */
    public String getAbsoluteDownloadPath() {
        Path gamePath = FabricLoader.getInstance().getGameDir();
        return gamePath.resolve(downloadPath).toString();
    }

    /**
     * Get the game directory path
     */
    public String getGameDirectory() {
        return FabricLoader.getInstance().getGameDir().toString();
    }

    public void setDownloadPath(String path) {
        this.downloadPath = path;
        save();
    }

    private File getConfigFile() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir.resolve(CONFIG_FILE).toFile();
    }

    private void load() {
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            save(); // Create default config
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json != null && json.has("downloadPath")) {
                downloadPath = json.get("downloadPath").getAsString();
            }
        } catch (IOException e) {
            System.err.println("Failed to load download settings: " + e.getMessage());
        }
    }

    private void save() {
        File configFile = getConfigFile();

        try {
            // Ensure config directory exists
            File parentDir = configFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            JsonObject json = new JsonObject();
            json.addProperty("downloadPath", downloadPath);

            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(json, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save download settings: " + e.getMessage());
        }
    }
}

