package io.github.ythibos.edi.adapter.fork;

public record ForkDependency(String type, String id, String reason) {

    public static final String BLOCKED_BY = "blocked-by";

    public boolean blocks() {
        return type.equals(BLOCKED_BY);
    }
}
