package io.github.ythibos.edi.application.presence;

import java.time.Instant;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.windows.WindowsToasts;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;

@Component
public class NoticeTheUnreadableCalendar {

    private final MeetingCalendar calendar;
    private final PresenceQuery presence;
    private final WindowsToasts toasts;
    private Instant toldOfTheOneUnreadableSince;

    public NoticeTheUnreadableCalendar(MeetingCalendar calendar, PresenceQuery presence, WindowsToasts toasts) {
        this.calendar = calendar;
        this.presence = presence;
        this.toasts = toasts;
    }

    public synchronized void notice() {
        calendar.unreadable()
                .filter(this::isNewsToTheCaptain)
                .filter(_ -> presence.current().keepsTheChatQuiet())
                .ifPresent(this::tellTheCaptain);
    }

    private boolean isNewsToTheCaptain(UnreadableCalendar unreadable) {
        return !unreadable.since().equals(toldOfTheOneUnreadableSince);
    }

    private void tellTheCaptain(UnreadableCalendar unreadable) {
        toasts.show(CaptainNotices.unreadableCalendar());
        toldOfTheOneUnreadableSince = unreadable.since();
    }
}
