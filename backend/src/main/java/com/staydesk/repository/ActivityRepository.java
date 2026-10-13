package com.staydesk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class ActivityRepository {

    private static final String LATEST_ACTIVITY_QUERY = """
            SELECT GREATEST(
                COALESCE((SELECT MAX(updated_at) FROM rooms), TIMESTAMP '1970-01-01'),
                COALESCE((SELECT MAX(updated_at) FROM reservations), TIMESTAMP '1970-01-01')
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public ActivityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LocalDateTime getLatestActivityTimestamp() {
        return jdbcTemplate.queryForObject(LATEST_ACTIVITY_QUERY, LocalDateTime.class);
    }
}
