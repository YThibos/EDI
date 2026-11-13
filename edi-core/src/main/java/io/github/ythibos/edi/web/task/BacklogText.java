package io.github.ythibos.edi.web.task;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.task.Backlog;
import io.github.ythibos.edi.application.task.BacklogEntry;
import io.github.ythibos.edi.domain.task.Wait;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class BacklogText implements TextPresenter<Backlog> {

    private static final String DETAIL_INDENT = "      ";

    @Override
    public Class<Backlog> presents() {
        return Backlog.class;
    }

    @Override
    public String text(Backlog backlog) {
        Table table = new Table("  ").line(Words.counted(backlog.readyOnly() ? "Ready" : "Queued", backlog.entries().size()));
        backlog.entries().forEach(entry -> describe(entry, table));
        return table.text();
    }

    private static void describe(BacklogEntry entry, Table table) {
        table.row(TaskCells.of(entry.task()));
        if (entry.task().isHeld()) {
            table.line(DETAIL_INDENT + Words.hold(entry.task().hold()));
        }
        List<Wait> unmet = Wait.unmet(entry.waits());
        if (!unmet.isEmpty()) {
            table.line(DETAIL_INDENT + Words.waits(unmet));
        }
    }
}
