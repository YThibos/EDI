package io.github.ythibos.edi.web.recovery;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.RecoveryCostStore.CountedRecovery;
import io.github.ythibos.edi.application.supervision.OutageList;
import io.github.ythibos.edi.application.supervision.OutageList.UnpushedWork;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;
import io.github.ythibos.edi.web.worker.WorkerListText;

@Component
class OutageListText implements TextPresenter<OutageList> {

    private static final String DETAIL = "       ";

    @Override
    public Class<OutageList> presents() {
        return OutageList.class;
    }

    @Override
    public String text(OutageList list) {
        List<String> lines = new ArrayList<>();
        if (list.entries().isEmpty()) {
            lines.add("The outage hold list is empty.");
        } else {
            lines.add(Words.counted("Outage hold list", list.entries().size()));
            list.entries().forEach(entry -> lines.addAll(entryLines(entry)));
            lines.add("Answer with one number and resume or fresh, such as edi recover 2 resume or edi recover 4 fresh --effort medium;"
                    + " EDI acts on one number at a time.");
        }
        lines.add(recoverySoFar(list.recoveries()));
        return String.join("\n", lines) + "\n";
    }

    private static List<String> entryLines(OutageList.Entry listed) {
        OutageEntry entry = listed.entry();
        Worker worker = listed.worker();
        return List.of(
                "  %-3d %s  %s  %s".formatted(entry.number(), entry.task(), projectsOf(listed.task()), "lost when " + entry.cause().sentence()),
                DETAIL + "outcome so far: " + WorkerListText.lastStatusOf(worker),
                DETAIL + "waits on: " + waitsOn(listed),
                DETAIL + "ran on " + worker.route() + "; its category gives " + listed.routeNow() + " now",
                DETAIL + "unpushed work: " + unpushed(listed.unpushed()),
                DETAIL + "resume: session " + worker.session() + " in " + listed.worktree());
    }

    private static String projectsOf(Task task) {
        return String.join(" + ", task.intake().projects().stream().map(Object::toString).toList());
    }

    private static String waitsOn(OutageList.Entry listed) {
        List<String> waits = new ArrayList<>();
        if (listed.openDecision() != null) {
            waits.add("the captain's answer to " + listed.openDecision());
        }
        if (listed.task().isHeld() && !listed.entry().holds(listed.task().hold())) {
            waits.add("its hold: " + listed.task().hold().reason());
        }
        Worker worker = listed.worker();
        if (worker.lastReported(StatusState.NEEDS_DECISION) || worker.lastReported(StatusState.BLOCKED) || worker.lastReported(StatusState.PAUSED)) {
            waits.add("its last status, " + worker.lastStatus().report().state().word());
        }
        if (listed.unreadMessages() > 0) {
            waits.add(listed.unreadMessages() + " unread messages in its inbox");
        }
        return waits.isEmpty() ? "nothing; it was working" : String.join(", ", waits);
    }

    private static String unpushed(UnpushedWork unpushed) {
        if (!unpushed.verified()) {
            return "not verified, because git could not read its copy";
        }
        if (!unpushed.exists()) {
            return "none";
        }
        String commits = unpushed.commits() == 0 ? "" : unpushed.commits() + " commits on no remote";
        String changes = unpushed.uncommittedChanges() ? "uncommitted changes" : "";
        return commits.isEmpty() || changes.isEmpty() ? commits + changes : commits + " and " + changes;
    }

    private static String recoverySoFar(List<CountedRecovery> recoveries) {
        long resumed = recoveries.stream().filter(recovery -> recovery.launch() == LaunchKind.RESUME).count();
        TokenUsage tokens = recoveries.stream().map(CountedRecovery::tokens).reduce(TokenUsage.NONE, TokenUsage::plus);
        return "Recovery so far: " + recoveries.size() + " relaunches counted (" + resumed + " resumed, " + (recoveries.size() - resumed)
                + " fresh), " + tokens.total() + " tokens (input " + tokens.input() + ", cache read " + tokens.cacheRead() + ", cache write "
                + tokens.cacheWrite() + ", output " + tokens.output() + ").";
    }
}
