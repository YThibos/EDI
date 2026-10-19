package io.github.ythibos.edi.application.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.TestHome;

class DailyMemoryCheckTest {

    @TempDir
    private Path directory;

    @Test
    void notifiesTheCaptainOnceWhenStartupMemoryGrowsPastItsBudget() throws IOException {
        TestHome home = TestHome.in(directory).writing("config/machine.yaml", "notify:\n  windowsToast: true\nmemory:\n  startupBudgetTokens: 50\n")
                .writing("memory/profile.md", "# Profile\n\n- A short rule. (2026-10-15)\n");

        home.dailyMemoryCheck().check();
        home.writing("memory/learnings.md", "- " + "A growing learning. ".repeat(20) + "(2026-10-16)\n");
        home.clock().advance(Duration.ofDays(1));
        home.dailyMemoryCheck().check();
        home.clock().advance(Duration.ofDays(1));
        home.dailyMemoryCheck().check();

        assertThat(home.windowsSide().toasts()).singleElement().asString()
                .startsWith("Startup memory is over its budget | ").endsWith(" of 50 tokens; /stow curates it before the next session.");
    }
}
