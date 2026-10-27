package io.github.ythibos.edi.application.brief;

import java.util.Map;

import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.delivery.PipelineIntent;

@Component
@ImportRuntimeHints(BriefTemplate.class)
public class WorkerBriefs {

    public String briefFor(BriefFacts facts) {
        return BriefTemplate.rendered(valuesOf(facts));
    }

    public void requireWithinItsLength(BriefFacts facts) {
        BriefLength.requireWithinTheLimit(BriefTemplate.sections(valuesOf(facts.withoutTheCaptainsNotesOrResearchPlan())));
    }

    public PipelineIntent pipelineIntentFor(BriefFacts facts) {
        return PipelineIntent.of(facts.dispatch().branch().workKey(), BriefSections.intent(facts), BriefSections.spec(facts),
                facts.project().craftRules() ? facts.craftRules() : null);
    }

    private static Map<String, String> valuesOf(BriefFacts facts) {
        return Map.ofEntries(
                Map.entry("task", facts.task().id().value()),
                Map.entry("worker", facts.worker().toString()),
                Map.entry("kind", facts.task().intake().kind().word()),
                Map.entry("project", facts.project().project().name().value()),
                Map.entry("key", facts.dispatch().branch().workKey().toString()),
                Map.entry("branch", facts.dispatch().branch().toString()),
                Map.entry("base", facts.base().name()),
                Map.entry("deliveryMode", facts.delivery().word()),
                Map.entry("worktree", facts.worktree().toString()),
                Map.entry("taskDirectory", facts.taskDirectory().toString()),
                Map.entry("socket", facts.ediSocket()),
                Map.entry("intent", BriefSections.intent(facts)),
                Map.entry("spec", BriefSections.spec(facts)),
                Map.entry("projectRules", BriefSections.projectRules(facts)),
                Map.entry("largeBuilds", BriefSections.largeBuilds(facts)),
                Map.entry("delivery", BriefSections.delivery(facts)),
                Map.entry("definitionOfDone", BriefSections.definitionOfDone(facts)));
    }
}
