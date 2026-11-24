package io.github.ythibos.edi.application.presence;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.application.fleet.ChangingRequest;
import io.github.ythibos.edi.application.fleet.LandedTask;
import io.github.ythibos.edi.application.fleet.OngoingTask;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.domain.presence.Presence;

public record ReturnBrief(Presence left, WaitingOnTheCaptain waiting, List<LandedTask> landed, List<Setback> setbacks, List<OngoingTask> running,
        List<ChangingRequest> changing) {

    public ReturnBrief {
        landed = List.copyOf(landed);
        setbacks = List.copyOf(setbacks);
        running = List.copyOf(running);
        changing = List.copyOf(changing);
    }

    public Optional<ChangingRequest> comingBackOf(OngoingTask ongoing) {
        return changing.stream().filter(request -> request.task().id().equals(ongoing.task().id())).findFirst();
    }
}
