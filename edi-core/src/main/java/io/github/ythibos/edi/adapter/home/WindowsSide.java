package io.github.ythibos.edi.adapter.home;

public record WindowsSide(boolean toasts, boolean outlookCalendar) {

    static final WindowsSide NONE = new WindowsSide(false, false);
}
