package io.github.ythibos.edi.domain.guard;

@FunctionalInterface
public interface SocketPaths {

    SocketPaths AS_WRITTEN = path -> path;

    String realPathOf(String socketPath);
}
