package io.github.ythibos.edi.application.dispatch;

import java.util.List;
import java.util.OptionalInt;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.worker.WorkerId;

public record ProjectRoom(ProjectName project, List<WorkerId> working, OptionalInt capacity) {

    public ProjectRoom {
        working = List.copyOf(working);
    }

    public boolean hasRoom() {
        return capacity.isEmpty() || working.size() < capacity.getAsInt();
    }
}
