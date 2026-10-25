package com.cricauction.modules.auth.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OtpPurpose {
    SIGNUP_VERIFICATION("signup_verification"),
    PASSWORD_RESET("password_reset");

    private final String value;

    OtpPurpose(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OtpPurpose fromString(String text) {
        if (text == null || text.isBlank()) {
            return SIGNUP_VERIFICATION;
        }
        for (OtpPurpose p : OtpPurpose.values()) {
            if (p.name().equalsIgnoreCase(text.trim()) || p.value.equalsIgnoreCase(text.trim())) {
                return p;
            }
        }
        return SIGNUP_VERIFICATION;
    }
}
