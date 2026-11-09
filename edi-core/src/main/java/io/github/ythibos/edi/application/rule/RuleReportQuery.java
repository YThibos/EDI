package io.github.ythibos.edi.application.rule;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.RuleFirings;
import io.github.ythibos.edi.adapter.store.RuleFirings.FiringCount;
import io.github.ythibos.edi.application.rule.RuleReport.FiredRule;
import io.github.ythibos.edi.application.rule.RuleReport.QuietRule;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleOutcome;

@Service
public class RuleReportQuery {

    private static final String OUT_OF_THE_CATALOGUE = "No rule of the current catalogue has this id.";

    private final RuleFirings firings;
    private final TransactionTemplate transaction;

    public RuleReportQuery(RuleFirings firings, TransactionTemplate transaction) {
        this.firings = firings;
        this.transaction = transaction;
    }

    public RuleReport since(Instant since) {
        Map<String, List<FiringCount>> counted = transaction.execute(_ -> firings.countedSince(since)).stream()
                .collect(Collectors.groupingBy(FiringCount::rule));
        List<FiredRule> fired = counted.entrySet().stream().map(rule -> firedRule(rule.getKey(), rule.getValue()))
                .sorted(Comparator.comparingLong(FiredRule::fired).reversed().thenComparing(FiredRule::rule))
                .toList();
        return new RuleReport(since, fired, quietRules(counted.keySet()));
    }

    private static FiredRule firedRule(String rule, List<FiringCount> counts) {
        return new FiredRule(rule, protectionOf(rule), countOf(counts, RuleOutcome.REFUSED), countOf(counts, RuleOutcome.WARNED),
                countOf(counts, RuleOutcome.ALLOWED_AFTER_A_CHECK),
                counts.stream().map(FiringCount::lastFired).max(Comparator.naturalOrder()).orElseThrow());
    }

    private static String protectionOf(String rule) {
        return Rule.withId(rule).map(Rule::protects).orElse(OUT_OF_THE_CATALOGUE);
    }

    private static long countOf(List<FiringCount> counts, RuleOutcome outcome) {
        return counts.stream().filter(count -> count.outcome() == outcome).mapToLong(FiringCount::fired).sum();
    }

    private static List<QuietRule> quietRules(Set<String> fired) {
        return Arrays.stream(Rule.values()).filter(rule -> !fired.contains(rule.id()))
                .map(rule -> new QuietRule(rule.id(), rule.protects()))
                .toList();
    }
}
