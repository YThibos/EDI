package io.github.ythibos.edi.adapter.forge;

import java.util.List;

import io.github.ythibos.edi.adapter.process.Program;

public enum ForgeCommand {
    GH_AUTH_TOKEN(Program.GH, "auth", "token"),
    GH_AUTH_STATUS(Program.GH, "auth", "status"),
    GH_AUTH_GIT_CREDENTIAL(Program.GH, "auth", "git-credential"),
    GH_PR_LIST(Program.GH, "pr", "list"),
    GH_PR_VIEW(Program.GH, "pr", "view"),
    GH_PR_CREATE(Program.GH, "pr", "create"),
    GLAB_API_USER(Program.GLAB, "api", "user"),
    GLAB_MR_LIST(Program.GLAB, "mr", "list"),
    GLAB_MR_VIEW(Program.GLAB, "mr", "view"),
    GLAB_MR_CREATE(Program.GLAB, "mr", "create");

    private final Program program;
    private final List<String> words;

    ForgeCommand(Program program, String... words) {
        this.program = program;
        this.words = List.of(words);
    }

    public Program program() {
        return program;
    }

    public List<String> words() {
        return words;
    }
}
