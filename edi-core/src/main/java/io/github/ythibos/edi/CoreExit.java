package io.github.ythibos.edi;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class CoreExit {

    private static final long AFTER_THE_ANSWER_MILLIS = 300;

    private final ConfigurableApplicationContext context;
    private final ScheduledExecutorService exit = Executors.newSingleThreadScheduledExecutor();

    public CoreExit(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public void afterTheAnswer() {
        exit.schedule(() -> System.exit(SpringApplication.exit(context)), AFTER_THE_ANSWER_MILLIS, TimeUnit.MILLISECONDS);
    }
}
