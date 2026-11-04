package io.github.ythibos.edi.application.fleet;

import java.util.List;

public record TaskLinks(String key, String tracker, Request request, Branch branch, String board, String report, String brief, List<String> decisions) {

    public static final TaskLinks NONE = new TaskLinks(null, null, null, null, null, null, null, List.of());

    public TaskLinks {
        decisions = List.copyOf(decisions);
    }

    public record Request(String url, int number, String state, String step) {
    }

    public record Branch(String name, String url) {
    }
}
