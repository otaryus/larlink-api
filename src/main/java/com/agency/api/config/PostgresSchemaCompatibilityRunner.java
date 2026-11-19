package com.agency.api.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Backfills optimistic-lock versions in databases created before HireRequest.version existed. */
@Component
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PostgresSchemaCompatibilityRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public PostgresSchemaCompatibilityRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("ALTER TABLE hire_requests ADD COLUMN IF NOT EXISTS version BIGINT");
        jdbc.update("UPDATE hire_requests SET version = 0 WHERE version IS NULL");
        jdbc.execute("ALTER TABLE hire_requests ALTER COLUMN version SET NOT NULL");
        jdbc.execute("ALTER TABLE hire_requests ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ");
        jdbc.update("UPDATE hire_requests SET expires_at = COALESCE(created_at + INTERVAL '14 days', CURRENT_TIMESTAMP + INTERVAL '14 days') WHERE expires_at IS NULL");
        jdbc.execute("ALTER TABLE hire_requests ALTER COLUMN expires_at SET NOT NULL");
        jdbc.execute("ALTER TABLE agency_tasks ADD COLUMN IF NOT EXISTS skill_code VARCHAR(40)");
        jdbc.update("""
                UPDATE agency_tasks SET skill_code = CASE
                    WHEN name ILIKE '%crian%' THEN 'CHILDCARE'
                    WHEN name ILIKE '%idos%' THEN 'ELDERLY_CARE'
                    WHEN name ILIKE '%roupa%' OR name ILIKE '%engom%' THEN 'LAUNDRY'
                    WHEN name ILIKE '%jardin%' THEN 'GARDENING'
                    WHEN name ILIKE '%refei%' OR name ILIKE '%cozinha%' THEN 'COOKING'
                    WHEN name ILIKE '%outras%' THEN 'OTHER'
                    ELSE 'CLEANING'
                END
                WHERE skill_code IS NULL
                """);
        jdbc.execute("ALTER TABLE agency_tasks ALTER COLUMN skill_code SET NOT NULL");
    }
}
