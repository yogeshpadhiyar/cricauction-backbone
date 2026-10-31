package com.cricauction.modules.tournament.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum TournamentStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    UPCOMING("upcoming"),
    AUCTION_LIVE("auction_live"),
    ONGOING("ongoing"),
    COMPLETED("completed"),
    ARCHIVED("archived");

    private final String value;

    TournamentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TournamentStatus fromString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (TournamentStatus status : TournamentStatus.values()) {
            if (status.name().equalsIgnoreCase(text) || status.value.equalsIgnoreCase(text)) {
                return status;
            }
        }
        return null;
    }
}
