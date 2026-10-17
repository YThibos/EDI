package io.github.ythibos.edi.domain.memory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record StartupMemory(List<MemoryFile> files, int budgetTokens) {

    public StartupMemory {
        files = List.copyOf(files);
        if (budgetTokens <= 0) {
            throw new IllegalArgumentException("memory.startupBudgetTokens is a positive number of tokens, such as 18000.");
        }
    }

    public int estimatedTokens() {
        return files.stream().mapToInt(MemoryFile::estimatedTokens).sum();
    }

    public boolean fitsItsBudget() {
        return estimatedTokens() <= budgetTokens;
    }

    public List<String> duplicatedHeadings() {
        Map<String, Long> counted = files.stream()
                .flatMap(file -> file.headings().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counted.entrySet().stream().filter(heading -> heading.getValue() > 1).map(Map.Entry::getKey).sorted().toList();
    }

    public boolean passes() {
        return fitsItsBudget() && duplicatedHeadings().isEmpty();
    }
}
