package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.home.InvalidConfiguration;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.dispatch.DispatchTask;
import io.github.ythibos.edi.application.dispatch.LaunchWorker;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CaptainOverride;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.DispatchCategory;
import io.github.ythibos.edi.domain.dispatch.DispatchRules;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.PermissionMode;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.testing.ExampleHome;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@DisplayName("4.3 Dispatch and effort")
class DispatchAndEffortTest {

    private static final Route OPUS_HIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH);

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-14 Routing by written category, three effort levels, high is the ceiling")
    class RoutingByWrittenCategory {

        @Test
        @DisplayName("AT-14 the loader rejects xhigh, max, ultra, a category without why, a weaker researcher and a launch without model and effort")
        void theLoaderRejectsInvalidDispatchRules() throws IOException {
            TestHome home = TestHome.in(directory);

            for (String effort : List.of("xhigh", "max", "ultra")) {
                assertRefused(home, category("research", "claude-opus-5-5", effort, "a reason", false), "high is EDI's ceiling, so not " + effort);
            }
            assertRefused(home, category("research", "claude-opus-5-5", "high", "", false), "The category research states no why");
            assertRefused(home, category("research", "claude-opus-5-5", "high", "a reason", false)
                    .replace("\"why\": \"a reason\"", "\"when\": \"always\""), "The category research states no why");
            assertRefused(home, rules(categoryEntry("research", "claude-sonnet-5-5", "high", "cheap", false),
                    categoryEntry("implementation", "claude-opus-5-5", "high", "strong", false),
                    categoryEntry("implementation-unplanned", "claude-opus-5-5", "high", "unplanned", true)),
                    "needs a researcher at least as strong as the implementer");
            assertRefused(home, category("research", "claude-opus-5-5", null, "a reason", false), "without an effort");
            assertRefused(home, category("research", "claude-opus-6", "high", "a reason", false), "which the machine profile does not allow");
            assertThatThrownBy(() -> EffortLevel.named("xhigh")).hasMessageContaining("high is EDI's ceiling");
            assertThatThrownBy(() -> new Launch(WorkerId.first(new TaskId("fit-lamp")), LaunchKind.DISPATCH, UUID.randomUUID(), null, directory, directory, directory, directory,
                    Map.of(), List.of(), PermissionMode.BYPASS, "brief")).hasMessageContaining("explicit model and effort");
            assertThatThrownBy(() -> new Route(null, EffortLevel.HIGH)).hasMessageContaining("explicit model");
            assertThat(home.writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json"))
                    .dispatchRulesFile().validatedAgainst(home.machineProfile().workers().models()).categories()).hasSize(8);
        }
    }

    @Nested
    @DisplayName("I-15 EDI work runs on Opus 5.5 high")
    class EdiWorkRunsOnTheProjectOverride {

        @Test
        @DisplayName("AT-15 a task on the EDI project resolves to the project override regardless of category")
        @Tag("adapter")
        void anEdiTaskResolvesToTheProjectOverride() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            DispatchRules rules = home.dispatchRulesFile().validatedAgainst(home.machineProfile().workers().models());

            for (DispatchCategory category : rules.categories()) {
                assertThat(rules.routeFor(List.of(new ProjectName("EDI")), category.id(), null)).as("EDI work as %s", category.id()).isEqualTo(OPUS_HIGH);
                assertThat(rules.routeFor(List.of(new ProjectName("harness")), category.id(), null)).isEqualTo(category.route());
            }
            home.storing(List.of(WorkerFixtures.queuedShip("fit-horn", "EDI", "EDI-9")));
            dispatch(home, "fit-horn", "documentation");
            assertThat(home.runtime().lastLaunch().route()).isEqualTo(OPUS_HIGH);
        }
    }

    @Nested
    @DisplayName("I-16 A relaunch re-routes")
    class ARelaunchReRoutes {

        @Test
        @DisplayName("AT-16 after a category's model changes, relaunching a dead worker uses the new model")
        @Tag("adapter")
        void aRelaunchUsesTheCategorysCurrentModel() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
            dispatch(home, "fit-lamp", "implementation");
            assertThat(home.runtime().lastLaunch().route().model()).isEqualTo(new ModelId("claude-sonnet-5-5"));

            home.runtime().closeEveryWindow();
            home.writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json")
                    .replace("\"use\": { \"model\": \"claude-sonnet-5-5\", \"effort\": \"high\" },\n      \"why\": \"The hard reasoning",
                            "\"use\": { \"model\": \"claude-opus-5-5\", \"effort\": \"medium\" },\n      \"why\": \"The hard reasoning"));
            dispatch(home, "fit-lamp", null);

            assertThat(home.runtime().lastLaunch().worker()).isEqualTo(new WorkerId(new TaskId("fit-lamp"), 2));
            assertThat(home.runtime().lastLaunch().route()).isEqualTo(new Route(new ModelId("claude-opus-5-5"), EffortLevel.MEDIUM));
            assertThat(home.workers().get(new WorkerId(new TaskId("fit-lamp"), 2)).orElseThrow().route().model()).isEqualTo(new ModelId("claude-opus-5-5"));
        }
    }

    @Nested
    @DisplayName("I-17 Quality is never traded for cost")
    class QualityIsNeverTradedForCost {

        @Test
        @DisplayName("AT-17 the dispatch resolver takes no budget input")
        void theResolverHasNoBudgetInput() throws NoSuchMethodException {
            Method resolver = DispatchRules.class.getMethod("routeFor", List.class, CategoryId.class, CaptainOverride.class);

            assertThat(resolver.getReturnType()).isEqualTo(Route.class);
            assertThat(Stream.of(DispatchRules.class, DispatchTask.class, LaunchWorker.class, DispatchRequest.class, CaptainOverride.class)
                    .flatMap(type -> Stream.concat(Arrays.stream(type.getDeclaredMethods()).flatMap(method -> Arrays.stream(method.getParameterTypes())),
                            Arrays.stream(type.getDeclaredFields()).map(Field::getType)))
                    .map(type -> type.getSimpleName().toLowerCase(Locale.ROOT)))
                    .noneMatch(name -> Stream.of("budget", "cost", "credit", "quota", "usage", "spend", "price").anyMatch(name::contains));
        }
    }

    private static void dispatch(TestHome home, String task, String category) {
        home.dispatchTask().dispatch(TaskReference.parse(task),
                new DispatchRequest(category == null ? null : new CategoryId(category), "", List.of(), null), Actor.PRIMARY);
    }

    private static void assertRefused(TestHome home, String rules, String reason) throws IOException {
        TestHome written = home.writing("config/dispatch-rules.json", rules);
        assertThatThrownBy(() -> written.dispatchRulesFile().validatedAgainst(written.machineProfile().workers().models()))
                .isInstanceOf(InvalidConfiguration.class)
                .hasMessageContaining("dispatch-rules.json is refused")
                .hasMessageContaining(reason);
    }

    private static String category(String id, String model, String effort, String why, boolean researchFirst) {
        return rules(categoryEntry(id, model, effort, why, researchFirst));
    }

    private static String rules(String... categories) {
        return "{\"schema\": \"edi-dispatch.v2\", \"categories\": [" + String.join(",", categories) + "]}";
    }

    private static String categoryEntry(String id, String model, String effort, String why, boolean researchFirst) {
        String use = effort == null ? "{\"model\": \"" + model + "\"}" : "{\"model\": \"" + model + "\", \"effort\": \"" + effort + "\"}";
        return "{\"id\": \"" + id + "\", \"use\": " + use + ", \"why\": \"" + why + "\", \"researchFirst\": " + researchFirst + "}";
    }
}
