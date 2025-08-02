package com.choculaterie.models;

import java.io.File;
import java.util.List;

public class SchematicCreateDTO {
    private String name;
    private String description; // Now optional (nullable)
    private List<File> schematicsPictureFiles;
    private List<File> litematicFiles;
    private String downloadLinkMediaFire;
    private String youtubeLink;
    private String tags;
    private Integer coverImageIndex;

    // Constructor without description
    public SchematicCreateDTO(String name, List<File> schematicsPictureFiles,
                              List<File> litematicFiles) {
        this.name = name;
        this.schematicsPictureFiles = schematicsPictureFiles;
        this.litematicFiles = litematicFiles;
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<File> getSchematicsPictureFiles() { return schematicsPictureFiles; }
    public List<File> getLitematicFiles() { return litematicFiles; }
    public String getDownloadLinkMediaFire() { return downloadLinkMediaFire; }
    public String getYoutubeLink() { return youtubeLink; }
    public String getTags() { return tags; }
    public Integer getCoverImageIndex() { return coverImageIndex; }

    // Optional: Setter for description
    public void setDescription(String description) {
        this.description = description;
    }
    // Setters for optional fields
    public void setDownloadLinkMediaFire(String downloadLinkMediaFire) {
        this.downloadLinkMediaFire = downloadLinkMediaFire;
    }
    public void setYoutubeLink(String youtubeLink) {
        this.youtubeLink = youtubeLink;
    }
    public void setTags(String tags) {
        this.tags = tags;
    }
    public void setCoverImageIndex(Integer coverImageIndex) {
        this.coverImageIndex = coverImageIndex;
    }
}
