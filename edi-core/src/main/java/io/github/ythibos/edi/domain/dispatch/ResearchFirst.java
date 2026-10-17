package io.github.ythibos.edi.domain.dispatch;

public enum ResearchFirst {
    NOT_APPLICABLE,
    QUEUES_RESEARCH,
    SWITCHED_OFF;

    public boolean queuesResearch() {
        return this == QUEUES_RESEARCH;
    }

    public boolean countsInThePilot() {
        return this != NOT_APPLICABLE;
    }
}
