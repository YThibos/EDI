package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.authority.Authorisation;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class CaptainWordStore {

    private final JdbcClient store;

    public CaptainWordStore(JdbcClient store) {
        this.store = store;
    }

    public void save(Authorisation word) {
        store.sql("insert into captain_word (session_id, task_id, act, verbatim, said_at) values (:session, :task, :act, :verbatim, :said)")
                .param("session", word.session().toString())
                .param("task", Columns.textOf(word.task()))
                .param("act", word.isGeneral() ? null : word.act().word())
                .param("verbatim", word.verbatim())
                .param("said", Columns.text(word.saidAt()))
                .update();
    }

    public List<Authorisation> heardIn(UUID session) {
        return store.sql("select session_id, task_id, act, verbatim, said_at from captain_word where session_id = :session order by number")
                .param("session", session.toString())
                .query((row, _) -> authorisation(row))
                .list();
    }

    private static Authorisation authorisation(ResultSet row) throws SQLException {
        String task = row.getString("task_id");
        String act = row.getString("act");
        return new Authorisation(UUID.fromString(row.getString("session_id")), task == null ? null : new TaskId(task),
                act == null ? null : CaptainWordAct.recordableForAWord(act), row.getString("verbatim"), Columns.instant(row.getString("said_at")));
    }
}
