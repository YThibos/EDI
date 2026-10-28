package io.github.ythibos.edi.domain.memory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.ythibos.edi.domain.project.WorkKey;

public record StowList(int estimatedTokens, int budgetTokens, List<StowFinding> findings, WorkaroundCount workarounds) {

    static final int LEARNING_SHELF_LIFE_DAYS = 90;

    public StowList {
        findings = List.copyOf(findings);
    }

    public static StowList of(StartupMemory memory, LocalDate today, Map<WorkKey, TicketStanding> tickets) {
        List<StowFinding> findings = new ArrayList<>();
        memory.duplicatedHeadings().forEach(heading -> findings.add(new StowFinding("", heading, "this rule heading appears more than once")));
        memory.files().forEach(file -> file.entries().forEach(entry -> findingOn(file, entry, today, tickets).ifPresent(findings::add)));
        return new StowList(memory.estimatedTokens(), memory.budgetTokens(), findings, WorkaroundCount.of(memory, tickets));
    }

    public int tokensOverBudget() {
        return Math.max(0, estimatedTokens - budgetTokens);
    }

    private static Optional<StowFinding> findingOn(MemoryFile file, MemoryEntry entry, LocalDate today, Map<WorkKey, TicketStanding> tickets) {
        Optional<String> why = file.holdsLearnings() && entry.isAWorkaround()
                ? whyTheWorkaroundIsCurated(entry.until().orElseThrow(), tickets)
                : whyTheEntryIsCurated(file, entry, today);
        return why.map(reason -> new StowFinding(file.name(), entry.shown(), reason));
    }

    private static Optional<String> whyTheWorkaroundIsCurated(WorkKey key, Map<WorkKey, TicketStanding> tickets) {
        return switch (TicketStanding.of(key, tickets)) {
            case OPEN -> Optional.empty();
            case LANDED -> Optional.of("expired: " + key + " has landed, so delete it");
            case UNTICKETED -> Optional.of("unticketed: no task or ticket carries " + key + "; give it a ticket, or turn it into a fact dated today");
            case UNCHECKED -> Optional.of("unchecked: the tracker could not say whether " + key + " has landed; list it again once the tracker answers");
        };
    }

    private static Optional<String> whyTheEntryIsCurated(MemoryFile file, MemoryEntry entry, LocalDate today) {
        Optional<LocalDate> dated = entry.dated();
        if (dated.isEmpty()) {
            return Optional.of("it carries no date");
        }
        if (file.holdsLearnings() && dated.get().isBefore(today.minusDays(LEARNING_SHELF_LIFE_DAYS))) {
            return Optional.of("it was last confirmed on " + dated.get() + ": confirm it with today's date, or move it to archive.md");
        }
        return Optional.empty();
    }
}
