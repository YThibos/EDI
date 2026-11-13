package io.github.ythibos.edi.web.task;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.task.Backlog;
import io.github.ythibos.edi.application.task.BacklogEntry;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.Wait;
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

    static String waitsOf(List<Wait> waits) {
        return "waits for " + waits.stream().map(BacklogText::waitFor).collect(Collectors.joining(", "));
    }

    private static void describe(BacklogEntry entry, Table table) {
        table.row(TaskCells.of(entry.task()));
        if (entry.task().isHeld()) {
            table.line(DETAIL_INDENT + holdOf(entry.task().hold()));
        }
        if (!entry.waits().isEmpty()) {
            table.line(DETAIL_INDENT + waitsOf(entry.waits()));
        }
    }

    private static String waitFor(Wait wait) {
        return wait.words() + " (" + wait.stage().word() + ")";
    }
}
