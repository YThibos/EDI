package io.github.ythibos.edi.adapter.git;

import java.util.List;

import io.github.ythibos.edi.domain.branch.CheckedOutBranch;

final class PorcelainStatus {

    private static final String HEADER = "# ";
    private static final String HEAD = "# branch.head ";
    private static final String COMMIT = "# branch.oid ";
    private static final String DETACHED = "(detached)";
    private static final int SHORT_COMMIT = 7;

    private PorcelainStatus() {
    }

    static CheckedOutBranch checkedOutBranchIn(String status) {
        List<String> lines = status.lines().toList();
        return new CheckedOutBranch(branchNameIn(lines), hasEntries(lines));
    }

    private static String branchNameIn(List<String> lines) {
        String head = headerValue(lines, HEAD);
        return head.equals(DETACHED) ? shortCommit(headerValue(lines, COMMIT)) : head;
    }

    private static String headerValue(List<String> lines, String header) {
        return lines.stream().filter(line -> line.startsWith(header)).map(line -> line.substring(header.length())).findFirst().orElse("");
    }

    private static String shortCommit(String commit) {
        return commit.substring(0, Math.min(SHORT_COMMIT, commit.length()));
    }

    private static boolean hasEntries(List<String> lines) {
        return lines.stream().anyMatch(line -> !line.startsWith(HEADER));
    }
}
