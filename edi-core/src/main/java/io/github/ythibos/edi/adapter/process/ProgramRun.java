package io.github.ythibos.edi.adapter.process;

public record ProgramRun(Program program, int exitCode, String output, String errors) {

    private static final int SUCCESS = 0;

    public boolean succeeded() {
        return exitCode == SUCCESS;
    }

    public String outputOfSuccess() {
        if (!succeeded()) {
            throw new ProgramFailure(program.executable() + " exited with " + exitCode + ": " + errors.strip());
        }
        return output;
    }
}
