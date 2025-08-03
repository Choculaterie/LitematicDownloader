package com.choculaterie.models;

public class LitematicFile {
    private String filename;
    private String base64Data;

    public LitematicFile(String filename, String base64Data) {
        this.filename = filename;
        this.base64Data = base64Data;
    }

    public String getFilename() {
        return filename;
    }

    public String getBase64Data() {
        return base64Data;
    }
}