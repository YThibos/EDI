package io.github.ythibos.edi.application.primary;

import java.util.List;

import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.memory.MemoryCheck;
import io.github.ythibos.edi.application.worker.WorkerList;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.recovery.OutageEntry;

public record SessionStartDigest(Standing standing, PrimarySession previousLimitStop, FleetSnapshot fleet, WorkerList workers,
        List<OutageEntry> outage, int wakesWaiting, Presence presence, MemoryCheck memory, boolean upkeepSuggested, List<String> missingFromTheHome) {

    public SessionStartDigest {
        outage = List.copyOf(outage);
        missingFromTheHome = List.copyOf(missingFromTheHome);
    }
}
