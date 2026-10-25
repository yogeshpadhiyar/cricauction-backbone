package com.cricauction.modules.auth.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AuthProvider {
    LOCAL("local"),
    GOOGLE("google");

    private final String value;

    AuthProvider(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AuthProvider fromString(String text) {
        if (text == null || text.isBlank()) {
            return LOCAL;
        }
        for (AuthProvider provider : AuthProvider.values()) {
            if (provider.name().equalsIgnoreCase(text.trim()) || provider.value.equalsIgnoreCase(text.trim())) {
                return provider;
            }
        }
        return LOCAL;
    }
}
