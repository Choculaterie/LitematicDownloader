package com.choculaterie.models;

public class MinemevPostDetailInfo {
    private final String uuid;
    private final String title;
    private final String description;
    private final String author;
    private final int downloads;
    private final String createdAt;
    private final String updatedAt;
    private final String[] tags;
    private final String[] versions;
    private final String[] images;
    private final String thumbnailUrl;

    public MinemevPostDetailInfo(String uuid, String title, String description, String author,
                                 int downloads, String createdAt, String updatedAt, String[] tags,
                                 String[] versions, String[] images, String thumbnailUrl) {
        this.uuid = uuid;
        this.title = title;
        this.description = description;
        this.author = author;
        this.downloads = downloads;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.tags = tags;
        this.versions = versions;
        this.images = images;
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getUuid() { return uuid; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public int getDownloads() { return downloads; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public String[] getTags() { return tags; }
    public String[] getVersions() { return versions; }
    public String[] getImages() { return images; }
    public String getThumbnailUrl() { return thumbnailUrl; }
}
