package io.github.ythibos.edi.domain.memory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record StowList(int estimatedTokens, int budgetTokens, List<StowFinding> findings) {

    public static final String LEARNINGS = "learnings.md";
    static final int LEARNING_SHELF_LIFE_DAYS = 90;

    public StowList {
        findings = List.copyOf(findings);
    }

    public static StowList of(StartupMemory memory, LocalDate today) {
        List<StowFinding> findings = new ArrayList<>();
        memory.duplicatedHeadings().forEach(heading -> findings.add(new StowFinding("", heading, "this rule heading appears more than once")));
        memory.files().forEach(file -> findings.addAll(findingsIn(file, today)));
        return new StowList(memory.estimatedTokens(), memory.budgetTokens(), findings);
    }

    public int tokensOverBudget() {
        return Math.max(0, estimatedTokens - budgetTokens);
    }

    private static List<StowFinding> findingsIn(MemoryFile file, LocalDate today) {
        List<StowFinding> findings = new ArrayList<>();
        for (MemoryEntry entry : file.entries()) {
            Optional<LocalDate> dated = entry.dated();
            if (dated.isEmpty()) {
                findings.add(new StowFinding(file.name(), entry.shown(), "it carries no date"));
            } else if (file.name().equals(LEARNINGS) && dated.get().isBefore(today.minusDays(LEARNING_SHELF_LIFE_DAYS))) {
                findings.add(new StowFinding(file.name(), entry.shown(),
                        "it was last confirmed on " + dated.get() + ": confirm it with today's date, or move it to archive.md"));
            }
        }
        return findings;
    }
}
