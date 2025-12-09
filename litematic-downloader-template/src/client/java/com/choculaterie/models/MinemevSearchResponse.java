package com.choculaterie.models;

public class MinemevSearchResponse {
    private final MinemevPostInfo[] posts;
    private final int totalPages;
    private final int totalItems;

    public MinemevSearchResponse(MinemevPostInfo[] posts, int totalPages, int totalItems) {
        this.posts = posts;
        this.totalPages = totalPages;
        this.totalItems = totalItems;
    }

    public MinemevPostInfo[] getPosts() { return posts; }
    public int getTotalPages() { return totalPages; }
    public int getTotalItems() { return totalItems; }
}

