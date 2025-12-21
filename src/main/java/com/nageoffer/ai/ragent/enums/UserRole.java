package com.nageoffer.ai.ragent.enums;

import lombok.Getter;

@Getter
public enum UserRole {

    ADMIN("admin"),

    USER("user");

    private final String code;

    UserRole(String code) {
        this.code = code;
    }
}
