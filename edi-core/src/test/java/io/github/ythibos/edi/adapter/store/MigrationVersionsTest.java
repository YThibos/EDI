package io.github.ythibos.edi.adapter.store;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class MigrationVersionsTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @Test
    void givesEveryMigrationAVersionOfItsOwn() throws IOException {
        Map<MigrationVersion, TreeSet<String>> migrationsByVersion = migrationsByVersion();

        assertThat(sharedVersions(migrationsByVersion))
                .as("migrations that share a version, which stops Flyway from opening the store; give the later one the next free version")
                .isEmpty();
    }

    private static Map<MigrationVersion, TreeSet<String>> migrationsByVersion() throws IOException {
        try (Stream<Path> migrations = Files.list(MIGRATIONS)) {
            return migrations.map(migration -> migration.getFileName().toString())
                    .collect(groupingBy(MigrationVersionsTest::versionOf, TreeMap::new, toCollection(TreeSet::new)));
        }
    }

    private static MigrationVersion versionOf(String migration) {
        return MigrationVersion.fromVersion(migration.substring(1, migration.indexOf("__")));
    }

    private static List<TreeSet<String>> sharedVersions(Map<MigrationVersion, TreeSet<String>> migrationsByVersion) {
        return migrationsByVersion.values().stream().filter(migrations -> migrations.size() > 1).toList();
    }
}
