package io.github.ythibos.edi.web.memory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.memory.MemoryCheck;
import io.github.ythibos.edi.application.memory.MemoryQuery;
import io.github.ythibos.edi.domain.memory.StowList;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleOutcome;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.CallerFirings;

@RestController
class MemoryController {

    private final MemoryQuery memory;
    private final CallerFirings firings;

    MemoryController(MemoryQuery memory, CallerFirings firings) {
        this.memory = memory;
        this.firings = firings;
    }

    @GetMapping("/api/memory/check")
    ResponseEntity<MemoryCheck> check(Caller caller) {
        MemoryCheck check = memory.check();
        if (!check.passes()) {
            firings.fired(Rule.MEMORY_BUDGET, RuleOutcome.REFUSED, caller, overBudget(check));
        }
        return ResponseEntity.status(check.passes() ? HttpStatus.OK : HttpStatus.CONFLICT).body(check);
    }

    @GetMapping("/api/memory/stow-list")
    StowList stowList() {
        return memory.stowList();
    }

    private static String overBudget(MemoryCheck check) {
        return "Startup memory holds about " + check.estimatedTokens() + " of its " + check.budgetTokens() + " tokens, with "
                + check.duplicatedHeadings().size() + " heading(s) twice.";
    }
}
