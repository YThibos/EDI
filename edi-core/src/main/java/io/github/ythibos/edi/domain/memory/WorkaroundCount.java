package io.github.ythibos.edi.domain.memory;

import java.util.List;
import java.util.Map;

import io.github.ythibos.edi.domain.project.WorkKey;

public record WorkaroundCount(int expired, int unticketed) {

    public static WorkaroundCount of(StartupMemory memory, Map<WorkKey, TicketStanding> tickets) {
        List<TicketStanding> standings = memory.workarounds().stream().map(entry -> TicketStanding.of(entry.until().orElseThrow(), tickets)).toList();
        return new WorkaroundCount(countOf(standings, TicketStanding.LANDED), countOf(standings, TicketStanding.UNTICKETED));
    }

    public boolean anyToCurate() {
        return expired > 0 || unticketed > 0;
    }

    private static int countOf(List<TicketStanding> standings, TicketStanding wanted) {
        return (int) standings.stream().filter(wanted::equals).count();
    }
}
