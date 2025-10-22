package com.choculaterie.models;

public class MinemevPostInfo {
    private final String uuid;
    private final String title;
    private final String description;
    private final String author;
    private final int downloads;
    private final String createdAt;
    private final String[] tags;
    private final String[] versions;
    private final String vendor;

    public MinemevPostInfo(String uuid, String title, String description, String author,
                           int downloads, String createdAt, String[] tags, String[] versions, String vendor) {
        this.uuid = uuid;
        this.title = title;
        this.description = description;
        this.author = author;
        this.downloads = downloads;
        this.createdAt = createdAt;
        this.tags = tags != null ? tags : new String[0];
        this.versions = versions != null ? versions : new String[0];
        this.vendor = vendor;
    }

    public String getUuid() { return uuid; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public int getDownloads() { return downloads; }
    public String getCreatedAt() { return createdAt; }
    public String[] getTags() { return tags; }
    public String[] getVersions() { return versions; }
    public String getVendor() { return vendor; }
}
