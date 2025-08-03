package com.choculaterie.models;

public class SchematicInfo {
    private String id;
    private String name;
    private String description;
    private int viewCount;
    private int downloadCount;
    private String username;

    // Constructor
    public SchematicInfo(String id, String name, String description, int viewCount, int downloadCount, String username) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.viewCount = viewCount;
        this.downloadCount = downloadCount;
        this.username = username;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getViewCount() { return viewCount; }
    public int getDownloadCount() { return downloadCount; }
    public String getUsername() { return username; }

}