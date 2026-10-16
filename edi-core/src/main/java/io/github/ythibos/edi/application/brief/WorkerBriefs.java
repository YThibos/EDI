package io.github.ythibos.edi.application.brief;

import java.util.Map;

import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

@Component
@ImportRuntimeHints(BriefTemplate.class)
public class WorkerBriefs {

    public String briefFor(BriefFacts facts) {
        return BriefTemplate.rendered(Map.ofEntries(
                Map.entry("task", facts.task().id().value()),
                Map.entry("worker", facts.worker().toString()),
                Map.entry("kind", facts.task().intake().kind().word()),
                Map.entry("project", facts.project().project().name().value()),
                Map.entry("key", facts.dispatch().branch().workKey().toString()),
                Map.entry("branch", facts.dispatch().branch().toString()),
                Map.entry("base", facts.base().name()),
                Map.entry("deliveryMode", facts.project().delivery().word()),
                Map.entry("worktree", facts.worktree().toString()),
                Map.entry("taskDirectory", facts.taskDirectory().toString()),
                Map.entry("socket", facts.ediSocket()),
                Map.entry("intent", BriefSections.intent(facts)),
                Map.entry("spec", BriefSections.spec(facts)),
                Map.entry("projectRules", BriefSections.projectRules(facts)),
                Map.entry("largeBuilds", BriefSections.largeBuilds(facts)),
                Map.entry("delivery", BriefSections.delivery(facts)),
                Map.entry("definitionOfDone", BriefSections.definitionOfDone(facts))));
    }
}
