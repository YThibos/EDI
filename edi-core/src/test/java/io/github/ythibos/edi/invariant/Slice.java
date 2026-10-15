package io.github.ythibos.edi.invariant;

enum Slice {
    S1("Domain and store"),
    S2("Reports"),
    S3("Workers"),
    S4("Supervision and recovery"),
    S5("Delivery"),
    S6("Tracker and time"),
    S7("Console and boards"),
    S8("Claude layer, memory and presence");

    private final String title;

    Slice(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return name() + " " + title;
    }
}
