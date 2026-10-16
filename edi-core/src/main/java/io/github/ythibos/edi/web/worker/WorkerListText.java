package io.github.ythibos.edi.web.worker;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.worker.WorkerList;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class WorkerListText implements TextPresenter<WorkerList> {

    @Override
    public Class<WorkerList> presents() {
        return WorkerList.class;
    }

    @Override
    public String text(WorkerList workers) {
        Table table = new Table("  ").line(Words.counted("Workers", workers.entries().size()));
        workers.entries().forEach(entry -> table.row(
                entry.worker().id().toString(),
                entry.windowOpen() ? entry.worker().activity().word() : "window closed",
                entry.worker().category().value(),
                entry.worker().route().toString(),
                lastStatusOf(entry.worker())));
        return table.text();
    }

    private static String lastStatusOf(Worker worker) {
        return worker.lastStatus() == null
                ? "no status yet"
                : worker.lastStatus().report().state().word() + ": " + worker.lastStatus().report().text();
    }
}
