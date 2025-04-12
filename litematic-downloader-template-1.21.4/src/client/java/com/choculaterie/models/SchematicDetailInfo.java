package com.choculaterie.models;

import java.util.List;

public class SchematicDetailInfo {
    private final String id;
    private final String name;
    private final String description;
    private final int viewCount;
    private final int downloadCount;
    private final String coverPicture;
    private final String username;
    private final String publishDate;

    public SchematicDetailInfo(String id, String name, String description, int viewCount,
                               int downloadCount, String coverPicture, String username, String publishDate
                               ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.viewCount = viewCount;
        this.downloadCount = downloadCount;
        this.coverPicture = coverPicture;
        this.username = username;
        this.publishDate = publishDate;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getViewCount() { return viewCount; }
    public int getDownloadCount() { return downloadCount; }
    public String getCoverPicture() { return coverPicture; }
    public String getUsername() { return username; }
    public String getPublishDate() { return publishDate; }
}