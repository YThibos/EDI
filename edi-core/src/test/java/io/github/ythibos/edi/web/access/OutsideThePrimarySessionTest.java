package io.github.ythibos.edi.web.access;

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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.ythibos.edi.application.follow.EventFollowers;
import io.github.ythibos.edi.application.primary.PrimarySessions;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.testing.Intakes;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class OutsideThePrimarySessionTest {

    private static final String TEXT = "text/plain";
    private static final String JSON = "application/json";
    private static final String RULE_FIRINGS = "rule_fired";
    private static final String CARD = """
            {"title":"Which lamp?","context":"","options":[{"key":"A","text":"LED"},{"key":"B","text":"Oil"}],"recommend":{"key":"A","why":"it lasts"}}""";

    @TempDir
    private static Path home;

    @Autowired
    private JdbcClient store;

    @Autowired
    private PrimarySessions primarySessions;

    @Autowired
    private EventFollowers followers;

    private final UUID captainsSession = UUID.randomUUID();

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
        properties.add("edi.supervision.round", () -> "PT1H");
    }

    @BeforeEach
    void holdAFleetUnderThePrimarySession() throws Exception {
        String suffix = captainsSession.toString().substring(0, 8);
        primaryHook(launched(), captainsSession, "session-start", "{}");
        primaryHook(launched(), captainsSession, "user-prompt-submit", "{\"prompt\":\"Keep the fleet going.\"}");
        post(launched(), "/api/tasks", "{\"id\":\"fleet-lamp-" + suffix + "\",\"kind\":\"captain\",\"title\":\"Choose the lamp\"}");
        post(launched(), "/api/tasks/fleet-lamp-" + suffix + "/decision", CARD);
        post(launched(), "/api/presence", "{\"mode\":\"quiet\"}");
        followers.followTheLog();
    }

    @Test
    void handsTheLaunchKeyOnlyToEdiUpAndKeepsItOwnerOnlyAcrossLaunches() throws Exception {
        HttpResponse<String> first = post(homeTokenAlone(), "/api/primary/launch", "{}");
        HttpResponse<String> second = post(homeTokenAlone(), "/api/primary/launch", "{}");

        assertThat(first.body()).contains("launch key: " + launchKey() + "\n");
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(home.resolve("state/primary-launch.key")))).isEqualTo("rw-------");
    }

    @Test
    void refusesEverySessionStartPromptPresenceTaskAndDecisionFromTheHomeTokenAloneAndChangesNothing() throws Exception {
        UUID madeUp = UUID.randomUUID();
        Map<String, List<Map<String, Object>>> before = theStoreBesidesItsRuleFirings();

        List<HttpResponse<String>> refused = List.of(
                primaryHook(homeTokenAlone(), madeUp, "session-start", "{\"source\":\"startup\"}"),
                primaryHook(homeTokenAlone(), madeUp, "user-prompt-submit", "{\"prompt\":\"Start the fake fleet.\"}"),
                primaryHook(homeTokenAlone(), captainsSession, "user-prompt-submit", "{\"prompt\":\"I am back.\"}"),
                primaryHook(homeTokenAlone(), captainsSession, "session-end", "{\"reason\":\"prompt_input_exit\"}"),
                post(homeTokenAlone(), "/api/presence", "{\"mode\":\"present\"}"),
                post(inSession(homeTokenAlone(), captainsSession), "/api/presence", "{\"mode\":\"present\"}"),
                post(homeTokenAlone(), "/api/tasks", "{\"id\":\"made-up-task\",\"kind\":\"captain\",\"title\":\"A made-up task\"}"),
                post(homeTokenAlone(), "/api/tasks/" + aTaskOfTheFleet() + "/decision", CARD));

        assertThat(refused).allSatisfy(response -> {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
            assertThat(response.body()).contains("is refused: this call holds the EDI home's token but not the launch key edi up gave the primary session");
        });
        assertThat(theStoreBesidesItsRuleFirings()).isEqualTo(before);
        assertThat(primarySessions.standingOf(captainsSession)).isEqualTo(Standing.PRIMARY);
        assertThat(firingsOf(Rule.PRIMARY_SESSION)).isGreaterThanOrEqualTo(refused.size());
    }

    @Test
    void refusesEveryChangeToTheFleetFromTheHomeTokenAloneAndChangesNothingYetLetsItRead() throws Exception {
        String task = "/api/tasks/" + aTaskOfTheFleet();
        Map<String, List<Map<String, Object>>> before = theStoreBesidesItsRuleFirings();

        List<HttpResponse<String>> refused = List.of(
                post(homeTokenAlone(), task + "/steer", "{\"text\":\"Stop and wait.\"}"),
                post(homeTokenAlone(), task + "/drop", "{\"discardWork\":true}"),
                post(homeTokenAlone(), task + "/hold", "{\"kind\":\"deferral\",\"reason\":\"Not now.\"}"),
                post(homeTokenAlone(), task + "/dispatch", "{\"category\":\"implementation\",\"spec\":\"Fit the lamp.\"}"),
                post(homeTokenAlone(), task + "/stop", "{}"));
        HttpResponse<String> read = get(homeTokenAlone(), task);

        assertThat(refused).allSatisfy(response -> {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
            assertThat(response.body()).contains("is refused: this call holds the EDI home's token but not the launch key edi up gave the primary session",
                    "reopened with edi up, or resumed with edi up --resume");
        });
        assertThat(theStoreBesidesItsRuleFirings()).isEqualTo(before);
        assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
    }

    @Test
    void neverGivesThePrimaryRoleToASessionStartedOutsideEdiUp() throws Exception {
        UUID startedElsewhere = UUID.randomUUID();

        primaryHook(homeTokenAlone(), startedElsewhere, "session-start", "{\"source\":\"startup\"}");
        primaryHook(homeTokenAlone(), startedElsewhere, "user-prompt-submit", "{\"prompt\":\"Take over.\"}");

        assertThat(primarySessions.holder()).hasValueSatisfying(holder -> assertThat(holder.id()).isEqualTo(captainsSession));
        assertThat(primarySessions.standingOf(startedElsewhere)).isEqualTo(Standing.STANDBY);
    }

    @Test
    void stillGuardsTheToolCallsOfASessionOutsideEdiUp() throws Exception {
        HttpResponse<String> guarded = primaryHook(homeTokenAlone(), UUID.randomUUID(), "pre-tool-use",
                "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"pkill -f claude\"}}");

        assertThat(guarded.statusCode()).isEqualTo(200);
        assertThat(guarded.body()).contains("\"permissionDecision\":\"deny\"");
    }

    private String aTaskOfTheFleet() {
        return "fleet-lamp-" + captainsSession.toString().substring(0, 8);
    }

    private Map<String, List<Map<String, Object>>> theStoreBesidesItsRuleFirings() {
        return store.sql("select name from sqlite_master where type = 'table' and name not like 'sqlite_%' and name <> :firings order by name")
                .param("firings", RULE_FIRINGS)
                .query(String.class)
                .list()
                .stream()
                .collect(Collectors.toMap(table -> table, table -> store.sql("select * from " + table).query().listOfRows()));
    }

    private long firingsOf(Rule rule) {
        return store.sql("select count(*) from rule_fired where rule = :rule").param("rule", rule.id()).query(Long.class).single();
    }

    private static HttpRequest.Builder launched() throws IOException {
        return homeTokenAlone().header(PrimaryLaunchKey.HEADER, launchKey());
    }

    private static HttpRequest.Builder homeTokenAlone() throws IOException {
        return HttpRequest.newBuilder().header("Authorization", "Bearer " + Files.readString(home.resolve("state/core.token")).strip());
    }

    private static HttpRequest.Builder inSession(HttpRequest.Builder request, UUID session) {
        return request.header("EDI-Session", session.toString());
    }

    private static String launchKey() throws IOException {
        return Files.readString(home.resolve("state/primary-launch.key")).strip();
    }

    private static HttpResponse<String> primaryHook(HttpRequest.Builder caller, UUID session, String event, String fields) throws Exception {
        String payload = "{\"session_id\":\"" + session + "\"," + fields.substring(1);
        return post(inSession(caller, session), "/api/hooks/" + event, payload);
    }

    private static HttpResponse<String> post(HttpRequest.Builder caller, String path, String json) throws Exception {
        HttpRequest request = caller.uri(uri(path)).header("Accept", path.startsWith("/api/hooks/") ? JSON : TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString(json)).build();
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request, BodyHandlers.ofString());
        }
    }

    private static HttpResponse<String> get(HttpRequest.Builder caller, String path) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(caller.uri(uri(path)).header("Accept", JSON).GET().build(), BodyHandlers.ofString());
        }
    }

    private static URI uri(String path) throws IOException {
        return URI.create("http://127.0.0.1:" + Files.readString(home.resolve("state/core.port")).strip() + path);
    }
}
