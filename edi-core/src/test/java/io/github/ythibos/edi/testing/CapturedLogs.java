package io.github.ythibos.edi.testing;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public final class CapturedLogs implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final Level levelFound = root.getLevel();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CapturedLogs() {
        root.setLevel(Level.DEBUG);
        appender.start();
        root.addAppender(appender);
    }

    public static CapturedLogs ofEveryLogger() {
        return new CapturedLogs();
    }

    public List<String> lines() {
        return List.copyOf(appender.list).stream()
                .flatMap(event -> Stream.of(event.getFormattedMessage(), event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage()))
                .toList();
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
        root.setLevel(levelFound);
    }
}
