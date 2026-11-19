package com.agency.api.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Keeps the PostgreSQL role constraint aligned when a new RBAC role is added. */
@Component
public class RoleConstraintMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public RoleConstraintMigration(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void run(ApplicationArguments args) {
        String database = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        if (database == null || !database.equalsIgnoreCase("PostgreSQL")) return;
        String definition = jdbc.query("select pg_get_constraintdef(oid) from pg_constraint where conrelid='accounts'::regclass and conname='accounts_role_check'",
                result -> result.next() ? result.getString(1) : null);
        if (definition != null && definition.contains("SECRETARY")) return;
        jdbc.execute("alter table accounts drop constraint if exists accounts_role_check");
        jdbc.execute("alter table accounts add constraint accounts_role_check check (role in ('AGENT','SECRETARY','HOUSEKEEPER','CANDIDATE','CLIENT'))");
    }
}
