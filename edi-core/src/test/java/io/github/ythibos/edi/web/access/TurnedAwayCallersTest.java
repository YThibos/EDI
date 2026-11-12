package io.github.ythibos.edi.web.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.testing.TestHome;

import jakarta.servlet.ServletException;

@Tag("adapter")
class TurnedAwayCallersTest {

    private static final int BURST = 50;
    private static final int CORE_PORT = 5490;

    @TempDir
    private Path directory;

    private TestHome home;
    private CallerFirings firings;

    @BeforeEach
    void openAHome() throws IOException {
        home = TestHome.in(directory);
        firings = new CallerFirings(home.firedRules(), home.clock());
    }

    @Test
    void recordsABurstOfCallsWithoutATokenAsOneFiringAMinute() throws IOException, ServletException {
        ApiTokenFilter filter = new ApiTokenFilter(ApiToken.issuedInto(directory.resolve("core.token")), home.workerTokens(), home.primarySessions(),
                home.consoleAccess(), firings);

        List<Integer> burst = statusesOf(filter, BURST, TurnedAwayCallersTest::callWithoutAToken);
        long firedInTheBurst = firingsOf(Rule.API_TOKEN);
        home.clock().advance(Duration.ofMinutes(1));
        List<Integer> aMinuteLater = statusesOf(filter, 1, TurnedAwayCallersTest::callWithoutAToken);

        assertThat(burst).hasSize(BURST).containsOnly(401);
        assertThat(aMinuteLater).containsExactly(401);
        assertThat(firedInTheBurst).isOne();
        assertThat(firingsOf(Rule.API_TOKEN)).isEqualTo(2);
    }

    @Test
    void recordsABurstOfCallsAddressedToAnotherHostAsOneFiringAMinute() throws IOException, ServletException {
        LoopbackHostFilter filter = new LoopbackHostFilter(firings);

        List<Integer> burst = statusesOf(filter, BURST, TurnedAwayCallersTest::callToAnotherHost);

        assertThat(burst).hasSize(BURST).containsOnly(421);
        assertThat(firingsOf(Rule.LOOPBACK_HOST)).isOne();
    }

    private static List<Integer> statusesOf(OncePerRequestFilter filter, int calls, Supplier<MockHttpServletRequest> call)
            throws IOException, ServletException {
        List<Integer> statuses = new ArrayList<>();
        for (int sent = 0; sent < calls; sent++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(call.get(), response, new MockFilterChain());
            statuses.add(response.getStatus());
        }
        return statuses;
    }

    private static MockHttpServletRequest callWithoutAToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/backlog");
        request.setServletPath("/api/backlog");
        return request;
    }

    private static MockHttpServletRequest callToAnotherHost() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.setServletPath("/api/health");
        request.setLocalPort(CORE_PORT);
        request.addHeader("Host", "rebound.example:" + CORE_PORT);
        return request;
    }

    private long firingsOf(Rule rule) {
        return home.jdbc().sql("select count(*) from rule_fired where rule = :rule").param("rule", rule.id()).query(Long.class).single();
    }
}
