package io.github.ythibos.edi.adapter.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.authority.Authorisation;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.authority.Passage;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class CaptainWordStore {

    private final JdbcClient store;

    public CaptainWordStore(JdbcClient store) {
        this.store = store;
    }

    public void recordTyped(UUID session, Set<Passage> passages, Instant at) {
        passages.forEach(passage -> store.sql("insert into typed_passage (session_id, typed_at, digest) values (:session, :typed, :digest)")
                .param("session", session.toString())
                .param("typed", Columns.text(at))
                .param("digest", digestOf(passage))
                .update());
    }

    public boolean wasTyped(UUID session, Passage passage) {
        return store.sql("select exists (select 1 from typed_passage where session_id = :session and digest = :digest)")
                .param("session", session.toString())
                .param("digest", digestOf(passage))
                .query((row, _) -> row.getBoolean(1))
                .single();
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

    private static String digestOf(Passage passage) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(passage.words().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
