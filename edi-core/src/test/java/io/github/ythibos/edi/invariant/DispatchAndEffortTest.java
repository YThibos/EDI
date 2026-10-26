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
import java.util.Set;
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
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
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
    private static final Set<CategoryId> EDIS_RESEARCH_AND_IMPLEMENTATION = Set.of(CategoryId.RESEARCH, CategoryId.IMPLEMENTATION,
            CategoryId.IMPLEMENTATION_UNPLANNED, CategoryId.BUGFIX);
    private static final String PIPELINE = "{\"use\": {\"model\": \"claude-opus-5-5\", \"effort\": \"high\"}, \"why\": \"The last judge.\"}";
    private static final Route OPUS_XHIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.XHIGH);
    private static final Route OPUS_MAX = new Route(new ModelId("claude-opus-5-5"), EffortLevel.MAX);
    private static final Route SONNET_HIGH = new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH);
    private static final String THE_REFUSAL_WITHOUT_THE_LAMPS_EFFORT_WORD = "To run one task above high, EDI's effort ceiling, EDI needs the captain's"
            + " word on fit-lamp in this session recorded for it: record it with edi captain said --task fit-lamp --for effort-above-high"
            + " --text \"<their words>\".";

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-14 Routing by written category, high is the ceiling, and only the captain's word lifts it for one task")
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
            assertRefused(home, category("research", "claude-opus-5-5", "high", "a reason", false).replace(PIPELINE, PIPELINE.replace("high", "max")),
                    "high is EDI's ceiling, so not max");
            assertRefused(home, category("research", "claude-opus-5-5", "high", "a reason", false).replace(", \"pipeline\": " + PIPELINE, ""),
                    "it names no pipeline entry, so the validation pipeline's agent would run at the harness default");
            assertThatThrownBy(() -> EffortLevel.withinTheCeiling("xhigh")).hasMessageContaining("high is EDI's ceiling");
            assertThatThrownBy(() -> EffortLevel.named("ultra")).hasMessageContaining("lifts that task to xhigh or max");
            assertThatThrownBy(() -> new Launch(WorkerId.first(new TaskId("fit-lamp")), LaunchKind.DISPATCH, UUID.randomUUID(), null, directory, directory, directory, directory,
                    Map.of(), List.of(), PermissionMode.BYPASS, "brief")).hasMessageContaining("explicit model and effort");
            assertThatThrownBy(() -> new Route(null, EffortLevel.HIGH)).hasMessageContaining("explicit model");
            assertThat(home.writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json"))
                    .dispatchRulesFile().validatedAgainst(home.machineProfile().workers().models()).categories()).hasSize(8);
        }

        @Test
        @DisplayName("the captain's word recorded on a task for effort above high in this session lifts that task to xhigh or max, with a reason")
        @Tag("adapter")
        void theCaptainsWordOnATaskLiftsItAboveHigh() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4")));
            CallingSession session = primarySession(home);
            captainSaysForEffortAboveHigh(home, session, "fit-lamp", "Fit the lamp on Opus at xhigh, it matters.");
            captainSaysForEffortAboveHigh(home, session, "fit-horn", "The horn runs at max.");

            dispatchAboveHigh(home, "fit-lamp", EffortLevel.XHIGH, session);
            assertThat(home.runtime().lastLaunch().route()).isEqualTo(OPUS_XHIGH);
            dispatchAboveHigh(home, "fit-horn", EffortLevel.MAX, session);
            assertThat(home.runtime().lastLaunch().route()).isEqualTo(OPUS_MAX);
            assertThatThrownBy(() -> new CaptainOverride(OPUS_XHIGH, " ")).hasMessageContaining("records the captain's reason");
        }

        @Test
        @DisplayName("without the captain's word on the task for effort above high, effort above high is refused with how to record it")
        @Tag("adapter")
        void effortAboveHighWithoutTheCaptainsWordIsRefused() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
            CallingSession session = primarySession(home);

            assertThatThrownBy(() -> dispatchAboveHigh(home, "fit-lamp", EffortLevel.XHIGH, session))
                    .isInstanceOf(Refusal.class)
                    .hasMessage(THE_REFUSAL_WITHOUT_THE_LAMPS_EFFORT_WORD);
            assertThat(home.runtime().launches()).isEmpty();
        }

        @Test
        @DisplayName("a general word on the task, such as one recorded to discard its work, lifts nothing")
        @Tag("adapter")
        void aGeneralWordOnTheTaskLiftsNothing() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
            CallingSession session = primarySession(home);
            captainSays(home, session, "fit-lamp", "Discard the work on fit-lamp and start it over.");

            assertThatThrownBy(() -> dispatchAboveHigh(home, "fit-lamp", EffortLevel.XHIGH, session))
                    .isInstanceOf(Refusal.class)
                    .hasMessage(THE_REFUSAL_WITHOUT_THE_LAMPS_EFFORT_WORD);
            assertThat(home.runtime().launches()).isEmpty();
        }

        @Test
        @DisplayName("the captain's effort word on another task, or a word on no task, lifts nothing")
        @Tag("adapter")
        void theCaptainsWordOnAnotherTaskLiftsNothing() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4")));
            CallingSession session = primarySession(home);
            captainSaysForEffortAboveHigh(home, session, "fit-horn", "The horn runs at xhigh.");
            home.primaryHooks().promptSubmitted(session.id(), "Everything at xhigh today.");
            home.captainsWord().record(session, "Everything at xhigh today.", null, null, Actor.PRIMARY);

            assertThatThrownBy(() -> dispatchAboveHigh(home, "fit-lamp", EffortLevel.XHIGH, session))
                    .isInstanceOf(Refusal.class).hasMessage(THE_REFUSAL_WITHOUT_THE_LAMPS_EFFORT_WORD);
            assertThat(home.runtime().launches()).isEmpty();
        }

        @Test
        @DisplayName("a relaunch of the lifted task keeps its effort in a later session, and another task does not inherit it")
        @Tag("adapter")
        void aRelaunchKeepsTheLiftAndNoOtherTaskInheritsIt() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4")));
            CallingSession session = primarySession(home);
            captainSaysForEffortAboveHigh(home, session, "fit-lamp", "Fit the lamp on Opus at xhigh, it matters.");
            dispatchAboveHigh(home, "fit-lamp", EffortLevel.XHIGH, session);
            home.runtime().closeEveryWindow();
            primarySession(home);

            home.relaunchWorker().relaunch(TaskReference.parse("fit-lamp"), Actor.PRIMARY);
            assertThat(home.runtime().lastLaunch().worker()).isEqualTo(new WorkerId(new TaskId("fit-lamp"), 2));
            assertThat(home.runtime().lastLaunch().route()).isEqualTo(OPUS_XHIGH);

            dispatch(home, "fit-horn", "implementation");
            assertThat(home.runtime().lastLaunch().route()).isEqualTo(SONNET_HIGH);
        }
    }

    @Nested
    @DisplayName("I-15 EDI's own research and implementation run on Opus 5.5 high")
    class EdiWorkRunsOnTheProjectOverride {

        @Test
        @DisplayName("AT-15 a research or implementation task on the EDI project resolves to the project override, any other to its category")
        @Tag("adapter")
        void anEdiResearchOrImplementationTaskResolvesToTheProjectOverride() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            DispatchRules rules = home.dispatchRulesFile().validatedAgainst(home.machineProfile().workers().models());

            for (DispatchCategory category : rules.categories()) {
                Route ediRoute = EDIS_RESEARCH_AND_IMPLEMENTATION.contains(category.id()) ? OPUS_HIGH : category.route();
                assertThat(rules.routeFor(List.of(new ProjectName("EDI")), category.id(), null)).as("EDI work as %s", category.id()).isEqualTo(ediRoute);
                assertThat(rules.routeFor(List.of(new ProjectName("harness")), category.id(), null)).isEqualTo(category.route());
            }
            home.storing(List.of(WorkerFixtures.queuedShip("fit-horn", "EDI", "EDI-9"), WorkerFixtures.queuedShip("fit-bell", "EDI", "EDI-10")));
            dispatch(home, "fit-horn", "implementation");
            assertThat(home.runtime().lastLaunch().route()).isEqualTo(OPUS_HIGH);
            dispatch(home, "fit-bell", "documentation");
            assertThat(home.runtime().lastLaunch().route()).isEqualTo(rules.category(new CategoryId("documentation")).route());
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
                new DispatchRequest(category == null ? null : new CategoryId(category), "", List.of(), null), CallingSession.UNNAMED, Actor.PRIMARY);
    }

    private static CallingSession primarySession(TestHome home) {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, true);
        return new CallingSession(session);
    }

    private static void captainSaysForEffortAboveHigh(TestHome home, CallingSession session, String task, String words) {
        recordTheCaptainsWord(home, session, task, words, CaptainWordAct.EFFORT_ABOVE_HIGH);
    }

    private static void captainSays(TestHome home, CallingSession session, String task, String words) {
        recordTheCaptainsWord(home, session, task, words, null);
    }

    private static void recordTheCaptainsWord(TestHome home, CallingSession session, String task, String words, CaptainWordAct act) {
        home.primaryHooks().promptSubmitted(session.id(), words);
        home.captainsWord().record(session, words, TaskReference.parse(task), act, Actor.PRIMARY);
    }

    private static void dispatchAboveHigh(TestHome home, String task, EffortLevel effort, CallingSession session) {
        CaptainOverride captainsOverride = new CaptainOverride(new Route(new ModelId("claude-opus-5-5"), effort), "the captain asked for " + effort.word());
        home.dispatchTask().dispatch(TaskReference.parse(task), new DispatchRequest(new CategoryId("research"), "", List.of(), captainsOverride),
                session, Actor.PRIMARY);
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
        return "{\"schema\": \"edi-dispatch.v2\", \"categories\": [" + String.join(",", categories) + "], \"pipeline\": " + PIPELINE + "}";
    }

    private static String categoryEntry(String id, String model, String effort, String why, boolean researchFirst) {
        String use = effort == null ? "{\"model\": \"" + model + "\"}" : "{\"model\": \"" + model + "\", \"effort\": \"" + effort + "\"}";
        return "{\"id\": \"" + id + "\", \"use\": " + use + ", \"why\": \"" + why + "\", \"researchFirst\": " + researchFirst + "}";
    }
}
