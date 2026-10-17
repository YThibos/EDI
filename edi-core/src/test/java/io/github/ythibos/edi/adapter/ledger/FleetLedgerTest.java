package io.github.ythibos.edi.adapter.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.task.TaskId;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class FleetLedgerTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final Instant AT = Instant.parse("2026-10-17T09:00:00Z");

    @TempDir
    private Path home;

    @Test
    void appendsOneRecordPerLineInTheOldHarnesssContract() throws IOException {
        FleetLedger ledger = new FleetLedger(new EdiHome(home), new JsonMapper());

        ledger.append(new LedgerEntry.Dispatched(LAMP, AT, "ship", "service", "claude-sonnet-5-5", "SVC-40"));
        ledger.append(new LedgerEntry.Status(LAMP, AT.plusSeconds(1), "working", null, "set-up done"));
        ledger.append(new LedgerEntry.DraftReady(LAMP, AT.plusSeconds(2), "https://forge.example.org/team/service/merge_requests/7"));
        ledger.append(new LedgerEntry.Merged(LAMP, AT.plusSeconds(3), "https://forge.example.org/team/service/merge_requests/7"));
        ledger.append(new LedgerEntry.CleanedUp(LAMP, AT.plusSeconds(4)));

        assertThat(Files.readAllLines(home.resolve("state/fleet-ledger.jsonl"))).containsExactly(
                "{\"v\":1,\"ts\":1792227600,\"event\":\"task.dispatched\",\"task\":\"fit-lamp\",\"kind\":\"ship\",\"project\":\"service\","
                        + "\"harness\":\"claude\",\"model\":\"claude-sonnet-5-5\",\"key\":\"SVC-40\"}",
                "{\"v\":1,\"ts\":1792227601,\"event\":\"task.status\",\"task\":\"fit-lamp\",\"state\":\"working\",\"key\":null,\"text\":\"set-up done\"}",
                "{\"v\":1,\"ts\":1792227602,\"event\":\"task.pr_ready\",\"task\":\"fit-lamp\",\"pr\":\"https://forge.example.org/team/service/merge_requests/7\"}",
                "{\"v\":1,\"ts\":1792227603,\"event\":\"task.merged\",\"task\":\"fit-lamp\",\"via\":\"pr\","
                        + "\"pr\":\"https://forge.example.org/team/service/merge_requests/7\"}",
                "{\"v\":1,\"ts\":1792227604,\"event\":\"task.cleaned_up\",\"task\":\"fit-lamp\"}");
    }

    @Test
    void capsAStatusTextAtTwoThousandCharacters() throws IOException {
        FleetLedger ledger = new FleetLedger(new EdiHome(home), new JsonMapper());

        ledger.append(new LedgerEntry.Status(LAMP, AT, "done", "nm-1-review", "x".repeat(2_500)));

        assertThat(Files.readString(home.resolve("state/fleet-ledger.jsonl"))).contains("\"key\":\"nm-1-review\"", "\"text\":\"" + "x".repeat(2_000) + "\"}");
    }
}
