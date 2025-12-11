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
    private boolean successToastsEnabled;
    private boolean errorToastsEnabled;
    private boolean infoToastsEnabled;
    private boolean warningToastsEnabled;
    // Sort/Filter settings
    private String sortOption;
    private int itemsPerPage;
    private String tagFilter;
    private String excludedVendors;
    // Dismissed mod message tracking
    private int dismissedModMessageId;
    private final Gson gson;

    private DownloadSettings() {
        gson = new GsonBuilder().setPrettyPrinting().create();
        // Default path is "schematics" (relative to game directory)
        downloadPath = "schematics";
        // Only warning and error toasts enabled by default
        successToastsEnabled = false;
        errorToastsEnabled = true;
        infoToastsEnabled = false;
        warningToastsEnabled = true;
        // Default sort/filter settings
        sortOption = "newest";
        itemsPerPage = 20;
        tagFilter = "";
        excludedVendors = "";
        // No dismissed message by default
        dismissedModMessageId = -1;
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

    /**
     * Check if success toasts are enabled
     */
    public boolean isSuccessToastsEnabled() {
        return successToastsEnabled;
    }

    /**
     * Set whether success toasts are enabled
     */
    public void setSuccessToastsEnabled(boolean enabled) {
        this.successToastsEnabled = enabled;
        save();
    }

    /**
     * Check if error toasts are enabled
     */
    public boolean isErrorToastsEnabled() {
        return errorToastsEnabled;
    }

    /**
     * Set whether error toasts are enabled
     */
    public void setErrorToastsEnabled(boolean enabled) {
        this.errorToastsEnabled = enabled;
        save();
    }

    /**
     * Check if info toasts are enabled
     */
    public boolean isInfoToastsEnabled() {
        return infoToastsEnabled;
    }

    /**
     * Set whether info toasts are enabled
     */
    public void setInfoToastsEnabled(boolean enabled) {
        this.infoToastsEnabled = enabled;
        save();
    }

    /**
     * Check if warning toasts are enabled
     */
    public boolean isWarningToastsEnabled() {
        return warningToastsEnabled;
    }

    /**
     * Set whether warning toasts are enabled
     */
    public void setWarningToastsEnabled(boolean enabled) {
        this.warningToastsEnabled = enabled;
        save();
    }

    // Sort/Filter settings getters and setters
    public String getSortOption() {
        return sortOption;
    }

    public void setSortOption(String sortOption) {
        this.sortOption = sortOption;
        save();
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public void setItemsPerPage(int itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
        save();
    }

    public String getTagFilter() {
        return tagFilter;
    }

    public void setTagFilter(String tagFilter) {
        this.tagFilter = tagFilter != null ? tagFilter : "";
        save();
    }

    public String getExcludedVendors() {
        return excludedVendors;
    }

    public void setExcludedVendors(String excludedVendors) {
        this.excludedVendors = excludedVendors != null ? excludedVendors : "";
        save();
    }

    public int getDismissedModMessageId() {
        return dismissedModMessageId;
    }

    public void setDismissedModMessageId(int messageId) {
        this.dismissedModMessageId = messageId;
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
            if (json != null && json.has("successToastsEnabled")) {
                successToastsEnabled = json.get("successToastsEnabled").getAsBoolean();
            }
            if (json != null && json.has("errorToastsEnabled")) {
                errorToastsEnabled = json.get("errorToastsEnabled").getAsBoolean();
            }
            if (json != null && json.has("infoToastsEnabled")) {
                infoToastsEnabled = json.get("infoToastsEnabled").getAsBoolean();
            }
            if (json != null && json.has("warningToastsEnabled")) {
                warningToastsEnabled = json.get("warningToastsEnabled").getAsBoolean();
            }
            // Sort/Filter settings
            if (json != null && json.has("sortOption")) {
                sortOption = json.get("sortOption").getAsString();
            }
            if (json != null && json.has("itemsPerPage")) {
                itemsPerPage = json.get("itemsPerPage").getAsInt();
            }
            if (json != null && json.has("tagFilter")) {
                tagFilter = json.get("tagFilter").getAsString();
            }
            if (json != null && json.has("excludedVendors")) {
                excludedVendors = json.get("excludedVendors").getAsString();
            }
            if (json != null && json.has("dismissedModMessageId")) {
                dismissedModMessageId = json.get("dismissedModMessageId").getAsInt();
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
            json.addProperty("successToastsEnabled", successToastsEnabled);
            json.addProperty("errorToastsEnabled", errorToastsEnabled);
            json.addProperty("infoToastsEnabled", infoToastsEnabled);
            json.addProperty("warningToastsEnabled", warningToastsEnabled);
            // Sort/Filter settings
            json.addProperty("sortOption", sortOption);
            json.addProperty("itemsPerPage", itemsPerPage);
            json.addProperty("tagFilter", tagFilter);
            json.addProperty("excludedVendors", excludedVendors);
            // Dismissed mod message
            json.addProperty("dismissedModMessageId", dismissedModMessageId);

            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(json, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save download settings: " + e.getMessage());
        }
    }
}

