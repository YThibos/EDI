package io.github.ythibos.edi.adapter.process;

public enum Program {
    GIT("git"),
    GITLEAKS("gitleaks"),
    TMUX("tmux"),
    CLAUDE_CODE("claude"),
    SHELL("sh");

    private final String executable;

    Program(String executable) {
        this.executable = executable;
    }

    public String executable() {
        return executable;
    }
}
