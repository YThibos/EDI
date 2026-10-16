package io.github.ythibos.edi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EdiCoreApplicationTest {

    private static final Path HOME = temporaryHome();

    @Autowired
    private JdbcClient store;

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", HOME::toString);
    }

    @AfterAll
    static void removeTheTemporaryHome() throws IOException {
        try (Stream<Path> files = Files.walk(HOME)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    @Test
    void reportsItsHealthOnTheApiPath() throws Exception {
        HttpResponse<String> health = get("/api/health");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void recordsItsPortInTheHomeForTheCommand() throws IOException {
        assertThat(Files.readString(HOME.resolve("state/core.port")).strip()).matches("\\d+");
    }

    @Test
    void keepsItsStoreInTheHomeWithMigrationsApplied() {
        assertThat(HOME.resolve("data/edi.db")).isRegularFile();
        assertThat(store.sql("select version from flyway_schema_history where success = 1").query(String.class).list()).containsExactly("1");
        assertThat(store.sql("select name from sqlite_master where type = 'table' and name not like 'flyway%' and name not like 'sqlite%' order by name")
                .query(String.class).list())
                .containsExactly("captain_hold", "decision", "decision_option", "event", "meta", "task", "task_dependency", "task_project");
        assertThat(store.sql("pragma journal_mode").query(String.class).single()).isEqualToIgnoringCase("wal");
        assertThat(store.sql("pragma foreign_keys").query(Integer.class).single()).isOne();
        assertThat(store.sql("pragma busy_timeout").query(Integer.class).single()).isEqualTo(5_000);
    }

    @Test
    void servesTheConsole() throws Exception {
        HttpResponse<String> console = get("/");

        assertThat(console.statusCode()).isEqualTo(200);
        assertThat(console.body()).contains("<app-root></app-root>");
    }

    private static HttpResponse<String> get(String path) throws Exception {
        String port = Files.readString(HOME.resolve("state/core.port")).strip();
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(), BodyHandlers.ofString());
        }
    }

    private static Path temporaryHome() {
        try {
            return Files.createTempDirectory("edi-home-");
        } catch (IOException unwritable) {
            throw new IllegalStateException(unwritable);
        }
    }
}
