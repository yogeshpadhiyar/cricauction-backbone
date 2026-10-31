package com.cricauction.modules.notification.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DevicePlatform {
    IOS("ios"),
    ANDROID("android"),
    WEB("web");

    private final String value;

    DevicePlatform(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DevicePlatform fromString(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("platform cannot be empty");
        }
        for (DevicePlatform platform : DevicePlatform.values()) {
            if (platform.name().equalsIgnoreCase(text) || platform.value.equalsIgnoreCase(text)) {
                return platform;
            }
        }
        throw new IllegalArgumentException("Unsupported platform: " + text);
    }
}
