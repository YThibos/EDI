package io.github.ythibos.edi.adapter.process;

public enum Program {
    GIT("git"),
    GITLEAKS("gitleaks"),
    TMUX("tmux"),
    CLAUDE_CODE("claude"),
    GH("gh"),
    GLAB("glab"),
    NO_MISTAKES("no-mistakes"),
    SHELL("sh"),
    LAVISH("lavish-axi"),
    CMD("cmd.exe"),
    POWERSHELL("powershell.exe");

    private final String executable;

    Program(String executable) {
        this.executable = executable;
    }

    public String executable() {
        return executable;
    }
}
