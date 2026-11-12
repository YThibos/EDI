package io.github.ythibos.edi.application.dispatch;

import java.util.OptionalInt;

import io.github.ythibos.edi.domain.project.ProjectName;

public record ProjectRoom(ProjectName project, int occupied, OptionalInt capacity) {

    public boolean hasRoom() {
        return capacity.isEmpty() || occupied < capacity.getAsInt();
    }
}
