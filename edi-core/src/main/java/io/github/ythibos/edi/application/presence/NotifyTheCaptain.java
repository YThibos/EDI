package io.github.ythibos.edi.application.presence;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.adapter.store.PresenceStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.windows.WindowsToasts;
import io.github.ythibos.edi.domain.presence.CaptainNotice;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class NotifyTheCaptain {

    public static final String FOLLOWER = "captain-notifications";

    private final PresenceStore presence;
    private final TaskStore tasks;
    private final WindowsToasts toasts;
    private final TransactionTemplate transaction;

    public NotifyTheCaptain(PresenceStore presence, TaskStore tasks, WindowsToasts toasts, TransactionTemplate transaction) {
        this.presence = presence;
        this.tasks = tasks;
        this.toasts = toasts;
        this.transaction = transaction;
    }

    public void follow(LoggedEvent event) {
        Optional<CaptainNotice> notice = transaction.execute(_ -> presence.current()
                .filter(captain -> keepsTheChatQuietSince(captain, event))
                .flatMap(_ -> CaptainNotices.of(event, taskOf(event))));
        notice.ifPresent(toasts::show);
    }

    private static boolean keepsTheChatQuietSince(Presence captain, LoggedEvent event) {
        return captain.keepsTheChatQuiet() && !event.at().isBefore(captain.since());
    }

    private Task taskOf(LoggedEvent event) {
        return tasks.get(event.task());
    }
}
