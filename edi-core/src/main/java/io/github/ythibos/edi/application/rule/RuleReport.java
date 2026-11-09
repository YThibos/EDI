package io.github.ythibos.edi.application.rule;

import java.time.Instant;
import java.util.List;

public record RuleReport(Instant since, List<FiredRule> fired, List<QuietRule> neverFired) {

    public RuleReport {
        fired = List.copyOf(fired);
        neverFired = List.copyOf(neverFired);
    }

    public record FiredRule(String rule, String protects, long refused, long warned, long allowed, Instant lastFired) {

        public long fired() {
            return refused + warned + allowed;
        }
    }

    public record QuietRule(String rule, String protects) {
    }
}
