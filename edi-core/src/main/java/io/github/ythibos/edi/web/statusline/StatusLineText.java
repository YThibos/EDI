package io.github.ythibos.edi.web.statusline;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.statusline.StatusLine;
import io.github.ythibos.edi.domain.branch.CheckedOutBranch;
import io.github.ythibos.edi.domain.statusline.ClaudeSessionStatus;
import io.github.ythibos.edi.domain.statusline.SessionTokens;
import io.github.ythibos.edi.domain.statusline.UsageLimit;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class StatusLineText implements TextPresenter<StatusLine> {

    private static final TerminalColour LABEL = new TerminalColour(95, 175, 175);
    private static final TerminalColour VALUE = new TerminalColour(200, 200, 200);
    private static final TerminalColour SECONDARY = new TerminalColour(128, 128, 128);
    private static final String SEPARATOR = " " + SECONDARY.paint("--") + " ";
    private static final String ABSENT = SECONDARY.paint("--");
    private static final DateTimeFormatter RESET_MOMENT = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);
    private static final long THOUSAND = 1000;
    private static final String DIRTY_MARK = "*";

    private final ZoneId zone;

    @Autowired
    StatusLineText() {
        this(ZoneId.systemDefault());
    }

    StatusLineText(ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public Class<StatusLine> presents() {
        return StatusLine.class;
    }

    @Override
    public String text(StatusLine line) {
        ClaudeSessionStatus session = line.session();
        return String.join(SEPARATOR,
                modelAndEffort(session),
                field("Context", percentage(session.contextUsed())),
                field("Session tokens", tokens(session.tokens())),
                field("5-hour", limit(session.fiveHourLimit())),
                field("Week", limit(session.weeklyLimit())),
                field("Branch", branch(line.branch())),
                field("Cache", cacheHitRatio(session.tokens()))) + "\n";
    }

    private static String modelAndEffort(ClaudeSessionStatus session) {
        String model = session.model().map(VALUE::paint).orElse(ABSENT);
        return session.effort().map(effort -> model + " " + SECONDARY.paint("·") + " " + VALUE.paint(effort)).orElse(model);
    }

    private static String field(String label, String value) {
        return LABEL.paint(label + ":") + " " + value;
    }

    private static String percentage(OptionalInt percentage) {
        return percentage.isPresent() ? shaded(percentage.getAsInt()) : ABSENT;
    }

    private static String shaded(int percentage) {
        return Shade.of(percentage).paint(percentage + "%");
    }

    private static String tokens(SessionTokens tokens) {
        return inThousands(tokens.total()) + " " + SECONDARY.paint("(C " + inThousands(tokens.cached()) + "/!C " + inThousands(tokens.uncached()) + ")");
    }

    private static String inThousands(long count) {
        return (count + THOUSAND / 2) / THOUSAND + "k";
    }

    private String limit(Optional<UsageLimit> limit) {
        return limit.map(this::usedUntilItsReset).orElse(ABSENT);
    }

    private String usedUntilItsReset(UsageLimit limit) {
        return shaded(limit.usedPercentage()) + limit.resetsAt().map(reset -> " " + SECONDARY.paint("(" + resetMoment(reset) + ")")).orElse("");
    }

    private String resetMoment(Instant reset) {
        return RESET_MOMENT.format(reset.atZone(zone));
    }

    private static String branch(Optional<CheckedOutBranch> branch) {
        return branch.map(StatusLineText::nameWithDirtyMark).orElse(ABSENT);
    }

    private static String nameWithDirtyMark(CheckedOutBranch branch) {
        return VALUE.paint(branch.name()) + (branch.hasUncommittedChanges() ? Shade.DARK_AMBER.paint(DIRTY_MARK) : "");
    }

    private static String cacheHitRatio(SessionTokens tokens) {
        OptionalInt ratio = tokens.cacheHitRatio();
        return ratio.isPresent() ? Shade.theOtherWay(ratio.getAsInt()).paint(ratio.getAsInt() + "%") : ABSENT;
    }
}
