package com.geofields.security;

/** Роли в БД: USER, AGRONOMIST, ORG_MANAGER, ORG_ADMIN (администратор организации). */
public enum UserRole {
    USER,
    AGRONOMIST,
    ORG_MANAGER,
    ORG_ADMIN;

    public String springAuthority() {
        return "ROLE_" + name();
    }

    /** Подпись для UI (Thymeleaf и т.п.). */
    public String displayNameRu() {
        return switch (this) {
            case ORG_ADMIN -> "Администратор организации";
            case ORG_MANAGER -> "Менеджер организации";
            case AGRONOMIST -> "Агроном";
            case USER -> "Пользователь";
        };
    }

    public static UserRole fromDatabase(String raw) {
        if (raw == null || raw.isBlank()) {
            return USER;
        }
        return UserRole.valueOf(raw.trim().toUpperCase());
    }
}
