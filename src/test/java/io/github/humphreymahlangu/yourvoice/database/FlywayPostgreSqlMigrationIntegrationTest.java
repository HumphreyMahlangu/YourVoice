package io.github.humphreymahlangu.votetrust.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.humphreymahlangu.votetrust.support.PostgreSqlTestContainerSupport;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgreSqlMigrationIntegrationTest extends PostgreSqlTestContainerSupport {

    private static final OffsetDateTime REGISTRATION_START = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime REGISTRATION_END = OffsetDateTime.parse("2026-02-01T00:00:00Z");
    private static final OffsetDateTime VOTING_START = OffsetDateTime.parse("2026-03-01T07:00:00Z");
    private static final OffsetDateTime VOTING_END = OffsetDateTime.parse("2026-03-01T21:00:00Z");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesContestGeographicScopeMigration() {
        Integer appliedMigrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '5' and success = true",
                Integer.class
        );
        Integer scopeColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'contests'
                  and column_name in ('scope_province', 'scope_municipality', 'scope_ward_number')
                """, Integer.class);
        Integer scopeConstraintCount = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_constraint
                where conname = 'ck_contests_geographic_scope'
                """, Integer.class);

        assertThat(appliedMigrationCount).isEqualTo(1);
        assertThat(scopeColumnCount).isEqualTo(3);
        assertThat(scopeConstraintCount).isEqualTo(1);
    }

    @Test
    void flywayAppliesVoteCorrelationMetadataMinimizationMigration() {
        Integer appliedMigrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '6' and success = true",
                Integer.class
        );
        Integer retainedBooleanColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where (table_name = 'voting_rights' and column_name = 'credential_issued')
                   or (table_name = 'anonymous_voting_credentials' and column_name = 'used')
                """, Integer.class);
        Integer removedTimingColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where (table_name = 'voting_rights'
                       and column_name in ('credential_issued_at', 'created_at', 'updated_at'))
                   or (table_name = 'anonymous_voting_credentials'
                       and column_name in ('issued_at', 'used_at'))
                """, Integer.class);

        assertThat(appliedMigrationCount).isEqualTo(1);
        assertThat(retainedBooleanColumnCount).isEqualTo(2);
        assertThat(removedTimingColumnCount).isZero();
    }

    @Test
    void flywayCreatesOrderedElectionLifecycleAuditTable() {
        Integer appliedMigrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '9' and success = true",
                Integer.class
        );
        Integer lifecycleTableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_name = 'election_lifecycle_events'
                """, Integer.class);
        Integer eventSequenceColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'election_lifecycle_events'
                  and column_name = 'event_sequence'
                  and is_identity = 'YES'
                """, Integer.class);

        assertThat(appliedMigrationCount).isEqualTo(1);
        assertThat(lifecycleTableCount).isEqualTo(1);
        assertThat(eventSequenceColumnCount).isEqualTo(1);
    }

    @Test
    void postgresRejectsContestScopeThatViolatesMigrationConstraint() {
        UUID electionId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into elections (
                    id, name, type, registration_start_at, registration_end_at,
                    voting_start_at, voting_end_at, status, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                electionId,
                "Constraint Test Election " + electionId,
                "MUNICIPAL",
                REGISTRATION_START,
                REGISTRATION_END,
                VOTING_START,
                VOTING_END,
                "DRAFT",
                CREATED_AT,
                CREATED_AT
        );

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into contests (
                    id, election_id, name, type, status, display_order, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                electionId,
                "Invalid Unscoped Ward Contest",
                "MUNICIPAL_WARD",
                "DRAFT",
                1,
                CREATED_AT,
                CREATED_AT
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
