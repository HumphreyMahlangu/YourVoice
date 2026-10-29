package io.github.humphreymahlangu.yourvoice.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.humphreymahlangu.yourvoice.support.PostgreSqlTestContainerSupport;
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

    @Autowired javax.sql.DataSource dataSource;

    @Test
    void upgradingExistingProfilesAndRegistrationsPreservesDataWithoutGrantingTrust() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = dataSource.getConnection()) {
            connection.createStatement().execute("create schema " + schema);
            connection.setSchema(schema);
            var isolated = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            var db = new JdbcTemplate(isolated);
            try {
                org.flywaydb.core.Flyway.configure().dataSource(isolated).schemas(schema).defaultSchema(schema).target("14").load().migrate();
                UUID user = UUID.randomUUID(), profile = UUID.randomUUID(), district = UUID.randomUUID(), election = UUID.randomUUID();
                db.update("insert into user_accounts(id,email,password_hash,role,enabled,created_at,updated_at) values (?, 'legacy@example.test','unused','VOTER',true,now(),now())", user);
                db.update("insert into voting_districts(id,code,name,province,municipality,ward_number,created_at,updated_at) values (?,'SIM-LEGACY','Fictional Legacy','Demo A','Same Town',1,now(),now())", district);
                db.update("insert into voting_districts(id,code,name,province,municipality,ward_number,created_at,updated_at) values (?,'SIM-OTHER','Fictional Other','Demo B','Same Town',1,now(),now())", UUID.randomUUID());
                db.update("insert into voter_profiles(id,user_account_id,id_number_hash,date_of_birth,voting_district_id,full_name,id_document_type,created_at,updated_at) values (?,?,'" + "0".repeat(64) + "','1980-01-01',?,'Fictional Legacy','SMART_ID_CARD',now(),now())", profile, user, district);
                db.update("insert into elections(id,name,type,status,registration_start_at,registration_end_at,voting_start_at,voting_end_at,created_at,updated_at) values (?,'Fictional Legacy Election','MUNICIPAL','REGISTRATION_OPEN','2026-01-01','2026-02-01','2026-03-01','2026-03-02',now(),now())", election);
                db.update("insert into election_registrations(id,voter_profile_id,election_id,voting_district_id,status,registered_at) values (?,?,?,?,'ACTIVE',now())", UUID.randomUUID(), profile, election, district);
                org.flywaydb.core.Flyway.configure().dataSource(isolated).schemas(schema).defaultSchema(schema).load().migrate();
                assertThat(db.queryForObject("select voting_district_id from voter_profiles", UUID.class)).isEqualTo(district);
                assertThat(db.queryForObject("select voting_district_id from election_registrations", UUID.class)).isEqualTo(district);
                assertThat(db.queryForObject("select identity_verified from election_registrations", Boolean.class)).isFalse();
                assertThat(db.queryForObject("select registered_province from election_registrations", String.class)).isNull();
                assertThat(db.queryForObject("select count(*) from voter_verifications", Integer.class)).isZero();
                assertThat(db.queryForObject("select count(*) from geographic_provinces", Integer.class)).isEqualTo(2);
                assertThat(db.queryForObject("select count(*) from geographic_municipalities", Integer.class)).isEqualTo(2);
                assertThatThrownBy(() -> db.update("""
                    update election_registrations set identity_verified=true,location_verified=true,source_reference='SIM-FORGED',
                    registered_province='Demo A',registered_province_id=(select id from geographic_provinces where name='Demo A'),
                    registered_municipality='Same Town',registered_municipality_id=(select m.id from geographic_municipalities m join geographic_provinces p on p.id=m.province_id where p.name='Demo B')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
                db.update("update voter_profiles set voting_district_id=null");
                db.update("update election_registrations set voting_district_id=null");
                assertThat(db.queryForObject("select count(*) from voter_profiles", Integer.class)).isEqualTo(1);
            } finally {
                connection.setSchema("public");
                connection.createStatement().execute("drop schema " + schema + " cascade");
            }
        }
    }

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
