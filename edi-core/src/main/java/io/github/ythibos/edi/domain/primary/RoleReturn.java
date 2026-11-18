package io.github.ythibos.edi.domain.primary;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record RoleReturn(UUID taker, Instant takenAt, Instant takerEndedAt) {

    public static Optional<RoleReturn> toTheHolder(PrimarySession holder, List<PrimarySession> sessions) {
        return sessions.stream()
                .filter(session -> session.hasClaimedTheRole() && session.claimedAt().isAfter(holder.claimedAt()))
                .max(Comparator.comparing(PrimarySession::claimedAt))
                .map(taker -> new RoleReturn(taker.id(), taker.claimedAt(), taker.endedAt()));
    }

    public boolean takerEndedItsSession() {
        return takerEndedAt != null;
    }
}
