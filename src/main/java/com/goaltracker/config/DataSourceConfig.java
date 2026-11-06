package com.goaltracker.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(@Value("${app.database.path:goals.db}") String databasePath)
            throws IOException {
        Path file = Path.of(databasePath).toAbsolutePath().normalize();
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + file);
        return dataSource;
    }
}
