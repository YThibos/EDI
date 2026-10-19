package io.github.ythibos.edi.application.migration;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ythibos.edi.application.migration.MarkdownSections.Section;

final class CaptainMemoryEdits {

    private static final String GLOBAL_RULES_POINTER = """
            The captain's project-independent style rules live in one authoritative file,\
             the one the machine profile names as `captain.globalRules`.
            Read it when a style question arises; do not copy its rules here.""";
    private static final String TRACKER_KEYED = "JIRA-keyed";
    private static final String WORK_KEYED = "keyed with the task's work key (JIRA on the laptop, EDI-native elsewhere)";
    private static final String OLD_BRANCH_PREFIX_SCRIPT = "fm-project-mode.sh";
    private static final String BASE_BRANCH_PER_PROJECT = "base branch per project";
    private static final String CRAFTSMANSHIP_REVIEW = "independent craftsmanship review";
    private static final String REVIEWER_RETIRED = "- **The independent craftsmanship reviewer is retired from all flows (2026-09-24);"
            + " the craft rules ride in the validation pipeline instead.**";
    private static final Pattern CHECKOUT_PATH = Pattern.compile("`[^`\\s]*/<repo>`");
    private static final String CHECKOUT_FROM_THE_PROFILE = "`<captain.checkoutsRoot>/<repo>` (the checkout root from the machine profile)";
    private static final Pattern OLD_REPORT_SCRIPT = Pattern.compile("`(?:bin/)?fm-captain-report\\.sh`");
    private static final String EDI_REPORT = "`edi report`";
    private static final String PARAGRAPH_BREAK = "\n\n";
    private static final Map<String, UnaryOperator<String>> EDITS = Map.of(
            "global personal preferences", CaptainMemoryEdits::pointingAtTheGlobalRules,
            "hard rules on every project commit", CaptainMemoryEdits::keyedOnTheWorkKey,
            "delivery flow", CaptainMemoryEdits::inTheCurrentDeliveryShape,
            "applying changes locally", CaptainMemoryEdits::withTheCheckoutRootFromTheProfile,
            "reporting format", CaptainMemoryEdits::pointingAtEdiReport);

    private CaptainMemoryEdits() {
    }

    static Section edited(Section section) {
        String heading = section.heading().toLowerCase(Locale.ROOT);
        return EDITS.entrySet().stream()
                .filter(edit -> heading.startsWith(edit.getKey()))
                .findFirst()
                .map(edit -> new Section(section.heading(), edit.getValue().apply(section.text())))
                .orElse(section);
    }

    private static String pointingAtTheGlobalRules(String text) {
        return headingLineOf(text) + PARAGRAPH_BREAK + GLOBAL_RULES_POINTER;
    }

    private static String keyedOnTheWorkKey(String text) {
        return String.join("\n", text.replace(TRACKER_KEYED, WORK_KEYED).lines()
                .filter(line -> !line.contains(OLD_BRANCH_PREFIX_SCRIPT))
                .toList());
    }

    private static String inTheCurrentDeliveryShape(String text) {
        return ListEntries.edited(text, CaptainMemoryEdits::inTheCurrentDeliveryShapeEntry);
    }

    private static String withTheCheckoutRootFromTheProfile(String text) {
        return CHECKOUT_PATH.matcher(text).replaceAll(Matcher.quoteReplacement(CHECKOUT_FROM_THE_PROFILE));
    }

    private static String pointingAtEdiReport(String text) {
        return OLD_REPORT_SCRIPT.matcher(text).replaceAll(EDI_REPORT);
    }

    private static String headingLineOf(String text) {
        return text.lines().findFirst().orElse("");
    }

    private static Optional<String> inTheCurrentDeliveryShapeEntry(String entry) {
        String title = ListEntries.titleOf(entry).toLowerCase(Locale.ROOT);
        if (title.startsWith(BASE_BRANCH_PER_PROJECT)) {
            return Optional.empty();
        }
        return Optional.of(title.contains(CRAFTSMANSHIP_REVIEW) ? REVIEWER_RETIRED : entry);
    }
}
