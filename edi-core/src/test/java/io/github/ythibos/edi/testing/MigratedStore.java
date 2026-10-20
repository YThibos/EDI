package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

import io.github.ythibos.edi.adapter.store.StoreFile;

final class MigratedStore {

    private MigratedStore() {
    }

    static DataSource openedAt(Path database) throws IOException {
        boolean fresh = Files.notExists(database);
        DataSource store = StoreFile.openedAt(database);
        if (fresh) {
            Files.copy(MigratedOnce.STORE, database);
        } else {
            migrate(store);
        }
        return store;
    }

    private static void migrate(DataSource store) {
        Flyway.configure().dataSource(store).load().migrate();
    }

    private static final class MigratedOnce {

        private static final Path STORE = migratedStore();

        private static Path migratedStore() {
            try {
                Path directory = Files.createTempDirectory("edi-migrated-store-");
                directory.toFile().deleteOnExit();
                Path store = directory.resolve("edi.db");
                migrate(StoreFile.openedAt(store));
                store.toFile().deleteOnExit();
                return store;
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
        }
    }
}
