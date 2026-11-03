package io.github.ythibos.edi.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.ythibos.edi.adapter.browser.CaptainsBrowser;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class EveryTestCoreRecordsItsBrowserTest {

    private static final String JSON = "application/json";

    @TempDir
    private static Path home;

    @Autowired
    private ApplicationContext core;

    @Autowired
    private RecordingBrowser browser;

    @BeforeAll
    static void prepareTheHome() throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/projects.yaml"), Intakes.REGISTRY);
    }

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", () -> home.toString());
        properties.add("edi.checkout", () -> home.resolve("checkout").toString());
        properties.add("edi.workers.socket", () -> "edi-test-" + UUID.randomUUID());
    }

    @Test
    void recordsTheSitrepASessionStartWouldOpenInTheCaptainsBrowser() throws Exception {
        UUID primary = UUID.randomUUID();
        HttpResponse<String> started = primaryHook(primary, "session-start", "{\"source\":\"startup\"}");
        primaryHook(primary, "session-end", "{\"reason\":\"prompt_input_exit\"}");

        assertThat(core.getBeansOfType(CaptainsBrowser.class).values()).singleElement().isSameAs(browser);
        assertThat(browser.opened()).singleElement().asString().startsWith(uri("/hand-off/").toString());
        assertThat(started.body()).contains("The console's sitrep page is open in the captain's browser at " + uri("/") + ".");
    }

    private static HttpResponse<String> primaryHook(UUID session, String event, String fields) throws Exception {
        String payload = "{\"session_id\":\"" + session + "\"," + fields.substring(1);
        HttpRequest.Builder hook = HttpRequest.newBuilder(uri("/api/hooks/" + event))
                .header("Authorization", "Bearer " + Files.readString(home.resolve("state/core.token")))
                .header("Accept", JSON)
                .header("EDI-Session", session.toString())
                .header("Content-Type", JSON)
                .POST(BodyPublishers.ofString(payload));
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(hook.build(), BodyHandlers.ofString());
        }
    }

    private static URI uri(String path) throws IOException {
        return URI.create("http://127.0.0.1:" + Files.readString(home.resolve("state/core.port")).strip() + path);
    }
}
