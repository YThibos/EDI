package io.github.ythibos.edi.testing;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.brief.BriefFacts;
import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.mechanical.ExpectedChange;
import io.github.ythibos.edi.domain.project.AiScaffolding;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.ShipBriefRule;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.WorkerId;

public final class BriefFixtures {

    private static final Path HOME = Path.of("/home/developer/.edi");
    private static final TaskId TASK = new TaskId("fit-lamp");

    private BriefFixtures() {
    }

    public static Stream<BriefFacts> everyKindOfBrief() {
        return Stream.of(
                facts(TaskKind.SCOUT, DeliveryMode.DRAFT_REVIEW, CategoryId.RESEARCH, "", AiScaffolding.ASK),
                facts(TaskKind.SHIP, DeliveryMode.DRAFT_REVIEW, CategoryId.IMPLEMENTATION, "Fit the brass lamp.", AiScaffolding.COMMITTED),
                facts(TaskKind.SHIP, DeliveryMode.LOCAL_BRANCH, CategoryId.IMPLEMENTATION, "", AiScaffolding.LOCAL_ONLY),
                facts(TaskKind.SHIP, DeliveryMode.LOCAL_APPLY, CategoryId.MECHANICAL, "Rename Lamp to Lantern in src/Lamp.java.", AiScaffolding.LOCAL_ONLY));
    }

    public static BriefFacts largestShip(String intent, String spec) {
        Task task = Task.queued(TASK, intake(TaskKind.SHIP), intent, TestHome.START);
        return new BriefFacts(task, WorkerId.first(TASK), project(DeliveryMode.DRAFT_REVIEW, AiScaffolding.LOCAL_ONLY),
                dispatch(CategoryId.MECHANICAL, spec), new BaseBranch("main"), worktree(), taskDirectory(), "edi", WorkerFixtures.CRAFT_RULES);
    }

    private static BriefFacts facts(TaskKind kind, DeliveryMode delivery, CategoryId category, String spec, AiScaffolding aiScaffolding) {
        Task task = Task.queued(TASK, intake(kind), "Fit the lamp in the hall; offline verification is accepted.", TestHome.START);
        return new BriefFacts(task, WorkerId.first(TASK), project(delivery, aiScaffolding), dispatch(category, spec), new BaseBranch("main"),
                worktree(), taskDirectory(), "edi", WorkerFixtures.CRAFT_RULES);
    }

    private static Intake intake(TaskKind kind) {
        return new Intake(kind, "Fit the lamp", List.of(new ProjectName("harness")), WorkKey.parse("HRN-3"), null, List.of());
    }

    private static ProjectEntry project(DeliveryMode delivery, AiScaffolding aiScaffolding) {
        return new ProjectEntry(new Project(new ProjectName("harness"), new ProjectKey("HRN")), HOME.resolve("projects/harness"),
                Path.of("/checkouts/harness"), new BaseBranch("main"), delivery, EnumSet.allOf(BranchKind.class), true, aiScaffolding, null,
                "./mvnw -B verify", true, List.of(ShipBriefRule.DECISION_LOG_ADR, ShipBriefRule.NO_EXPLANATORY_COMMENTS));
    }

    private static Dispatch dispatch(CategoryId category, String spec) {
        ExpectedChange expected = category.isMechanical() ? new ExpectedChange(spec.isBlank() ? "Rename Lamp to Lantern." : spec, List.of("src/Lamp.java")) : null;
        return Dispatch.requested(TASK, category, BranchName.forWork(BranchKind.FEATURE, WorkKey.parse("HRN-3")), spec, expected, null, TestHome.START);
    }

    private static Path worktree() {
        return HOME.resolve("worktrees/harness/fit-lamp");
    }

    private static Path taskDirectory() {
        return HOME.resolve("data/tasks/fit-lamp");
    }
}
