package io.github.ythibos.edi.adapter.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.domain.presence.CalendarEntry;
import io.github.ythibos.edi.domain.presence.CalendarEntry.Response;
import io.github.ythibos.edi.domain.presence.CalendarEntry.Showing;
import io.github.ythibos.edi.domain.presence.CaptainNotice;
import io.github.ythibos.edi.testing.ManualClock;
import io.github.ythibos.edi.testing.TestHome;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class WindowsSideTest {

    private static final ManualClock IN_BRUSSELS = new ManualClock(TestHome.START, ZoneId.of("Europe/Brussels"));

    @TempDir
    private Path directory;

    @Test
    void showsAToastOnlyWhereTheMachineProfileAsksForThem() throws IOException {
        TestHome home = TestHome.in(directory);
        boolean withoutToasts = home.windowsToasts().show(new CaptainNotice("Ready for your review", "Fit the lamp: https://example.org/pull/7"));
        home.writing("config/machine.yaml", "notify:\n  windowsToast: true\n");
        boolean withToasts = home.windowsToasts().show(new CaptainNotice("Ready for your review", "Fit the lamp's <brass> & \"glass\""));

        assertThat(withoutToasts).isFalse();
        assertThat(withToasts).isTrue();
        assertThat(home.windowsSide().toasts()).containsExactly("Ready for your review | Fit the lamp's <brass> & \"glass\"");
    }

    @Test
    void readsTheOutlookExportWithOrWithoutAnOffset() throws IOException {
        TestHome home = TestHome.in(directory).writing("config/machine.yaml", "presence:\n  calendar: outlook\n");
        home.windowsSide().showingTheCalendar("""
                [{"Subject":"Sprint review","Start":"2026-10-17T10:00:00.0000000+02:00","End":"2026-10-17T11:00:00.0000000+02:00",
                  "AllDay":false,"ResponseStatus":3,"BusyStatus":2},
                 {"Subject":"Holiday","Start":"2026-10-17T00:00:00","End":"2026-10-18T00:00:00","AllDay":true,"ResponseStatus":0,"BusyStatus":3}]""");
        OutlookCalendar calendar = new OutlookCalendar(new ExternalPrograms(), home.machineProfile(),
                new JsonMapper(), home.windowsSide().executable(), home.windowsSide().windowsDirectory(), IN_BRUSSELS);

        assertThat(calendar.isConfigured()).isTrue();
        assertThat(calendar.entriesOn(LocalDate.of(2026, 10, 17))).containsExactly(
                new CalendarEntry("Sprint review", Instant.parse("2026-10-17T08:00:00Z"), Instant.parse("2026-10-17T09:00:00Z"), false,
                        Response.ACCEPTED, Showing.BUSY),
                new CalendarEntry("Holiday", Instant.parse("2026-10-16T22:00:00Z"), Instant.parse("2026-10-17T22:00:00Z"), true,
                        Response.OWN_APPOINTMENT, Showing.OUT_OF_OFFICE));
    }

    @Test
    void readsTheCalendarWithTheCaptainsOrdinaryRightsEvenFromAnElevatedTerminal() throws IOException {
        TestHome home = TestHome.in(directory).writing("config/machine.yaml", "presence:\n  calendar: outlook\n");
        home.windowsSide().showingTheCalendar("[]");
        OutlookCalendar calendar = new OutlookCalendar(new ExternalPrograms(), home.machineProfile(),
                new JsonMapper(), home.windowsSide().executable(), home.windowsSide().windowsDirectory(), IN_BRUSSELS);

        calendar.entriesOn(LocalDate.of(2026, 10, 17));

        assertThat(home.windowsSide().interopSockets()).containsExactly("/run/WSL/1_interop");
    }

    @Test
    void readsASingleEntryAndRefusesAnExportItCannotRead() throws IOException {
        TestHome home = TestHome.in(directory).writing("config/machine.yaml", "presence:\n  calendar: outlook\n");
        OutlookCalendar calendar = new OutlookCalendar(new ExternalPrograms(), home.machineProfile(),
                new JsonMapper(), home.windowsSide().executable(), home.windowsSide().windowsDirectory(), IN_BRUSSELS);

        home.windowsSide().showingTheCalendar("""
                {"Subject":"Standup","Start":"2026-10-17T09:00:00Z","End":"2026-10-17T09:15:00Z","AllDay":false,"ResponseStatus":1,"BusyStatus":2}""");
        assertThat(calendar.entriesOn(LocalDate.of(2026, 10, 17))).extracting(CalendarEntry::response).containsExactly(Response.ORGANISED);
        home.windowsSide().showingTheCalendar("not json");
        assertThatThrownBy(() -> calendar.entriesOn(LocalDate.of(2026, 10, 17))).isInstanceOf(ProgramFailure.class)
                .hasMessageStartingWith("The Outlook calendar's export is not readable");
    }
}
