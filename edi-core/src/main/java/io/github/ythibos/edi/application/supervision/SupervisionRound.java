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
import io.github.ythibos.edi.application.presence.FollowThePresence;
import io.github.ythibos.edi.application.presence.NoticeTheUnreadableCalendar;
import io.github.ythibos.edi.application.tracker.TicketProposals;
import io.github.ythibos.edi.application.worker.CheckMechanicalChange;
import io.github.ythibos.edi.application.worker.NoticeFullContexts;
import io.github.ythibos.edi.application.worker.NoticeHeavyRunsAtOnce;
import io.github.ythibos.edi.application.worker.NoticeMissedLaunches;
import io.github.ythibos.edi.application.worker.NoticeMissedStops;
import io.github.ythibos.edi.application.worker.NoticeOverdueSteers;
import io.github.ythibos.edi.application.worker.RemindOfUnansweredQuestions;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class SupervisionRound implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SupervisionRound.class);

    private final NoticeOverdueSteers overdueSteers;
    private final NoticeMissedLaunches missedLaunches;
    private final LaunchDeferredDispatches deferredDispatches;
    private final CheckMechanicalChange mechanicalChanges;
    private final NoticeFullContexts fullContexts;
    private final NoticeMissedStops missedStops;
    private final JudgeWorkers judgeWorkers;
    private final ResumeAfterUsageLimits usageLimits;
    private final NoticeDueHolds dueHolds;
    private final TicketProposals ticketProposals;
    private final FollowThePresence presence;
    private final NoticeTheUnreadableCalendar unreadableCalendar;
    private final NoticeHeavyRunsAtOnce heavyRuns;
    private final RemindOfUnansweredQuestions unansweredQuestions;
    private final ExecutorService checks = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<WorkerId> checking = ConcurrentHashMap.newKeySet();

    public SupervisionRound(NoticeOverdueSteers overdueSteers, NoticeMissedLaunches missedLaunches,
            LaunchDeferredDispatches deferredDispatches, CheckMechanicalChange mechanicalChanges, NoticeFullContexts fullContexts,
            NoticeMissedStops missedStops, JudgeWorkers judgeWorkers, ResumeAfterUsageLimits usageLimits, NoticeDueHolds dueHolds, TicketProposals ticketProposals,
            FollowThePresence presence, NoticeTheUnreadableCalendar unreadableCalendar, NoticeHeavyRunsAtOnce heavyRuns,
            RemindOfUnansweredQuestions unansweredQuestions) {
        this.overdueSteers = overdueSteers;
        this.missedLaunches = missedLaunches;
        this.deferredDispatches = deferredDispatches;
        this.mechanicalChanges = mechanicalChanges;
        this.fullContexts = fullContexts;
        this.missedStops = missedStops;
        this.judgeWorkers = judgeWorkers;
        this.usageLimits = usageLimits;
        this.dueHolds = dueHolds;
        this.ticketProposals = ticketProposals;
        this.presence = presence;
        this.unreadableCalendar = unreadableCalendar;
        this.heavyRuns = heavyRuns;
        this.unansweredQuestions = unansweredQuestions;
    }

    @Scheduled(fixedDelayString = "${edi.supervision.round:PT30S}", initialDelayString = "${edi.supervision.round:PT30S}")
    public void run() {
        attempt("notice overdue steers", overdueSteers::notice);
        attempt("notice missed launches", missedLaunches::notice);
        attempt("notice workers whose context is full", fullContexts::notice);
        attempt("wake workers whose stop was missed", missedStops::notice);
        attempt("judge the workers", judgeWorkers::judge);
        attempt("raise unanswered worker questions again", unansweredQuestions::remind);
        attempt("resume a worker after its usage limit", usageLimits::resumeTheNextOneDue);
        attempt("notice holds whose date arrived", dueHolds::notice);
        attempt("launch deferred dispatches", deferredDispatches::launchWhereThereIsRoom);
        attempt("check mechanical changes", this::startTheMechanicalChecks);
        attempt("settle the tickets the captain chose", ticketProposals::settleEveryAnswered);
        attempt("follow the captain's presence", presence::follow);
        attempt("tell the captain the calendar cannot be read", unreadableCalendar::notice);
        attempt("notice heavy runs at once", heavyRuns::notice);
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
