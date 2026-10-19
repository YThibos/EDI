package io.github.ythibos.edi.application.migration;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class LearningsFilter {

    private static final List<String> OLD_HARNESS_MECHANICS = List.of(
            "command guard", "tasks-axi", "fork tests", "monitoring restarts itself", "cannot be quietened", "`fm-");
    private static final String UPSTREAM_PULL_REQUESTS = "`gh-axi` resolves pull requests";
    private static final String ALWAYS_NAME_THE_REPOSITORY = """
            - **`gh-axi` can resolve pull requests against an upstream repository instead of the fork it runs in (2026-10-01).**
              Always pass `--repo <owner>/<name>` when reading a repository's pull requests.""";
    private static final String TOP_LEVEL_CD = "never `cd` at the top level";
    private static final String WINDOWS_WORKING_DIRECTORY = """
            - **`cmd.exe` refuses a WSL working directory (2026-10-07).**
              Launch a Windows tool from a subshell that first changes to a directory on a Windows drive.""";

    private int dropped;

    String kept(String learnings) {
        return ListEntries.edited(learnings, this::filtered);
    }

    int dropped() {
        return dropped;
    }

    private Optional<String> filtered(String entry) {
        String title = ListEntries.titleOf(entry).toLowerCase(Locale.ROOT);
        if (namesOldHarnessMechanics(title)) {
            dropped++;
            return Optional.empty();
        }
        if (title.startsWith(UPSTREAM_PULL_REQUESTS)) {
            return Optional.of(ALWAYS_NAME_THE_REPOSITORY);
        }
        if (title.startsWith(TOP_LEVEL_CD)) {
            return Optional.of(WINDOWS_WORKING_DIRECTORY);
        }
        return Optional.of(entry);
    }

    private static boolean namesOldHarnessMechanics(String title) {
        return OLD_HARNESS_MECHANICS.stream().anyMatch(title::contains);
    }
}
