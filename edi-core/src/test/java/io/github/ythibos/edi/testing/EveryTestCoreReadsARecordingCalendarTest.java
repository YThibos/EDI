package io.github.ythibos.edi.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.ythibos.edi.adapter.windows.OutlookCalendar;
import io.github.ythibos.edi.application.presence.MeetingCalendar;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class EveryTestCoreReadsARecordingCalendarTest {

    @TempDir
    private static Path home;

    @Autowired
    private ApplicationContext core;

    @Autowired
    private MeetingCalendar meetings;

    @BeforeAll
    static void prepareAHomeWhoseMachineReadsOutlook() throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/projects.yaml"), Intakes.REGISTRY);
        Files.writeString(home.resolve("config/machine.yaml"), "presence:\n  calendar: outlook\n");
    }

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", () -> home.toString());
        properties.add("edi.workers.socket", () -> "edi-test-" + UUID.randomUUID());
    }

    @Test
    void readsARecordingCalendarInPlaceOfTheCaptainsOutlook() {
        meetings.meetingInProgressAt(Instant.parse("2026-10-30T09:00:00Z"));

        assertThat(core.getBeansOfType(OutlookCalendar.class).values()).singleElement()
                .isInstanceOfSatisfying(RecordingCalendar.class, calendar -> assertThat(calendar.readDays()).isNotEmpty());
    }
}
