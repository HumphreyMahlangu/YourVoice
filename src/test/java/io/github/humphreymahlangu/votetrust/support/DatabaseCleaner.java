package io.github.humphreymahlangu.votetrust.support;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Empties every application table between integration tests. Truncating the whole schema (rather than calling
 * repository deleteAll in foreign-key order) keeps tests correct when new tables are added.
 */
public final class DatabaseCleaner {

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbcTemplate) {
        List<String> tables = jdbcTemplate.queryForList("""
                select quote_ident(tablename)
                from pg_tables
                where schemaname = current_schema()
                  and tablename <> 'flyway_schema_history'
                """, String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("truncate table " + String.join(", ", tables) + " cascade");
        }
    }
}
