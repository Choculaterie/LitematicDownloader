package com.choculaterie.models;

/**
 * Response from the quick share upload API
 */
public class QuickShareResponse {
    private final String shortUrl;

    public QuickShareResponse(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    @Override
    public String toString() {
        return "QuickShareResponse{" +
                "shortUrl='" + shortUrl + '\'' +
                '}';
    }
}

