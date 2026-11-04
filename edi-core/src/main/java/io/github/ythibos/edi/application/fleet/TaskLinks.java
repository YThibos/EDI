package io.github.ythibos.edi.application.fleet;

import java.util.List;

public record TaskLinks(String key, String tracker, Request request, Branch branch, String board, String report, List<String> decisions) {

    public TaskLinks {
        decisions = List.copyOf(decisions);
    }

    public record Request(String url, int number, String state, String step) {
    }

    public record Branch(String name, String url) {
    }
}
