package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.UnplannedShip;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class UnplannedShipStore {

    private static final String SELECT_UNPLANNED_SHIPS = "select ship_id, category, research_id, dispatched_at from unplanned_ship ";

    private final JdbcClient store;

    public UnplannedShipStore(JdbcClient store) {
        this.store = store;
    }

    public void insert(UnplannedShip unplanned) {
        store.sql("insert into unplanned_ship (ship_id, category, research_id, dispatched_at) values (:ship, :category, :research, :dispatched)")
                .param("ship", unplanned.ship().value())
                .param("category", unplanned.category().value())
                .param("research", Columns.textOf(unplanned.research()))
                .param("dispatched", Columns.text(unplanned.dispatchedAt()))
                .update();
    }

    public Optional<UnplannedShip> of(TaskId ship) {
        return store.sql(SELECT_UNPLANNED_SHIPS + "where ship_id = :ship").param("ship", ship.value()).query((row, _) -> unplannedShipOf(row)).optional();
    }

    public List<UnplannedShip> finished() {
        return store.sql(SELECT_UNPLANNED_SHIPS + "where ship_id in (select id from task where stage = 'retired') order by dispatched_at, rowid")
                .query((row, _) -> unplannedShipOf(row))
                .list();
    }

    private static UnplannedShip unplannedShipOf(ResultSet row) throws SQLException {
        String research = row.getString("research_id");
        return new UnplannedShip(
                new TaskId(row.getString("ship_id")),
                new CategoryId(row.getString("category")),
                research == null ? null : new TaskId(research),
                Columns.instant(row.getString("dispatched_at")));
    }
}
