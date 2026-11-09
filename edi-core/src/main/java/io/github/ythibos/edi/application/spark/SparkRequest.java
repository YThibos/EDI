package io.github.ythibos.edi.application.spark;

import java.util.UUID;

import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.spark.SparkAuthor;

public record SparkRequest(String idea, SparkAuthor author, UUID session, TaskReference task, ProjectName project) {
}
