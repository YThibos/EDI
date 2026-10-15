package io.github.ythibos.edi.adapter.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteDataSource;

import io.github.ythibos.edi.EdiHome;

@Configuration(proxyBeanMethods = false)
class StoreConfiguration {

    private static final int BUSY_TIMEOUT_MILLISECONDS = 5_000;

    @Bean
    DataSource store(EdiHome home) throws IOException {
        Path database = home.store();
        Files.createDirectories(database.getParent());
        SQLiteDataSource store = new SQLiteDataSource(storeSettings());
        store.setUrl("jdbc:sqlite:" + database);
        return store;
    }

    private static SQLiteConfig storeSettings() {
        SQLiteConfig settings = new SQLiteConfig();
        settings.setJournalMode(JournalMode.WAL);
        settings.enforceForeignKeys(true);
        settings.setBusyTimeout(BUSY_TIMEOUT_MILLISECONDS);
        return settings;
    }
}
