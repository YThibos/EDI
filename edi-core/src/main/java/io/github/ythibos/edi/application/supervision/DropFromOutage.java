package io.github.ythibos.edi.application.supervision;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.application.delivery.DropTask;
import io.github.ythibos.edi.application.delivery.DroppedTask;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.rule.Rule;

@Service
public class DropFromOutage {

    private final LeaveTheOutageList outageList;
    private final DropTask dropTask;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DropFromOutage(LeaveTheOutageList outageList, DropTask dropTask, Clock clock, TransactionTemplate transaction) {
        this.outageList = outageList;
        this.dropTask = dropTask;
        this.clock = clock;
        this.transaction = transaction;
    }

    public DroppedTask drop(int number, boolean discardingWork, CallingSession calling, Actor actor) {
        OutageEntry entry = transaction.execute(_ -> outageList.stillListed(number, clock.now()))
                .orElseThrow(() -> new Refusal(Rule.OUTAGE_LIST, "The outage hold list has no number " + number + "."));
        return dropTask.drop(new TaskReference.ById(entry.task()), discardingWork, calling, actor);
    }
}
