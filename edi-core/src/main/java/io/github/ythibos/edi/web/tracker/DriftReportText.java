package io.github.ythibos.edi.web.tracker;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.tracker.DriftReport;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class DriftReportText implements TextPresenter<DriftReport> {

    @Override
    public Class<DriftReport> presents() {
        return DriftReport.class;
    }

    @Override
    public String text(DriftReport report) {
        Table table = new Table("  ").line(Words.counted("Ticket drift in " + report.project(), report.drifts().size()));
        report.drifts().forEach(drift -> table.row(drift.key().toString(), drift.status(), drift.kind().sentence()));
        report.unchecked().forEach(reason -> table.line("Not checked: " + reason + "."));
        return table.text();
    }
}
