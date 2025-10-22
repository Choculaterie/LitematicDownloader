package com.choculaterie.models;

public class SchematicInfo {
    public enum SourceServer { CHOCULATERIE, MINEMEV }

    private String id;
    private String name;
    private String description;
    private int viewCount;
    private int downloadCount;
    private String username;
    private SourceServer source = SourceServer.CHOCULATERIE; // default for legacy callers
    private  String vendor;

    // Constructor (legacy) defaults to CHOCULATERIE
    public SchematicInfo(String id, String name, String description, int viewCount, int downloadCount, String username, String vendor) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.viewCount = viewCount;
        this.downloadCount = downloadCount;
        this.username = username;
        this.source = SourceServer.CHOCULATERIE;
        this.vendor = vendor;
    }

    // New constructor allowing explicit source
    public SchematicInfo(String id, String name, String description, int viewCount, int downloadCount, String username, SourceServer source, String vendor) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.viewCount = viewCount;
        this.downloadCount = downloadCount;
        this.username = username;
        this.source = source == null ? SourceServer.CHOCULATERIE : source;
        this.vendor = vendor;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getViewCount() { return viewCount; }
    public int getDownloadCount() { return downloadCount; }
    public String getUsername() { return username; }
    public SourceServer getSource() { return source; }
    public String getVendor() { return vendor; }
}