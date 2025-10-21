package com.choculaterie.models;

public class MinemevFileInfo {
    private final String fileName;
    private final String downloadUrl;
    private final long fileSize;
    private final String[] versions;
    private final int downloadCount;
    private final String fileType;

    public MinemevFileInfo(String fileName, String downloadUrl, long fileSize,
                           String[] versions, int downloadCount, String fileType) {
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.fileSize = fileSize;
        this.versions = versions;
        this.downloadCount = downloadCount;
        this.fileType = fileType;
    }

    public String getFileName() { return fileName; }
    public String getDownloadUrl() { return downloadUrl; }
    public long getFileSize() { return fileSize; }
    public String[] getVersions() { return versions; }
    public int getDownloadCount() { return downloadCount; }
    public String getFileType() { return fileType; }
}
