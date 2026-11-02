package com.campuspulse.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UnitNameSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public UnitNameSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> columnTypes = jdbcTemplate.query(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                    AND table_name = 'unit'
                    AND column_name = 'name'
                """,
                (resultSet, rowNumber) -> resultSet.getString("data_type")
        );

        if (columnTypes.isEmpty()) {
            throw new IllegalStateException(
                    "Expected the unit.name database column to exist.");
        }

        if (!"text".equalsIgnoreCase(columnTypes.get(0))) {
            jdbcTemplate.execute(
                    "ALTER TABLE unit ALTER COLUMN name TYPE TEXT");
        }
    }
}
