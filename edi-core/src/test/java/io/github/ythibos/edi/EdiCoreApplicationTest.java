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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.testing.Patience;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class EdiCoreApplicationTest {

    @TempDir
    private static Path home;

    @Autowired
    private JdbcClient store;

    @Autowired
    private ScheduledJobWitness scheduledJob;

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", () -> home.toString());
    }

    @Test
    void reportsItsHealthOnTheApiPath() throws Exception {
        HttpResponse<String> health = get("/api/health");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void recordsItsPortInTheHomeForTheCommand() throws IOException {
        assertThat(Files.readString(home.resolve("state/core.port")).strip()).matches("\\d+");
    }

    @Test
    void keepsItsStoreInTheHomeWithMigrationsApplied() {
        assertThat(home.resolve("data/edi.db")).isRegularFile();
        assertThat(store.sql("select version from flyway_schema_history where success = 1").query(String.class).list()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
                "18", "19", "20", "21", "22", "23", "24", "25", "26", "27");
        assertThat(store.sql("select name from sqlite_master where type = 'table' and name not like 'flyway%' and name not like 'sqlite%' order by name")
                .query(String.class).list())
                .containsExactly("adoption", "board", "board_answer", "board_decision", "captain_hold", "captain_message", "captain_word", "clone_refresh", "console_session",
                        "console_sign_in", "decision", "decision_option", "delivery", "dispatch", "dispatch_file", "event", "event_cursor", "health_sample",
                        "hold_notice", "inbox_message", "memory_check", "meta", "outage_entry", "pipeline_run", "presence", "primary_session", "recovery_cost",
                        "rule_fired", "spark", "task",
                        "task_cost", "task_dependency", "task_project", "task_review", "ticket_candidate", "ticket_proposal",
                        "unbriefed_absence", "unplanned_ship", "wake", "wake_stream", "work_key_sequence", "worker", "worker_notice");
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

    @Test
    void handlesARequestOnAVirtualThread() throws Exception {
        assertThat(get("/witness/request-thread").body()).isEqualTo(ThreadWitnesses.VIRTUAL);
    }

    @Test
    void runsAScheduledJobOnAVirtualThread() throws Exception {
        assertThat(scheduledJob.firstRun().get(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)).isEqualTo(ThreadWitnesses.VIRTUAL);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        String port = Files.readString(home.resolve("state/core.port")).strip();
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(), BodyHandlers.ofString());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ThreadWitnesses {

        static final String VIRTUAL = "virtual";
        static final String PLATFORM = "platform";

        @Bean
        RequestThreadWitness requestThreadWitness() {
            return new RequestThreadWitness();
        }

        @Bean
        ScheduledJobWitness scheduledJobWitness() {
            return new ScheduledJobWitness();
        }

        static String kindOfTheCurrentThread() {
            return Thread.currentThread().isVirtual() ? VIRTUAL : PLATFORM;
        }
    }

    @RestController
    static class RequestThreadWitness {

        @GetMapping("/witness/request-thread")
        String requestThread() {
            return ThreadWitnesses.kindOfTheCurrentThread();
        }
    }

    static class ScheduledJobWitness {

        private final CompletableFuture<String> firstRun = new CompletableFuture<>();

        @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
        void run() {
            firstRun.complete(ThreadWitnesses.kindOfTheCurrentThread());
        }

        CompletableFuture<String> firstRun() {
            return firstRun;
        }
    }
}
