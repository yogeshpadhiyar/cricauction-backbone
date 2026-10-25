package com.cricauction.modules.auth.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {
    ORGANIZER("organizer"),
    CAPTAIN("captain"),
    PLAYER("player");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UserRole fromString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (UserRole role : UserRole.values()) {
            if (role.name().equalsIgnoreCase(text.trim()) || role.value.equalsIgnoreCase(text.trim())) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + text + ". Allowed roles: organizer, captain, player");
    }
}
