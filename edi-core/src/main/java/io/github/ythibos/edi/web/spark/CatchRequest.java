package io.github.ythibos.edi.web.spark;

import static io.github.ythibos.edi.web.RequestValues.optional;
import static io.github.ythibos.edi.web.RequestValues.required;

import java.util.UUID;

import io.github.ythibos.edi.application.spark.SparkRequest;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.spark.SparkAuthor;

record CatchRequest(String text, String task, String project) {

    SparkRequest by(SparkAuthor author, UUID session) {
        return new SparkRequest(required(text, "text"), author, session, optional(task, TaskReference::parse), optional(project, ProjectName::new));
    }
}
