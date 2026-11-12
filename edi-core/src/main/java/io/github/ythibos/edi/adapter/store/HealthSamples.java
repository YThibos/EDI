package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class HealthSamples {

    private final JdbcClient store;

    public HealthSamples(JdbcClient store) {
        this.store = store;
    }

    public void record(List<HealthSample> samples, Instant at) {
        samples.forEach(sample -> store.sql("insert into health_sample (at, measure, count, total, max) values (:at, :measure, :count, :total, :max)")
                .param("at", Columns.text(at))
                .param("measure", sample.measure())
                .param("count", sample.count())
                .param("total", sample.total())
                .param("max", sample.max())
                .update());
    }

    public void forgetBefore(Instant moment) {
        store.sql("delete from health_sample where julianday(at) < julianday(:moment)").param("moment", Columns.text(moment)).update();
    }

    public List<HealthSample> summedSince(Instant since) {
        return store.sql("""
                        select measure, sum(count) as count, sum(total) as total, max(max) as max
                        from health_sample where julianday(at) >= julianday(:since)
                        group by measure order by measure""")
                .param("since", Columns.text(since))
                .query((row, _) -> new HealthSample(row.getString("measure"), row.getLong("count"), row.getDouble("total"), row.getDouble("max")))
                .list();
    }

    public record HealthSample(String measure, long count, double total, double max) {

        public HealthSample plus(HealthSample later) {
            return new HealthSample(measure, count + later.count, total + later.total, Math.max(max, later.max));
        }
    }
}
