package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.NoticeKind;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerNotice;

@Component
public class NoticeStore {

    private final JdbcClient store;

    public NoticeStore(JdbcClient store) {
        this.store = store;
    }

    public boolean wasNoticed(WorkerId worker, WorkerNotice notice) {
        return store.sql("select exists (select 1 from worker_notice where worker_id = :worker and kind = :kind and since = :since)")
                .param("worker", worker.toString())
                .param("kind", notice.kind().word())
                .param("since", Columns.text(notice.since()))
                .query(Boolean.class)
                .single();
    }

    public void notice(WorkerId worker, WorkerNotice notice, Instant at) {
        store.sql("insert into worker_notice (worker_id, kind, since, noticed_at) values (:worker, :kind, :since, :noticed)")
                .param("worker", worker.toString())
                .param("kind", notice.kind().word())
                .param("since", Columns.text(notice.since()))
                .param("noticed", Columns.text(at))
                .update();
    }

    public Optional<Instant> latestNoticed(NoticeKind kind) {
        return store.sql("select noticed_at from worker_notice where kind = :kind")
                .param("kind", kind.word())
                .query((row, _) -> Columns.instant(row.getString("noticed_at")))
                .list()
                .stream()
                .max(Instant::compareTo);
    }

    public boolean wasNoticed(TaskId task, CaptainHold hold) {
        return store.sql("select exists (select 1 from hold_notice where task_id = :task and set_at = :set)")
                .param("task", task.value())
                .param("set", Columns.text(hold.setAt()))
                .query(Boolean.class)
                .single();
    }

    public void notice(TaskId task, CaptainHold hold, Instant at) {
        store.sql("insert into hold_notice (task_id, set_at, noticed_at) values (:task, :set, :noticed)")
                .param("task", task.value())
                .param("set", Columns.text(hold.setAt()))
                .param("noticed", Columns.text(at))
                .update();
    }
}
