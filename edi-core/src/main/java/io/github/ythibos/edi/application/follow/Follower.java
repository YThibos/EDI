package io.github.ythibos.edi.application.follow;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.github.ythibos.edi.adapter.store.LoggedEvent;

public record Follower(String name, Consumer<LoggedEvent> follows, ReentrantLock turn) {

    public Follower {
        Objects.requireNonNull(name, "A follower has the name its cursor is kept under.");
        Objects.requireNonNull(follows, () -> "The follower " + name + " follows each event.");
        Objects.requireNonNull(turn, () -> "The follower " + name + " follows the log one run at a time.");
    }

    public static Follower named(String name, Consumer<LoggedEvent> follows) {
        return new Follower(name, follows, new ReentrantLock());
    }
}
