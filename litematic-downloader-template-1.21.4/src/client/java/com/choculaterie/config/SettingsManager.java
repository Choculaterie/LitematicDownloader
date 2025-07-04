package com.choculaterie.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SettingsManager {
    private static final String SETTINGS_FILE = "config/litematic-downloader-settings.json";
    private static Settings settings;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static class Settings {
        public String customSchematicsPath = "";
        public boolean useCustomPath = false;
    }

    public static Settings getSettings() {
        if (settings == null) {
            loadSettings();
        }
        return settings;
    }

    public static void loadSettings() {
        try {
            File configDir = new File(MinecraftClient.getInstance().runDirectory, "config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File settingsFile = new File(MinecraftClient.getInstance().runDirectory, SETTINGS_FILE);
            if (settingsFile.exists()) {
                try (FileReader reader = new FileReader(settingsFile)) {
                    settings = gson.fromJson(reader, Settings.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (settings == null) {
            settings = new Settings();
        }
    }

    public static void saveSettings() {
        try {
            File configDir = new File(MinecraftClient.getInstance().runDirectory, "config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File settingsFile = new File(MinecraftClient.getInstance().runDirectory, SETTINGS_FILE);
            try (FileWriter writer = new FileWriter(settingsFile)) {
                gson.toJson(settings, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getSchematicsPath() {
        Settings settings = getSettings();
        if (settings.useCustomPath && !settings.customSchematicsPath.isEmpty()) {
            return settings.customSchematicsPath;
        }
        return getDefaultSchematicsPath();
    }

    public static String getDefaultSchematicsPath() {
        try {
            File gameDir = MinecraftClient.getInstance().runDirectory;
            if (gameDir != null) {
                return new File(gameDir, "schematics").getAbsolutePath();
            } else {
                boolean isDevelopment = System.getProperty("dev.env", "false").equals("true");
                String homeDir = System.getProperty("user.home");

                if (isDevelopment) {
                    return homeDir + File.separator + "Downloads" + File.separator +
                            "litematic-downloader-template-1.21.4" + File.separator +
                            "run" + File.separator + "schematics";
                } else {
                    return homeDir + File.separator + "AppData" + File.separator +
                            "Roaming" + File.separator + ".minecraft" + File.separator +
                            "schematics";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return System.getProperty("user.home") + File.separator + "schematics";
        }
    }

    public static void setCustomPath(String path, boolean useCustom) {
        Settings settings = getSettings();
        settings.customSchematicsPath = path;
        settings.useCustomPath = useCustom;
        saveSettings();
    }
}