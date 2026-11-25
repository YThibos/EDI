package io.github.ythibos.edi.application.supervision;

import java.nio.file.Path;
import java.util.List;

import io.github.ythibos.edi.adapter.store.RecoveryCostStore.CountedRecovery;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge.NotVerified;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;

public record OutageList(List<Entry> entries, List<CountedRecovery> recoveries) {

    public OutageList {
        entries = List.copyOf(entries);
        recoveries = List.copyOf(recoveries);
    }

    public record Entry(OutageEntry entry, Task task, Worker worker, Route routeNow, int unreadMessages,
            UnpushedWork unpushed, Path worktree) {
    }

    public record UnpushedWork(CommitsOnTheForge commits, boolean uncommittedChanges) {

        static final UnpushedWork UNVERIFIED = new UnpushedWork(new NotVerified("git could not read its copy"), false);
    }
}
