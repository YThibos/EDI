package io.github.ythibos.edi.application.supervision;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.dispatch.LaunchDeferredDispatches;
import io.github.ythibos.edi.application.tracker.TicketProposals;
import io.github.ythibos.edi.application.worker.CheckMechanicalChange;
import io.github.ythibos.edi.application.worker.NoticeMissedLaunches;
import io.github.ythibos.edi.application.worker.NoticeOverdueSteers;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class SupervisionRound implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SupervisionRound.class);

    private final NoticeOverdueSteers overdueSteers;
    private final NoticeMissedLaunches missedLaunches;
    private final LaunchDeferredDispatches deferredDispatches;
    private final CheckMechanicalChange mechanicalChanges;
    private final JudgeWorkers judgeWorkers;
    private final ResumeAfterUsageLimits usageLimits;
    private final NoticeDueHolds dueHolds;
    private final TicketProposals ticketProposals;
    private final ExecutorService checks = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<WorkerId> checking = ConcurrentHashMap.newKeySet();

    public SupervisionRound(NoticeOverdueSteers overdueSteers, NoticeMissedLaunches missedLaunches,
            LaunchDeferredDispatches deferredDispatches, CheckMechanicalChange mechanicalChanges, JudgeWorkers judgeWorkers,
            ResumeAfterUsageLimits usageLimits, NoticeDueHolds dueHolds, TicketProposals ticketProposals) {
        this.overdueSteers = overdueSteers;
        this.missedLaunches = missedLaunches;
        this.deferredDispatches = deferredDispatches;
        this.mechanicalChanges = mechanicalChanges;
        this.judgeWorkers = judgeWorkers;
        this.usageLimits = usageLimits;
        this.dueHolds = dueHolds;
        this.ticketProposals = ticketProposals;
    }

    @Scheduled(fixedDelayString = "${edi.supervision.round:PT30S}", initialDelayString = "${edi.supervision.round:PT30S}")
    public void run() {
        attempt("notice overdue steers", overdueSteers::notice);
        attempt("notice missed launches", missedLaunches::notice);
        attempt("judge the workers", judgeWorkers::judge);
        attempt("resume a worker after its usage limit", usageLimits::resumeTheNextOneDue);
        attempt("notice holds whose date arrived", dueHolds::notice);
        attempt("launch deferred dispatches", deferredDispatches::launchWhereThereIsRoom);
        attempt("check mechanical changes", this::startTheMechanicalChecks);
        attempt("settle the tickets the captain chose", ticketProposals::settleEveryAnswered);
    }

    @Override
    public void close() {
        checks.shutdownNow();
    }

    private static void attempt(String part, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException failed) {
            LOG.warn("The supervision round could not {}: {}", part, failed.getMessage());
        }
    }

    private void startTheMechanicalChecks() {
        mechanicalChanges.awaitingTheirCheck().stream().filter(checking::add).forEach(this::checkInTheBackground);
    }

    private void checkInTheBackground(WorkerId worker) {
        checks.submit(() -> {
            try {
                mechanicalChanges.check(worker);
            } catch (RuntimeException failed) {
                LOG.warn("The mechanical check of {} could not finish: {}", worker, failed.getMessage());
            } finally {
                checking.remove(worker);
            }
        });
    }
}
