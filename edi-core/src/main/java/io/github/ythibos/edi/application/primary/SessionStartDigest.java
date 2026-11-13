package io.github.ythibos.edi.application.primary;

import java.util.List;

import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.memory.MemoryCheck;
import io.github.ythibos.edi.application.personality.PersonalityInForce;
import io.github.ythibos.edi.application.worker.WorkerList;
import io.github.ythibos.edi.domain.memory.WorkaroundCount;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.recovery.OutageEntry;

public record SessionStartDigest(Standing standing, PersonalityInForce personality, PrimarySession previousLimitStop, FleetSnapshot fleet, WorkerList workers,
        List<OutageEntry> outage, int wakesWaiting, Presence presence, MemoryCheck memory, WorkaroundCount workarounds, boolean gardeningDue,
        List<String> missingFromTheHome, ConsoleOpening sitrep) {

    public SessionStartDigest {
        outage = List.copyOf(outage);
        missingFromTheHome = List.copyOf(missingFromTheHome);
    }

    public SessionStartDigest withSitrep(ConsoleOpening opening) {
        return new SessionStartDigest(standing, personality, previousLimitStop, fleet, workers, outage, wakesWaiting, presence, memory, workarounds,
                gardeningDue, missingFromTheHome, opening);
    }
}
