package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.call.CallKey;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.HeadChecks;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class DeliveryStore {

    private static final String SELECT_DELIVERIES = "select task_id, provider, host, path, number, url, draft, head_commit, checks, state,"
            + " landed_commit, stacked_on, recorded_at, observed_at from delivery ";

    private final JdbcClient store;

    public DeliveryStore(JdbcClient store) {
        this.store = store;
    }

    public void save(Delivery delivery) {
        ForgeRepository repository = delivery.address().repository();
        store.sql("""
                        insert into delivery (task_id, provider, host, path, number, url, draft, head_commit, checks, state, landed_commit,
                                              stacked_on, recorded_at, observed_at)
                        values (:task, :provider, :host, :path, :number, :url, :draft, :head, :checks, :state, :landed, :stackedOn, :recorded,
                                :observed)
                        on conflict (task_id) do update set provider = excluded.provider, host = excluded.host, path = excluded.path,
                            number = excluded.number, url = excluded.url, draft = excluded.draft, head_commit = excluded.head_commit,
                            checks = excluded.checks, state = excluded.state, landed_commit = excluded.landed_commit,
                            stacked_on = excluded.stacked_on, observed_at = excluded.observed_at""")
                .param("task", delivery.task().value())
                .param("provider", repository.kind().word())
                .param("host", repository.host())
                .param("path", repository.path())
                .param("number", delivery.address().number())
                .param("url", delivery.address().url())
                .param("draft", delivery.draft() ? 1 : 0)
                .param("head", delivery.headCommit())
                .param("checks", delivery.checks().word())
                .param("state", delivery.state().word())
                .param("landed", delivery.landedCommit())
                .param("stackedOn", delivery.stackedOn())
                .param("recorded", Columns.text(delivery.recordedAt()))
                .param("observed", Columns.text(delivery.observedAt()))
                .update();
    }

    public Optional<Delivery> of(TaskId task) {
        return store.sql(SELECT_DELIVERIES + "where task_id = :task").param("task", task.value()).query((row, _) -> deliveryOf(row)).optional();
    }

    public Optional<Delivery> at(String url) {
        return store.sql(SELECT_DELIVERIES + "where url = :url").param("url", url).query((row, _) -> deliveryOf(row)).optional();
    }

    public List<Delivery> settledAwaitingTheirTick() {
        return store.sql(SELECT_DELIVERIES + """
                        where state in (:merged, :closed)
                          and not exists (select 1 from call_tick where call = :call || delivery.task_id and subject = delivery.url)
                        order by observed_at, task_id""")
                .param("merged", MergeRequestState.MERGED.word())
                .param("closed", MergeRequestState.CLOSED.word())
                .param("call", CallKey.REVIEW)
                .query((row, _) -> deliveryOf(row))
                .list();
    }

    public List<Delivery> inState(MergeRequestState state) {
        return store.sql(SELECT_DELIVERIES + "where state = :state order by recorded_at").param("state", state.word())
                .query((row, _) -> deliveryOf(row)).list();
    }

    private static Delivery deliveryOf(ResultSet row) throws SQLException {
        ForgeRepository repository = new ForgeRepository(ForgeKind.named(row.getString("provider")), row.getString("host"), row.getString("path"));
        MergeRequestAddress address = new MergeRequestAddress(repository, row.getInt("number"));
        if (!address.url().equals(row.getString("url"))) {
            throw new IllegalStateException("The delivery of " + row.getString("task_id") + " records " + row.getString("url")
                    + ", which its parts rebuild as " + address + "; the record is refused.");
        }
        return new Delivery(
                new TaskId(row.getString("task_id")),
                address,
                row.getInt("draft") == 1,
                row.getString("head_commit"),
                HeadChecks.named(row.getString("checks")),
                MergeRequestState.named(row.getString("state")),
                row.getString("landed_commit"),
                row.getString("stacked_on"),
                Columns.instant(row.getString("recorded_at")),
                Columns.instant(row.getString("observed_at")));
    }
}
