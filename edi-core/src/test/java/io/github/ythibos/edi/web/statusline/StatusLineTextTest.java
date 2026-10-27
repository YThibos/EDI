package io.github.ythibos.edi.web.statusline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.statusline.StatusLine;
import io.github.ythibos.edi.domain.branch.CheckedOutBranch;
import io.github.ythibos.edi.domain.primary.PrimaryWait;
import io.github.ythibos.edi.testing.ManualClock;
import tools.jackson.databind.json.JsonMapper;

class StatusLineTextTest {

    private static final CheckedOutBranch CHANGED = new CheckedOutBranch("feature/HRN-3", true);
    private static final CheckedOutBranch CLEAN = new CheckedOutBranch("main", false);
    private static final PrimaryWait LIVE = new PrimaryWait(PrimaryWait.Sighting.LIVE, Instant.EPOCH, 2);

    private final StatusLineText presenter = new StatusLineText(new ManualClock(Instant.EPOCH, ZoneId.of("Europe/Brussels")));

    @Test
    void writesTheFieldsInTheCaptainsOrder() throws IOException {
        String line = withoutColours(rendered("first-answer.json", Optional.of(CHANGED)));

        assertThat(line).isEqualTo("Opus 5.5 · medium -- Context: 6% -- Session tokens: 58k (C 25k/!C 33k) -- 5-hour: 28% (Mon 00:10)"
                + " -- Week: 72% (Mon 10:00) -- Branch: feature/HRN-3* -- Cache: 43%\n");
    }

    @Test
    void writesEveryAbsentValueAsTwoDashes() throws IOException {
        String line = withoutColours(rendered("session-start.json", Optional.empty()));

        assertThat(line).isEqualTo("Opus 5.5 · medium -- Context: -- -- Session tokens: 0k (C 0k/!C 0k) -- 5-hour: 28% (Mon 00:10)"
                + " -- Week: 72% (Mon 10:00) -- Branch: -- -- Cache: --\n");
    }

    @Test
    void writesASessionWithoutUsageLimitsEffortOrCacheTokens() throws IOException {
        String line = withoutColours(rendered("without-limits-or-cache.json", Optional.of(CLEAN)));

        assertThat(line).isEqualTo("Opus 5.5 -- Context: 0% -- Session tokens: 9k (C 0k/!C 9k) -- 5-hour: -- -- Week: -- -- Branch: main -- Cache: 0%\n");
    }

    @Test
    void paintsLabelsMutedCyanValuesLightGreyAndSecondaryTextGrey() throws IOException {
        String line = withVisibleColours(rendered("first-answer.json", Optional.of(CHANGED)));

        assertThat(line).startsWith("<200,200,200>Opus 5.5</> <128,128,128>·</> <200,200,200>medium</> <128,128,128>--</> <95,175,175>Context:</>")
                .contains("<95,175,175>Session tokens:</> 58k <128,128,128>(C 25k/!C 33k)</>", "<128,128,128>(Mon 00:10)</>",
                        "<95,175,175>Branch:</> <200,200,200>feature/HRN-3</><160,120,40>*</>");
        assertThat(withVisibleColours(rendered("session-start.json", Optional.empty())))
                .contains("<95,175,175>Context:</> <128,128,128>--</>", "<95,175,175>Cache:</> <128,128,128>--</>");
    }

    @Test
    void shadesEveryPercentageFromDarkGreenThroughDarkAmberToDarkRed() throws IOException {
        String line = withVisibleColours(rendered("first-answer.json", Optional.empty()));

        assertThat(line).contains("<95,175,175>Context:</> <54,129,40>6%</>", "<95,175,175>5-hour:</> <107,125,40>28%</>",
                "<95,175,175>Week:</> <160,85,40>72%</>");
        assertThat(Shade.of(0)).isEqualTo(new TerminalColour(40, 130, 40));
        assertThat(Shade.of(25)).isEqualTo(new TerminalColour(100, 125, 40));
        assertThat(Shade.of(50)).isEqualTo(new TerminalColour(160, 120, 40));
        assertThat(Shade.of(75)).isEqualTo(new TerminalColour(160, 80, 40));
        assertThat(Shade.of(100)).isEqualTo(new TerminalColour(160, 40, 40));
        assertThat(Shade.of(-4)).isEqualTo(Shade.of(0));
        assertThat(Shade.of(130)).isEqualTo(Shade.of(100));
    }

    @Test
    void shadesTheCacheHitRatioTheOtherWay() throws IOException {
        assertThat(withVisibleColours(rendered("first-answer.json", Optional.empty()))).endsWith("<95,175,175>Cache:</> <160,109,40>43%</>\n");
        assertThat(withVisibleColours(rendered("without-limits-or-cache.json", Optional.empty()))).endsWith("<95,175,175>Cache:</> <160,40,40>0%</>\n");
        assertThat(Shade.theOtherWay(90)).isEqualTo(Shade.of(10));
    }

    @Test
    void marksAMissingWaitInDarkRedOnlyWhileWorkIsInFlight() throws IOException {
        PrimaryWait missing = new PrimaryWait(PrimaryWait.Sighting.MISSING, Instant.EPOCH, 3);
        PrimaryWait missingWithNothingInFlight = new PrimaryWait(PrimaryWait.Sighting.MISSING, Instant.EPOCH, 0);
        PrimaryWait inATurn = new PrimaryWait(PrimaryWait.Sighting.UNCHECKED_IN_A_TURN, Instant.EPOCH, 3);

        assertThat(withoutColours(rendered("without-limits-or-cache.json", Optional.of(CLEAN), missing)))
                .isEqualTo("Opus 5.5 -- Context: 0% -- Session tokens: 9k (C 0k/!C 9k) -- 5-hour: -- -- Week: -- -- Branch: main -- Cache: 0%"
                        + " -- Wait: none (3 in flight)\n");
        assertThat(withVisibleColours(rendered("without-limits-or-cache.json", Optional.of(CLEAN), missing)))
                .endsWith("<95,175,175>Wait:</> <160,40,40>none</> <128,128,128>(3 in flight)</>\n");
        assertThat(withoutColours(rendered("without-limits-or-cache.json", Optional.of(CLEAN), missingWithNothingInFlight))).doesNotContain("Wait");
        assertThat(withoutColours(rendered("without-limits-or-cache.json", Optional.of(CLEAN), inATurn))).doesNotContain("Wait");
        assertThat(withoutColours(rendered("without-limits-or-cache.json", Optional.of(CLEAN)))).doesNotContain("Wait");
    }

    private String rendered(String fixture, Optional<CheckedOutBranch> branch) throws IOException {
        return rendered(fixture, branch, LIVE);
    }

    private String rendered(String fixture, Optional<CheckedOutBranch> branch, PrimaryWait wait) throws IOException {
        try (InputStream payload = StatusLineTextTest.class.getResourceAsStream("/statusline/" + fixture)) {
            return presenter.text(new StatusLine(StatusLinePayload.sessionIn(new JsonMapper().readTree(payload)), branch, wait));
        }
    }

    private static String withoutColours(String line) {
        return line.replaceAll("\u001b\\[[0-9;]*m", "");
    }

    private static String withVisibleColours(String line) {
        return line.replaceAll("\u001b\\[38;2;(\\d+);(\\d+);(\\d+)m", "<$1,$2,$3>").replace("\u001b[0m", "</>");
    }
}
