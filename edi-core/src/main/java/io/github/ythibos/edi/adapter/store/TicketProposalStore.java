package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.ProposedTicket;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketProposal;

@Component
public class TicketProposalStore {

    private static final String SELECT_PROPOSALS = """
            select task_id, project_key, issue_type, summary, description, epic, labels, priority, proposed_at,
                   create_tried_at, created_key, filled_at, settled_key, settled_at, refusal
            from ticket_proposal
            """;
    private static final String LABEL_SEPARATOR = " ";

    private final JdbcClient store;

    public TicketProposalStore(JdbcClient store) {
        this.store = store;
    }

    public void insert(ProposedTicket proposed) {
        TicketDraft draft = proposed.proposal().draft();
        store.sql("""
                        insert into ticket_proposal (task_id, project_key, issue_type, summary, description, epic, labels, priority, proposed_at)
                        values (:task, :project, :type, :summary, :description, :epic, :labels, :priority, :proposed)""")
                .param("task", proposed.task().value())
                .param("project", draft.project().value())
                .param("type", draft.issueType())
                .param("summary", draft.summary())
                .param("description", draft.description())
                .param("epic", Columns.textOf(draft.epic()))
                .param("labels", String.join(LABEL_SEPARATOR, draft.labels()))
                .param("priority", draft.priority())
                .param("proposed", Columns.text(proposed.proposedAt()))
                .update();
        insertCandidates(proposed.proposal());
        update(proposed);
    }

    public void update(ProposedTicket proposed) {
        store.sql("""
                        update ticket_proposal set create_tried_at = :tried, created_key = :created, filled_at = :filled,
                            settled_key = :settledKey, settled_at = :settled, refusal = :refusal
                        where task_id = :task""")
                .param("task", proposed.task().value())
                .param("tried", Columns.text(proposed.createTriedAt()))
                .param("created", Columns.textOf(proposed.createdKey()))
                .param("filled", Columns.text(proposed.filledAt()))
                .param("settledKey", Columns.textOf(proposed.settledKey()))
                .param("settled", Columns.text(proposed.settledAt()))
                .param("refusal", proposed.refusal())
                .update();
    }

    public Optional<ProposedTicket> of(TaskId task) {
        return store.sql(SELECT_PROPOSALS + "where task_id = :task").param("task", task.value()).query((row, _) -> proposedTicketOf(row)).optional();
    }

    public List<TaskId> answeredAndUnsettled() {
        return store.sql("""
                        select proposal.task_id from ticket_proposal proposal join decision on decision.task_id = proposal.task_id
                        where proposal.settled_at is null and decision.answered_at is not null
                        order by decision.answered_at""")
                .query((row, _) -> new TaskId(row.getString("task_id")))
                .list();
    }

    private void insertCandidates(TicketProposal proposal) {
        List<Ticket> candidates = proposal.candidates();
        IntStream.range(0, candidates.size()).forEach(position ->
                store.sql("insert into ticket_candidate (task_id, position, work_key, summary, status) values (:task, :position, :key, :summary, :status)")
                        .param("task", proposal.task().value())
                        .param("position", position)
                        .param("key", candidates.get(position).key().toString())
                        .param("summary", candidates.get(position).summary())
                        .param("status", candidates.get(position).status())
                        .update());
    }

    private ProposedTicket proposedTicketOf(ResultSet row) throws SQLException {
        TaskId task = new TaskId(row.getString("task_id"));
        TicketDraft draft = new TicketDraft(
                new ProjectKey(row.getString("project_key")),
                row.getString("issue_type"),
                row.getString("summary"),
                row.getString("description"),
                keyOrNull(row.getString("epic")),
                labelsOf(row.getString("labels")),
                row.getString("priority"));
        return new ProposedTicket(
                new TicketProposal(task, draft, candidatesOf(task)),
                Columns.instant(row.getString("proposed_at")),
                Columns.instant(row.getString("create_tried_at")),
                keyOrNull(row.getString("created_key")),
                Columns.instant(row.getString("filled_at")),
                keyOrNull(row.getString("settled_key")),
                Columns.instant(row.getString("settled_at")),
                row.getString("refusal"));
    }

    private List<Ticket> candidatesOf(TaskId task) {
        return store.sql("select work_key, summary, status from ticket_candidate where task_id = :task order by position")
                .param("task", task.value())
                .query((row, _) -> new Ticket(WorkKey.parse(row.getString("work_key")), row.getString("summary"), row.getString("status")))
                .list();
    }

    private static List<String> labelsOf(String joined) {
        return joined.isBlank() ? List.of() : Arrays.asList(joined.split(LABEL_SEPARATOR));
    }

    private static WorkKey keyOrNull(String key) {
        return key == null ? null : WorkKey.parse(key);
    }
}
