package com.cricauction.modules.match.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum MatchStatus {
    UPCOMING("UPCOMING"),
    LIVE("LIVE"),
    COMPLETED("COMPLETED"),
    ABANDONED("ABANDONED");

    private final String value;

    MatchStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MatchStatus fromString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (MatchStatus status : MatchStatus.values()) {
            if (status.name().equalsIgnoreCase(text) || status.value.equalsIgnoreCase(text)) {
                return status;
            }
        }
        return null;
    }
}

