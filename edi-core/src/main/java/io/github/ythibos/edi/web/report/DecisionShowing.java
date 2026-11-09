package io.github.ythibos.edi.web.report;

sealed interface DecisionShowing {

    DecisionShowing IN_FULL = new InFull();
    DecisionShowing AS_A_REMINDER = new AsAReminder();

    record InFull() implements DecisionShowing {
    }

    record AsAReminder() implements DecisionShowing {
    }

    record InTheRound(String closesAt) implements DecisionShowing {
    }
}
