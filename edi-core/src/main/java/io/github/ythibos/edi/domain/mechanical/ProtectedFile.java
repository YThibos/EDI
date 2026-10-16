package io.github.ythibos.edi.domain.mechanical;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.github.ythibos.edi.domain.AsciiCharacters;

public enum ProtectedFile {
    DECISION_RECORD("a decision record", path -> path.hasDirectory("decisions", "adr", "adrs") || path.lowerCaseName().startsWith("adr-")),
    MIGRATION("a migration", path -> path.hasDirectory("migration", "migrations") || path.isVersionedSqlMigration()),
    SECURITY_CONFIGURATION("security configuration", path -> path.mentions("security")),
    TEST_EXPECTATION("a test or its expected values",
            path -> path.hasDirectory("test", "tests", "__tests__", "__snapshots__") || path.isNamedAsATest());

    private final String description;
    private final Predicate<RepositoryPath> recognises;

    ProtectedFile(String description, Predicate<RepositoryPath> recognises) {
        this.description = description;
        this.recognises = recognises;
    }

    public static Optional<ProtectedFile> of(String file) {
        RepositoryPath path = RepositoryPath.of(file);
        return Arrays.stream(values()).filter(kind -> kind.recognises.test(path)).findFirst();
    }

    public String description() {
        return description;
    }

    private record RepositoryPath(List<String> directories, String name) {

        private static final List<String> TEST_CLASS_SUFFIXES = List.of("Test.java", "Tests.java", "IT.java");
        private static final List<String> TEST_FILE_MARKS = List.of(".spec.", ".test.");
        private static final String MIGRATION_SEPARATOR = "__";
        private static final String SQL = ".sql";

        static RepositoryPath of(String file) {
            List<String> segments = List.of(file.split("/"));
            List<String> directories = segments.subList(0, segments.size() - 1).stream().map(RepositoryPath::lowerCase).toList();
            return new RepositoryPath(directories, segments.getLast());
        }

        boolean hasDirectory(String... names) {
            Set<String> wanted = Set.of(names);
            return directories.stream().anyMatch(wanted::contains);
        }

        String lowerCaseName() {
            return lowerCase(name);
        }

        boolean mentions(String word) {
            return lowerCaseName().contains(word) || directories.stream().anyMatch(directory -> directory.contains(word));
        }

        boolean isVersionedSqlMigration() {
            String lowerCaseName = lowerCaseName();
            int separator = lowerCaseName.indexOf(MIGRATION_SEPARATOR);
            return lowerCaseName.startsWith("v") && lowerCaseName.endsWith(SQL) && separator > 1
                    && lowerCaseName.substring(1, separator).chars().allMatch(character -> AsciiCharacters.isDigit(character) || character == '_');
        }

        boolean isNamedAsATest() {
            return TEST_CLASS_SUFFIXES.stream().anyMatch(name::endsWith) || TEST_FILE_MARKS.stream().anyMatch(lowerCaseName()::contains);
        }

        private static String lowerCase(String text) {
            return text.toLowerCase(Locale.ROOT);
        }
    }
}
