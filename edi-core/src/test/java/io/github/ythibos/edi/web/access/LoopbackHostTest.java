package io.github.ythibos.edi.web.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.ythibos.edi.testing.Intakes;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class LoopbackHostTest {

    private static final String HEALTH = "/api/health";
    private static final String CONSOLE_PAGE = "/";
    private static final String NO_FRAMING_POLICY = "Content-Security-Policy: frame-ancestors 'none'";
    private static final String NO_FRAMING = "X-Frame-Options: DENY";

    @TempDir
    private static Path home;

    @BeforeAll
    static void prepareTheHome() throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/projects.yaml"), Intakes.REGISTRY);
    }

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", () -> home.toString());
        properties.add("edi.workers.socket", () -> "edi-test-" + UUID.randomUUID());
    }

    @Test
    void servesARequestAddressedToItsOwnLoopbackHostAndPort() throws IOException {
        int port = port();

        assertThat(statusOf(answerTo(HEALTH, "127.0.0.1:" + port))).isEqualTo(200);
        assertThat(statusOf(answerTo(HEALTH, "localhost:" + port))).isEqualTo(200);
        assertThat(statusOf(answerTo(HEALTH, "LocalHost:" + port))).isEqualTo(200);
    }

    @Test
    void refusesARequestAddressedToAnyOtherHost() throws IOException {
        int port = port();

        assertThat(statusOf(answerTo(HEALTH, "harbour.example:" + port))).isEqualTo(421);
        assertThat(statusOf(answerTo(HEALTH, "harbour.example"))).isEqualTo(421);
        assertThat(statusOf(answerTo(HEALTH, "127.0.0.1"))).isEqualTo(421);
        assertThat(statusOf(answerTo(HEALTH, "localhost"))).isEqualTo(421);
        assertThat(statusOf(answerTo(HEALTH, "127.0.0.1:" + otherPort(port)))).isEqualTo(421);
        assertThat(statusOf(answerTo(CONSOLE_PAGE, "rebound.example:" + port))).isEqualTo(421);
        assertThat(statusOf(answerTo("/api/console/session", "rebound.example:" + port))).isEqualTo(421);
    }

    @Test
    void namesTheHostsItAnswersWhenItRefusesOne() throws IOException {
        int port = port();

        assertThat(answerTo(HEALTH, "rebound.example:" + port)).endsWith(
                "\r\n\r\nThis core answers only a request addressed to 127.0.0.1:" + port + " or localhost:" + port + ".\n");
    }

    @Test
    void forbidsEveryPageToFrameAnyAnswer() throws IOException {
        int port = port();

        assertThat(headersOf(answerTo(HEALTH, "127.0.0.1:" + port))).contains(NO_FRAMING_POLICY, NO_FRAMING);
        assertThat(headersOf(answerTo(CONSOLE_PAGE, "localhost:" + port))).contains(NO_FRAMING_POLICY, NO_FRAMING);
        assertThat(headersOf(answerTo("/api/backlog", "127.0.0.1:" + port))).contains(NO_FRAMING_POLICY, NO_FRAMING);
        assertThat(headersOf(answerTo(HEALTH, "rebound.example:" + port))).contains(NO_FRAMING_POLICY, NO_FRAMING);
    }

    private static int port() throws IOException {
        return Integer.parseInt(Files.readString(home.resolve("state/core.port")).strip());
    }

    private static int otherPort(int port) {
        return port == 65535 ? port - 1 : port + 1;
    }

    private static String answerTo(String path, String host) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port())) {
            OutputStream request = socket.getOutputStream();
            request.write(("GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            request.flush();
            try (InputStream answer = socket.getInputStream()) {
                return new String(answer.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static int statusOf(String answer) {
        return Integer.parseInt(answer.split(" ", 3)[1].strip());
    }

    private static String headersOf(String answer) {
        return answer.substring(0, answer.indexOf("\r\n\r\n"));
    }
}
