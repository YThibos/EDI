package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.ythibos.edi.adapter.store.StoreFile;

final class MigratedStore {

    private static final int MOST_CONNECTIONS_AT_ONCE = 16;
    private static final Duration IDLE_CONNECTION_KEPT = Duration.ofSeconds(10);
    private static final ScheduledExecutorService POOL_HOUSEKEEPING =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("test-home-store-pools").factory());

    private MigratedStore() {
    }

    static DataSource openedAt(Path database) throws IOException {
        boolean fresh = Files.notExists(database);
        DataSource store = keepingConnections(StoreFile.disposableAt(database));
        if (fresh) {
            Files.copy(MigratedOnce.STORE, database);
        } else {
            migrate(store);
        }
        return store;
    }

    private static DataSource keepingConnections(DataSource store) {
        HikariConfig pool = new HikariConfig();
        pool.setDataSource(store);
        pool.setMinimumIdle(0);
        pool.setMaximumPoolSize(MOST_CONNECTIONS_AT_ONCE);
        pool.setIdleTimeout(IDLE_CONNECTION_KEPT.toMillis());
        pool.setInitializationFailTimeout(-1);
        pool.setScheduledExecutor(POOL_HOUSEKEEPING);
        return new HikariDataSource(pool);
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
                migrate(StoreFile.disposableAt(store));
                store.toFile().deleteOnExit();
                return store;
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
        }
    }
}
