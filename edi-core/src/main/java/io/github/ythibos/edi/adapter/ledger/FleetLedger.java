package io.github.ythibos.edi.adapter.ledger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class FleetLedger {

    private static final int FORMAT = 1;
    private static final int LONGEST_TEXT = 2000;
    private static final String HARNESS = "claude";
    private static final String MERGED_THROUGH_ITS_DRAFT = "pr";

    private final Path file;
    private final JsonMapper json;

    public FleetLedger(EdiHome home, JsonMapper json) {
        this.file = home.fleetLedger();
        this.json = json;
    }

    public void append(LedgerEntry entry) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.writeValueAsString(recordOf(entry)) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private ObjectNode recordOf(LedgerEntry entry) {
        ObjectNode record = json.createObjectNode()
                .put("v", FORMAT)
                .put("ts", entry.at().getEpochSecond())
                .put("event", eventOf(entry))
                .put("task", entry.task().value());
        return switch (entry) {
            case LedgerEntry.Dispatched dispatched -> record.put("kind", dispatched.kind())
                    .put("project", dispatched.project())
                    .put("harness", HARNESS)
                    .put("model", dispatched.model())
                    .put("key", dispatched.key());
            case LedgerEntry.Status status -> record.put("state", status.state()).put("key", status.key()).put("text", capped(status.text()));
            case LedgerEntry.DraftReady ready -> record.put("pr", ready.url());
            case LedgerEntry.Merged merged -> record.put("via", MERGED_THROUGH_ITS_DRAFT).put("pr", merged.url());
            case LedgerEntry.Worked worked -> record.put("key", worked.key())
                    .put("active_minutes", worked.activeMinutes())
                    .put("wall_clock_minutes", worked.wallClockMinutes());
            case LedgerEntry.CleanedUp _ -> record;
        };
    }

    private static String eventOf(LedgerEntry entry) {
        return switch (entry) {
            case LedgerEntry.Dispatched _ -> "task.dispatched";
            case LedgerEntry.Status _ -> "task.status";
            case LedgerEntry.DraftReady _ -> "task.pr_ready";
            case LedgerEntry.Merged _ -> "task.merged";
            case LedgerEntry.Worked _ -> "task.worked";
            case LedgerEntry.CleanedUp _ -> "task.cleaned_up";
        };
    }

    private static String capped(String text) {
        return text.length() <= LONGEST_TEXT ? text : text.substring(0, LONGEST_TEXT);
    }
}
