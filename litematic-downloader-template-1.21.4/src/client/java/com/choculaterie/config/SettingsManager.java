package com.choculaterie.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

public class SettingsManager {
    private static final String SETTINGS_FILE = "config/litematic-downloader-settings.json";
    private static final String KEY_FILE = "config/.ld-key";
    private static Settings settings;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static SecretKey encryptionKey;

    public static class Settings {
        public String customSchematicsPath = "";
        public boolean useCustomPath = false;
        public String encryptedApiToken = "";
        public String customImagesPath = "";
        public boolean useCustomImagesPath = false;
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

    public static String getImagesPath() {
        Settings settings = getSettings();
        if (settings.useCustomImagesPath && !settings.customImagesPath.isEmpty()) {
            return settings.customImagesPath;
        }
        return getDefaultImagesPath();
    }

    public static String getDefaultImagesPath() {
        try {
            File gameDir = MinecraftClient.getInstance().runDirectory;
            if (gameDir != null) {
                return new File(gameDir, "screenshots").getAbsolutePath();
            } else {
                boolean isDevelopment = System.getProperty("dev.env", "false").equals("true");
                String homeDir = System.getProperty("user.home");

                if (isDevelopment) {
                    return homeDir + File.separator + "Downloads" + File.separator +
                            "litematic-downloader-template-1.21.4" + File.separator +
                            "run" + File.separator + "screenshots";
                } else {
                    return homeDir + File.separator + "AppData" + File.separator +
                            "Roaming" + File.separator + ".minecraft" + File.separator +
                            "screenshots";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return System.getProperty("user.home") + File.separator + "Pictures";
        }
    }

    public static void setCustomImagesPath(String path, boolean useCustom) {
        Settings settings = getSettings();
        settings.customImagesPath = path;
        settings.useCustomImagesPath = useCustom;
        saveSettings();
    }

    /**
     * Gets the decrypted API token
     */
    public static String getApiToken() {
        Settings settings = getSettings();
        if (settings.encryptedApiToken == null || settings.encryptedApiToken.isEmpty()) {
            return "";
        }

        try {
            return decryptToken(settings.encryptedApiToken);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Sets and encrypts the API token
     */
    public static void setApiToken(String token) {
        Settings settings = getSettings();
        if (token == null || token.trim().isEmpty()) {
            settings.encryptedApiToken = "";
        } else {
            try {
                settings.encryptedApiToken = encryptToken(token.trim());
            } catch (Exception e) {
                e.printStackTrace();
                settings.encryptedApiToken = "";
            }
        }
        saveSettings();
    }

    /**
     * Clears the stored API token
     */
    public static void clearApiToken() {
        setApiToken("");
    }

    /**
     * Checks if an API token is stored
     */
    public static boolean hasApiToken() {
        Settings settings = getSettings();
        return settings.encryptedApiToken != null && !settings.encryptedApiToken.isEmpty();
    }

    /**
     * Gets or creates the encryption key
     */
    private static SecretKey getEncryptionKey() {
        if (encryptionKey != null) {
            return encryptionKey;
        }

        try {
            File configDir = new File(MinecraftClient.getInstance().runDirectory, "config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File keyFile = new File(MinecraftClient.getInstance().runDirectory, KEY_FILE);

            if (keyFile.exists()) {
                // Load existing key
                byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
                encryptionKey = new SecretKeySpec(keyBytes, "AES");
            } else {
                // Generate new key
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256, new SecureRandom());
                encryptionKey = keyGen.generateKey();

                // Save key to file
                Files.write(keyFile.toPath(), encryptionKey.getEncoded());

                // Hide the key file on Windows
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    try {
                        Runtime.getRuntime().exec("attrib +H \"" + keyFile.getAbsolutePath() + "\"");
                    } catch (Exception e) {
                        // Ignore if we can't hide the file
                    }
                }
            }

            return encryptionKey;
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to a default key (not secure, but better than nothing)
            String fallbackKey = "LitematicDownloader2024!@#$%^&*";
            return new SecretKeySpec(fallbackKey.substring(0, 32).getBytes(), "AES");
        }
    }

    /**
     * Encrypts the API token
     */
    private static String encryptToken(String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, getEncryptionKey());
        byte[] encryptedBytes = cipher.doFinal(token.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * Decrypts the API token
     */
    private static String decryptToken(String encryptedToken) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, getEncryptionKey());
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedToken));
        return new String(decryptedBytes);
    }
}