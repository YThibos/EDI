package io.github.ythibos.edi.web.task;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.task.Backlog;
import io.github.ythibos.edi.application.task.BacklogEntry;
import io.github.ythibos.edi.domain.task.Blocker;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.web.text.Moments;
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

    static String holdOf(CaptainHold hold) {
        return "held (" + hold.kind().word() + ", " + Moments.until(hold.until()) + "): " + hold.reason();
    }

    static String blockersOf(List<Blocker> blockers) {
        return "blocked by " + blockers.stream().map(BacklogText::blocker).collect(Collectors.joining(", "));
    }

    private static void describe(BacklogEntry entry, Table table) {
        table.row(TaskCells.of(entry.task()));
        if (entry.task().isHeld()) {
            table.line(DETAIL_INDENT + holdOf(entry.task().hold()));
        }
        if (!entry.blockers().isEmpty()) {
            table.line(DETAIL_INDENT + blockersOf(entry.blockers()));
        }
    }

    private static String blocker(Blocker blocker) {
        return blocker.id() + " (" + blocker.state().word() + ")";
    }
}
