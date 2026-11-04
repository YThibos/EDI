package io.github.ythibos.edi.application.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.MachineFixture;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class TaskLinksQueryTest {

    private static final String REGISTRY = """
            projects:
              - name: harness
                tracker: { kind: jira, projectKey: SVC }
                forge: { kind: gitlab, host: forge.example.org, path: team/harness, account: example-owner }
                base: main
              - name: garden
                tracker: { kind: edi, projectKey: GRD }
                forge: { kind: github, path: example-owner/garden, account: example-owner }
                base: main
            """;
    private static final ForgeRepository HARNESS = ForgeRepository.registered(ForgeKind.GITLAB, "forge.example.org", "team/harness");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void openALaptopHome() throws IOException {
        home = TestHome.in(directory).on(MachineFixture.LAPTOP).registering(REGISTRY);
    }

    @Test
    void linksAJiraKeyToTheTrackerAndTheRequestWithItsStateAndPipelineStepAndTheBranchOnGitLab() {
        Task lamp = WorkerFixtures.queuedShip("fit-lamp", "harness", "SVC-7");
        WorkerFixtures.servingWorkerOn(home, lamp, CategoryId.IMPLEMENTATION);
        MergeRequestAddress request = new MergeRequestAddress(HARNESS, 13);
        home.deliveries().save(Delivery.of(lamp.id(), new MergeRequestView(request, MergeRequestState.OPEN, true, "feature/SVC-7", "main", "2222", null,
                null), TestHome.START));
        home.pipelineRuns().save(lamp.id(), new PipelineRun("01RUN", "running", "review", "2222", request.url()), TestHome.START);

        TaskLinks links = linksOf(lamp);

        assertThat(links.key()).isEqualTo("SVC-7");
        assertThat(links.tracker()).isEqualTo("https://jira.example.org/browse/SVC-7");
        assertThat(links.request()).isEqualTo(new TaskLinks.Request("https://forge.example.org/team/harness/-/merge_requests/13", 13, "open", "review"));
        assertThat(links.branch()).isEqualTo(new TaskLinks.Branch("feature/SVC-7", "https://forge.example.org/team/harness/-/tree/feature/SVC-7"));
    }

    @Test
    void givesAnEdiKeyNoTrackerLinkAndLinksAGitHubBranchItsOwnWay() {
        Task bed = WorkerFixtures.queuedShip("dig-bed", "garden", "GRD-2");
        WorkerFixtures.servingWorkerOn(home, bed, CategoryId.IMPLEMENTATION);

        TaskLinks links = linksOf(bed);

        assertThat(links.key()).isEqualTo("GRD-2");
        assertThat(links.tracker()).isNull();
        assertThat(links.request()).isNull();
        assertThat(links.branch()).isEqualTo(new TaskLinks.Branch("feature/GRD-2", "https://github.com/example-owner/garden/tree/feature/GRD-2"));
    }

    @Test
    void keepsTheBoardAfterItsRoundAndLinksTheReportAndBriefOnlyOnceTheyExist() throws IOException {
        Task research = WorkerFixtures.queuedShip("lamp-research", "garden", "GRD-3");
        home.storing(List.of(research));
        Board board = home.boards().arm(research.id(), directory.resolve("board.html").toString(), BoardRounds.asking(1), TestHome.START);
        home.boards().update(board.ended(TestHome.START));
        TaskLinks withoutPages = linksOf(research);
        Path taskDirectory = Files.createDirectories(home.home().taskDirectory(research.id()));
        Files.writeString(taskDirectory.resolve("report.md"), "# Findings\n");
        Files.writeString(taskDirectory.resolve("brief.md"), "# Brief\n");

        TaskLinks withPages = linksOf(research);

        assertThat(withoutPages.board()).isEqualTo(BoardRounds.LINK);
        assertThat(withoutPages.report()).isNull();
        assertThat(withoutPages.brief()).isNull();
        assertThat(withPages.report()).isEqualTo("/api/tasks/lamp-research/pages/report");
        assertThat(withPages.brief()).isEqualTo("/api/tasks/lamp-research/pages/brief");
    }

    private TaskLinks linksOf(Task task) {
        Map<TaskId, TaskLinks> links = home.taskLinks().linksOf(List.of(home.tasks().get(task.id())));
        return links.get(task.id());
    }
}
