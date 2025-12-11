package com.choculaterie.models;

/**
 * Model for mod messages from the Choculaterie API
 */
public class ModMessage {
    private final boolean hasMessage;
    private final Integer id;
    private final String message;
    private final String type; // e.g., "info", "warning", "error"
    private final String time;

    public ModMessage(boolean hasMessage, Integer id, String message, String type, String time) {
        this.hasMessage = hasMessage;
        this.id = id;
        this.message = message;
        this.type = type;
        this.time = time;
    }

    public boolean hasMessage() {
        return hasMessage;
    }

    public Integer getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }

    public String getTime() {
        return time;
    }
}

