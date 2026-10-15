package io.github.ythibos.edi.domain.task;

public class UnknownTask extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownTask(TaskId id) {
        super("No task is called " + id + ".");
    }
}
