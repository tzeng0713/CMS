package com.example.cms.service.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

public abstract class CmsJdbcSupport {
    protected final JdbcTemplate jdbc;

    protected CmsJdbcSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    protected int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    protected int countWhere(String table, String where) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
    }

    protected Long nextId(String table, String column) {
        Number max = jdbc.queryForObject("SELECT COALESCE(MAX(" + column + "), 0) + 1 FROM " + table, Number.class);
        return max.longValue();
    }

    protected Long requiredId(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    protected String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    protected String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    protected String optionalPattern(String value, String fieldName, int maxLength, String regex) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
        if (regex != null && !trimmed.matches(regex)) {
            throw new IllegalArgumentException(fieldName + " has an invalid format");
        }
        return trimmed;
    }

    protected LocalDate localDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(text);
            }
            if (text.matches("\\d{2,3}\\.\\d{1,2}\\.\\d{1,2}")) {
                String[] parts = text.split("\\.");
                int year = Integer.parseInt(parts[0]) + 1911;
                return LocalDate.of(year, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
            if (text.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
                String[] parts = text.split("/");
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    protected Integer dateNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return Integer.parseInt(text.replace("-", ""));
        }
        if (text.matches("\\d{2,3}\\.\\d{1,2}\\.\\d{1,2}")) {
            String[] parts = text.split("\\.");
            int year = Integer.parseInt(parts[0]) + 1911;
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return year * 10000 + month * 100 + day;
        }
        if (text.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
            String[] parts = text.split("/");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return year * 10000 + month * 100 + day;
        }
        return null;
    }
}
