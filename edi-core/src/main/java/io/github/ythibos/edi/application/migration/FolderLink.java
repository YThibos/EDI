package io.github.ythibos.edi.application.migration;

import io.github.ythibos.edi.domain.task.TaskId;

public record FolderLink(TaskId task, String path, String target) {
}
