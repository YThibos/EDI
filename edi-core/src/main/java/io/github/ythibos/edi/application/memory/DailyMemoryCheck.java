package io.github.ythibos.edi.application.memory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.MemoryCheckStore;
import io.github.ythibos.edi.adapter.store.MemoryCheckStore.CheckedMemory;
import io.github.ythibos.edi.adapter.windows.WindowsToasts;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.rule.FiredRules;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.memory.StartupMemory;
import io.github.ythibos.edi.domain.presence.CaptainNotice;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleFired;

@Component
public class DailyMemoryCheck {

    private final MemoryQuery memory;
    private final MemoryCheckStore checks;
    private final WindowsToasts toasts;
    private final FiredRules firedRules;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DailyMemoryCheck(MemoryQuery memory, MemoryCheckStore checks, WindowsToasts toasts, FiredRules firedRules, Clock clock,
            TransactionTemplate transaction) {
        this.memory = memory;
        this.checks = checks;
        this.toasts = toasts;
        this.firedRules = firedRules;
        this.clock = clock;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${edi.memory.check-every:P1D}", initialDelayString = "${edi.memory.first-check:PT1M}")
    public void check() {
        StartupMemory startup = memory.startupMemory();
        CheckedMemory checked = new CheckedMemory(clock.now(), startup.estimatedTokens(), startup.budgetTokens());
        if (checked.overBudget()) {
            firedRules.record(RuleFired.warned(Rule.MEMORY_BUDGET, Actor.CORE, null, overBudget(checked)));
        }
        if (recordedAsNewlyOverBudget(checked)) {
            toasts.show(new CaptainNotice("Startup memory is over its budget",
                    checked.tokens() + " of " + checked.budget() + " tokens; /stow curates it before the next session."));
        }
    }

    private static String overBudget(CheckedMemory checked) {
        return "Startup memory holds about " + checked.tokens() + " of its " + checked.budget() + " tokens.";
    }

    private boolean recordedAsNewlyOverBudget(CheckedMemory checked) {
        return transaction.execute(_ -> {
            boolean wasOver = checks.latest().map(CheckedMemory::overBudget).orElse(false);
            checks.record(checked);
            return checked.overBudget() && !wasOver;
        });
    }
}
