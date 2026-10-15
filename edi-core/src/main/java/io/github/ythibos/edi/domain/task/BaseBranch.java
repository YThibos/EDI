package io.github.ythibos.edi.domain.task;

public record BaseBranch(String name) {

    private static final String OPTION_MARK = "-";
    private static final String PARENT_STEP = "..";

    public BaseBranch {
        if (!isBranchName(name)) {
            throw new IllegalArgumentException("A base branch is a git branch name such as main: " + name);
        }
    }

    private static boolean isBranchName(String name) {
        return name != null
                && !name.isEmpty()
                && !name.startsWith(OPTION_MARK)
                && !name.contains(PARENT_STEP)
                && name.chars().noneMatch(character -> Character.isWhitespace(character) || Character.isISOControl(character));
    }

    @Override
    public String toString() {
        return name;
    }
}
