package io.github.ythibos.edi.adapter.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.tracker.WorkflowGraph;

@Component
public class WorkflowCache {

    private final JdbcClient store;
    private final TransactionTemplate transaction;

    public WorkflowCache(JdbcClient store, TransactionTemplate transaction) {
        this.store = store;
        this.transaction = transaction;
    }

    public WorkflowGraph graphOf(ProjectKey project, String issueType) {
        Map<String, List<String>> offeredFrom = new LinkedHashMap<>();
        store.sql("select status from workflow_status where project = :project and issue_type = :type")
                .param("project", project.value())
                .param("type", issueType)
                .query((row, _) -> row.getString(1))
                .list()
                .forEach(status -> offeredFrom.put(status, new ArrayList<>()));
        store.sql("select from_status, to_status from workflow_transition where project = :project and issue_type = :type")
                .param("project", project.value())
                .param("type", issueType)
                .query((row, _) -> Map.entry(row.getString(1), row.getString(2)))
                .list()
                .forEach(transition -> offeredFrom.get(transition.getKey()).add(transition.getValue()));
        return WorkflowGraph.of(offeredFrom);
    }

    public void remember(ProjectKey project, String issueType, String status, List<String> offeredTo) {
        transaction.executeWithoutResult(_ -> {
            store.sql("delete from workflow_transition where project = :project and issue_type = :type and from_status = :status")
                    .param("project", project.value())
                    .param("type", issueType)
                    .param("status", status)
                    .update();
            store.sql("delete from workflow_status where project = :project and issue_type = :type and status = :status")
                    .param("project", project.value())
                    .param("type", issueType)
                    .param("status", status)
                    .update();
            store.sql("insert into workflow_status (project, issue_type, status) values (:project, :type, :status)")
                    .param("project", project.value())
                    .param("type", issueType)
                    .param("status", status)
                    .update();
            offeredTo.stream().distinct().forEach(to -> store.sql("""
                            insert into workflow_transition (project, issue_type, from_status, to_status)
                            values (:project, :type, :from, :to)""")
                    .param("project", project.value())
                    .param("type", issueType)
                    .param("from", status)
                    .param("to", to)
                    .update());
        });
    }
}
