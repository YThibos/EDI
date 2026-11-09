package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.rule.RuleFired;
import io.github.ythibos.edi.domain.rule.RuleOutcome;

@Component
public class RuleFirings {

    private final JdbcClient store;

    public RuleFirings(JdbcClient store) {
        this.store = store;
    }

    public void record(RuleFired firing, Instant at) {
        store.sql("""
                        insert into rule_fired (at, rule, outcome, actor, task_id, session_id, reason)
                        values (:at, :rule, :outcome, :actor, :task, :session, :reason)""")
                .param("at", Columns.text(at))
                .param("rule", firing.rule().id())
                .param("outcome", firing.outcome().word())
                .param("actor", firing.actor().name())
                .param("task", Columns.textOf(firing.task()))
                .param("session", Columns.textOf(firing.session()))
                .param("reason", firing.reason().text())
                .update();
    }

    public List<FiringCount> countedSince(Instant since) {
        return store.sql("""
                        select rule, outcome, count(*) as fired, max(julianday(at)) as last_day, at as last_at
                        from rule_fired where julianday(at) >= julianday(:since)
                        group by rule, outcome order by rule, outcome""")
                .param("since", Columns.text(since))
                .query((row, _) -> new FiringCount(row.getString("rule"), RuleOutcome.named(row.getString("outcome")).orElseThrow(),
                        row.getLong("fired"), Columns.instant(row.getString("last_at"))))
                .list();
    }

    public record FiringCount(String rule, RuleOutcome outcome, long fired, Instant lastFired) {
    }
}
