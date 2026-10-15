package io.github.ythibos.edi.adapter.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.TransactionMode;
import org.sqlite.SQLiteDataSource;

public final class StoreFile {

    private static final int BUSY_TIMEOUT_MILLISECONDS = 5_000;

    private StoreFile() {
    }

    public static DataSource openedAt(Path database) throws IOException {
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
        settings.setTransactionMode(TransactionMode.IMMEDIATE);
        return settings;
    }
}
