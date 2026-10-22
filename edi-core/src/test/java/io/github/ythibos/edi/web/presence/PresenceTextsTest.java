package io.github.ythibos.edi.web.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.presence.ReturnBrief;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Mandate;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.ManualClock;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.PresenterFixtures;
import io.github.ythibos.edi.web.report.CaptainReport;
import io.github.ythibos.edi.web.report.CaptainReportPresenter;
import io.github.ythibos.edi.web.report.ReportWidth;
import io.github.ythibos.edi.web.report.Turn;
import io.github.ythibos.edi.web.text.Moments;

class PresenceTextsTest {

    private static final Instant NOW = Instant.parse("2026-10-17T09:30:00Z");
    private static final Presence PRESENT = Presence.presentSince(Instant.parse("2026-10-17T09:00:00Z"));

    @TempDir
    private Path directory;

    @Test
    void saysEachPresenceInPlainWordsInTheMachinesTimeZone() {
        PresenceWords words = new PresenceWords(new Moments(new ManualClock(NOW, ZoneId.of("Europe/Brussels"))));

        assertThat(words.of(PRESENT)).isEqualTo("present");
        assertThat(words.of(PRESENT.quiet(NOW))).isEqualTo("quiet since 2026-10-17 11:30 CEST, on request");
        assertThat(words.of(PRESENT.quietAfterSilence(PRESENT.since(), Duration.ofMinutes(15), NOW)))
                .isEqualTo("quiet since 2026-10-17 11:15 CEST, after a stretch without a typed message");
        assertThat(words.of(PRESENT.quietForAMeeting(NOW, PRESENT.since(), NOW))).isEqualTo("quiet since 2026-10-17 11:30 CEST, because a meeting began");
        assertThat(words.of(PRESENT.away(new Mandate("Carry on with the lamp.", 2), NOW)))
                .isEqualTo("away since 2026-10-17 11:30 CEST, on the words \"Carry on with the lamp.\", with at most 2 workers at a time");
        assertThat(words.of(PRESENT.demo(NOW, NOW.plus(Duration.ofHours(1))))).isEqualTo("present for a demo until 2026-10-17 12:30 CEST");
    }

    @Test
    void briefsTheReturningCaptainInSectionsToScan() throws IOException {
        TestHome home = homeWithADecisionAndWork();

        String brief = PresenterFixtures.returnBriefText().text(new ReturnBrief(PRESENT.quiet(NOW), home.fleetSnapshot().snapshot().decisions(), List.of(),
                List.of(), home.fleetSnapshot().snapshot().ongoing()));

        assertThat(brief).isEqualTo("""
                EDI return brief: show the captain the lines below verbatim, in one fenced block, before you answer their message.
                While you were away (quiet since 2026-10-17 09:30 UTC, on request):
                Decisions waiting on you (1)
                  D1  Which lamp?
                Landed (0)
                Failed (0)
                Running (1)
                  Work on fit-lamp
                """);
    }

    @Test
    void headsTheCaptainReportWithThePresenceOnlyWhenItIsNotPlainlyPresent() throws IOException {
        TestHome home = homeWithADecisionAndWork();

        String present = report(home, PRESENT);
        String quiet = report(home, PRESENT.quiet(NOW));

        assertThat(present).startsWith("╔══ NEEDS YOUR DECISION");
        assertThat(quiet).startsWith("Presence: quiet since 2026-10-17 09:30 UTC, on request\n\n╔══ NEEDS YOUR DECISION");
    }

    private TestHome homeWithADecisionAndWork() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory);
        WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
        home.storing(List.of(WorkerFixtures.queued("choose-lamp", TaskKind.CAPTAIN, "harness", null)));
        home.raiseDecision().raise(TaskReference.parse("choose-lamp"), Cards.card("Which lamp?"), Actor.PRIMARY);
        return home;
    }

    private static String report(TestHome home, Presence presence) {
        return PresenterFixtures.captainReportPresenter().text(new CaptainReport(home.fleetSnapshot().snapshot(), Turn.NONE, ReportWidth.standard(), presence));
    }
}
