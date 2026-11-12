package io.github.ythibos.edi.domain.rule;

import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;

public record RuleFired(Rule rule, RuleOutcome outcome, Actor actor, TaskId task, UUID session, RuleReason reason) {

    public RuleFired {
        Objects.requireNonNull(rule, "A firing names its rule.");
        Objects.requireNonNull(outcome, "A firing says whether it refused, warned or allowed after its check.");
        Objects.requireNonNull(actor, "A firing names who tripped the rule.");
        Objects.requireNonNull(reason, "A firing says why.");
    }

    public static RuleFired refused(Rule rule, Actor actor, TaskId task, String reason) {
        return new RuleFired(rule, RuleOutcome.REFUSED, actor, task, null, RuleReason.of(reason));
    }

    public static RuleFired warned(Rule rule, Actor actor, TaskId task, String reason) {
        return new RuleFired(rule, RuleOutcome.WARNED, actor, task, null, RuleReason.of(reason));
    }

    public RuleFired inSession(UUID claudeSession) {
        return new RuleFired(rule, outcome, actor, task, claudeSession, reason);
    }
}
