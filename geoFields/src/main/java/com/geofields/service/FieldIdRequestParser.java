package com.geofields.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FieldIdRequestParser {

    private FieldIdRequestParser() {
    }

    public static List<Long> parseFieldIds(List<String> rawFieldIds) {
        if (rawFieldIds == null || rawFieldIds.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (String raw : rawFieldIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String part : raw.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                try {
                    unique.add(Long.parseLong(t));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Некорректный field_id: " + part);
                }
            }
        }
        return new ArrayList<>(unique);
    }
}

