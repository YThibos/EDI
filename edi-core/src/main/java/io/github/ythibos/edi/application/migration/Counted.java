package io.github.ythibos.edi.application.migration;

final class Counted {

    private Counted() {
    }

    static String sections(int count) {
        return things(count, "section");
    }

    static String projects(int count) {
        return things(count, "project");
    }

    static String terms(int count) {
        return things(count, "term");
    }

    static String things(int count, String thing) {
        return count + " " + thing + (count == 1 ? "" : "s");
    }
}
