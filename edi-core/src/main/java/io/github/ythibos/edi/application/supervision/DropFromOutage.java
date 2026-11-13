package io.github.ythibos.edi.application.supervision;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.OutageStore;
import io.github.ythibos.edi.application.delivery.DropTask;
import io.github.ythibos.edi.application.delivery.DroppedTask;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.rule.Rule;

@Service
public class DropFromOutage {

    private final OutageStore outages;
    private final DropTask dropTask;
    private final TransactionTemplate transaction;

    public DropFromOutage(OutageStore outages, DropTask dropTask, TransactionTemplate transaction) {
        this.outages = outages;
        this.dropTask = dropTask;
        this.transaction = transaction;
    }

    public DroppedTask drop(int number, boolean discardingWork, CallingSession calling, Actor actor) {
        OutageEntry entry = transaction.execute(_ -> outages.numbered(number))
                .orElseThrow(() -> new Refusal(Rule.OUTAGE_LIST, "The outage hold list has no number " + number + "."));
        return dropTask.drop(new TaskReference.ById(entry.task()), discardingWork, calling, actor);
    }
}
