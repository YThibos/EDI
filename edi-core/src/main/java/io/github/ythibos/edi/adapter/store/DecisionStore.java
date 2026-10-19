package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class DecisionStore {

    private static final String SELECT_DECISIONS = """
            select number, task_id, title, context, recommended, recommended_because, raised_at,
                   answer_verbatim, answer_option, answer_interpretation, answered_at, answer_channel
            from decision
            """;

    private final JdbcClient store;

    public DecisionStore(JdbcClient store) {
        this.store = store;
    }

    public DecisionNumber nextNumber() {
        store.sql("update meta set value = value + 1 where name = 'decision_seq'").update();
        return new DecisionNumber(lastNumberIssued());
    }

    public int lastNumberIssued() {
        return store.sql("select value from meta where name = 'decision_seq'").query((row, _) -> row.getInt(1)).single();
    }

    public void continueAfter(int highestNumberSoFar) {
        store.sql("update meta set value = max(value, :highest) where name = 'decision_seq'").param("highest", highestNumberSoFar).update();
    }

    public void save(Decision decision) {
        store.sql("""
                        insert into decision (number, task_id, title, context, recommended, recommended_because, raised_at,
                                              answer_verbatim, answer_option, answer_interpretation, answered_at, answer_channel)
                        values (:number, :task, :title, :context, :recommended, :because, :raised,
                                :verbatim, :option, :interpretation, :answered, :channel)
                        on conflict (number) do update set
                            title = excluded.title, context = excluded.context, recommended = excluded.recommended,
                            recommended_because = excluded.recommended_because, raised_at = excluded.raised_at,
                            answer_verbatim = excluded.answer_verbatim, answer_option = excluded.answer_option,
                            answer_interpretation = excluded.answer_interpretation, answered_at = excluded.answered_at,
                            answer_channel = excluded.answer_channel""")
                .param("number", decision.number().value())
                .param("task", decision.task().value())
                .param("title", decision.card().title())
                .param("context", decision.card().context())
                .param("recommended", decision.card().recommendation().key().toString())
                .param("because", decision.card().recommendation().why())
                .param("raised", Columns.text(decision.raisedAt()))
                .params(AnswerColumns.of(decision.answer()).asParameters())
                .update();
        replaceOptions(decision);
    }

    public Decision get(DecisionNumber number) {
        return decisions(store.sql(SELECT_DECISIONS + "where number = :number").param("number", number.value())).stream()
                .findFirst()
                .orElseThrow(() -> new UnknownDecision(number));
    }

    public Optional<Decision> ofTask(TaskId task) {
        return decisions(store.sql(SELECT_DECISIONS + "where task_id = :task").param("task", task.value())).stream().findFirst();
    }

    public List<Decision> all() {
        return decisions(store.sql(SELECT_DECISIONS + "order by number"));
    }

    public List<Decision> open() {
        return decisions(store.sql(SELECT_DECISIONS + "where answered_at is null order by number"));
    }

    public List<Decision> answeredSince(Instant since) {
        return decisions(store.sql(SELECT_DECISIONS + "where answered_at >= :since order by answered_at desc, number desc")
                .param("since", Columns.text(since)));
    }

    private void replaceOptions(Decision decision) {
        store.sql("delete from decision_option where decision_number = :number").param("number", decision.number().value()).update();
        List<DecisionOption> options = decision.card().options();
        IntStream.range(0, options.size()).forEach(position ->
                store.sql("""
                                insert into decision_option (decision_number, position, option_key, option_text, authorises)
                                values (:number, :position, :key, :text, :authorises)""")
                        .param("number", decision.number().value())
                        .param("position", position)
                        .param("key", options.get(position).key().toString())
                        .param("text", options.get(position).text())
                        .param("authorises", authorisedBy(options.get(position)))
                        .update());
    }

    private static String authorisedBy(DecisionOption option) {
        return option.authorises() == null ? null : option.authorises().word();
    }

    private List<Decision> decisions(StatementSpec query) {
        List<DecisionRow> rows = query.query((row, _) -> decisionRow(row)).list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<DecisionOption>> options = optionsOf(rows.stream().map(DecisionRow::number).toList());
        return rows.stream().map(row -> row.decision(options.getOrDefault(row.number(), List.of()))).toList();
    }

    private static DecisionRow decisionRow(ResultSet row) throws SQLException {
        return new DecisionRow(
                row.getInt("number"),
                row.getString("task_id"),
                row.getString("title"),
                row.getString("context"),
                row.getString("recommended"),
                row.getString("recommended_because"),
                row.getString("raised_at"),
                new AnswerColumns(
                        row.getString("answer_verbatim"),
                        row.getString("answer_option"),
                        row.getString("answer_interpretation"),
                        row.getString("answered_at"),
                        row.getString("answer_channel")));
    }

    private Map<Integer, List<DecisionOption>> optionsOf(List<Integer> numbers) {
        return store.sql("""
                        select decision_number, option_key, option_text, authorises from decision_option
                        where decision_number in (:numbers) order by decision_number, position""")
                .param("numbers", numbers)
                .query((row, _) -> Map.entry(row.getInt("decision_number"), option(row)))
                .list()
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static DecisionOption option(ResultSet row) throws SQLException {
        String authorises = row.getString("authorises");
        return new DecisionOption(OptionKey.parse(row.getString("option_key")), row.getString("option_text"),
                authorises == null ? null : CaptainWordAct.onATaskNamed(authorises));
    }

    private record DecisionRow(
            int number,
            String task,
            String title,
            String context,
            String recommended,
            String recommendedBecause,
            String raised,
            AnswerColumns answer) {

        Decision decision(List<DecisionOption> options) {
            DecisionCard card = new DecisionCard(title, context, options, new Recommendation(OptionKey.parse(recommended), recommendedBecause));
            return new Decision(new DecisionNumber(number), new TaskId(task), card, Columns.instant(raised), answer.captainAnswer());
        }
    }

    private record AnswerColumns(String verbatim, String option, String interpretation, String answered, String channel) {

        private static final AnswerColumns UNANSWERED = new AnswerColumns(null, null, null, null, null);

        static AnswerColumns of(CaptainAnswer answer) {
            if (answer == null) {
                return UNANSWERED;
            }
            return new AnswerColumns(
                    answer.verbatim(),
                    Columns.textOf(answer.option()),
                    answer.interpretation(),
                    Columns.text(answer.answeredAt()),
                    answer.channel().word());
        }

        Map<String, Object> asParameters() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("verbatim", verbatim);
            parameters.put("option", option);
            parameters.put("interpretation", interpretation);
            parameters.put("answered", answered);
            parameters.put("channel", channel);
            return parameters;
        }

        CaptainAnswer captainAnswer() {
            if (verbatim == null) {
                return null;
            }
            return new CaptainAnswer(
                    verbatim,
                    option == null ? null : OptionKey.parse(option),
                    interpretation,
                    Columns.instant(answered),
                    AnswerChannel.named(channel));
        }
    }
}
