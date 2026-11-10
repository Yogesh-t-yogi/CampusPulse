package com.campuspulse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Logs a loud error at startup when the Flyway migrations did not run (for example when an
 * IDE runner still uses an old classpath without Flyway). Without them some tables or
 * columns are missing and requests fail with "A database error occurred."
 */
@Component
@Order(0)
public class SchemaVersionCheck implements ApplicationRunner {

    /** Highest migration in src/main/resources/db/migration. Update with each new V*.sql. */
    static final int EXPECTED_VERSION = 7;

    private static final Logger log = LoggerFactory.getLogger(SchemaVersionCheck.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaVersionCheck(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer version;
        try {
            version = jdbcTemplate.queryForObject(
                    "SELECT max(CAST(version AS INT)) FROM flyway_schema_history WHERE success",
                    Integer.class);
        } catch (RuntimeException e) {
            version = null;
        }
        if (version == null || version < EXPECTED_VERSION) {
            log.error("""

                    ************************************************************
                    DATABASE NOT MIGRATED: schema version {} but the app needs {}.
                    Flyway did not run. Start the backend with
                        mvnw.cmd spring-boot:run   (in the backend folder)
                    (or reload the Java project in your IDE so it picks up pom.xml),
                    then restart. Until then uploads and lists will fail.
                    ************************************************************""",
                    version == null ? "none" : version, EXPECTED_VERSION);
        } else {
            log.info("Database schema at version {}", version);
        }
    }
}
