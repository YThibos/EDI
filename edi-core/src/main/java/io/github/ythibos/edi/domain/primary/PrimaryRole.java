package io.github.ythibos.edi.domain.primary;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public record PrimaryRole(Optional<PrimarySession> holder) {

    public static PrimaryRole among(List<PrimarySession> sessions, Predicate<SessionProcess> stillRuns) {
        return new PrimaryRole(sessions.stream()
                .filter(session -> !session.isEnded() && session.hasClaimedTheRole())
                .filter(session -> session.process() == null || stillRuns.test(session.process()))
                .max(Comparator.comparing(PrimarySession::claimedAt)));
    }

    public Standing standingOf(UUID session) {
        if (holder.isEmpty()) {
            return Standing.UNCLAIMED;
        }
        return holder.get().id().equals(session) ? Standing.PRIMARY : Standing.STANDBY;
    }

    public boolean isVacant() {
        return holder.isEmpty();
    }
}
