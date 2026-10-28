package io.github.ythibos.edi.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class StartupMemoryTest {

    private static final MemoryFile PROFILE = new MemoryFile("profile.md", """
            # Profile

            ## Commits

            - Every commit carries its key. (2026-10-15)
            - Never add a trailer.
              It holds for every project.
            """);
    private static final MemoryFile LEARNINGS = new MemoryFile("learnings.md", """
            # Learnings

            ## commits

            - Links open with Shift and a click (2026-10-13).
            - The old daemon restarts itself (2026-03-01, confirmed 2026-04-02).
            """);

    @Test
    void estimatesTokensAsCharactersDividedByFour() {
        assertThat(new MemoryFile("profile.md", "x".repeat(9)).estimatedTokens()).isEqualTo(3);
        assertThat(new StartupMemory(List.of(PROFILE, LEARNINGS), 18_000).estimatedTokens())
                .isEqualTo(Math.ceilDiv(PROFILE.text().length(), 4) + Math.ceilDiv(LEARNINGS.text().length(), 4));
    }

    @Test
    void failsAboveTheBudgetAndOnARepeatedRuleHeading() {
        assertThat(new StartupMemory(List.of(PROFILE), 18_000).passes()).isTrue();
        assertThat(new StartupMemory(List.of(PROFILE), 10).fitsItsBudget()).isFalse();
        assertThat(new StartupMemory(List.of(PROFILE, LEARNINGS), 18_000).duplicatedHeadings()).containsExactly("commits");
        assertThatThrownBy(() -> new StartupMemory(List.of(PROFILE), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsEachEntryWithTheLatestDateItCarries() {
        assertThat(PROFILE.entries()).extracting(MemoryEntry::firstLine, MemoryEntry::dated).containsExactly(
                tuple("Every commit carries its key. (2026-10-15)", Optional.of(LocalDate.of(2026, 10, 15))),
                tuple("Never add a trailer.", Optional.empty()));
        assertThat(LEARNINGS.entries().get(1).dated()).contains(LocalDate.of(2026, 4, 2));
    }

    @Test
    void listsWhatStowShouldCurate() {
        StowList list = StowList.of(new StartupMemory(List.of(PROFILE, LEARNINGS), 20), LocalDate.of(2026, 10, 17), Map.of());

        assertThat(list.findings()).extracting(StowFinding::file, StowFinding::entry).containsExactly(
                tuple("", "commits"),
                tuple("profile.md", "Never add a trailer."),
                tuple("learnings.md", "The old daemon restarts itself (2026-03-01, confirmed 2026-04-02)."));
        assertThat(list.tokensOverBudget()).isPositive().isEqualTo(list.estimatedTokens() - 20);
    }
}
