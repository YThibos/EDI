package io.github.ythibos.edi.web.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.memory.MemoryCheck;
import io.github.ythibos.edi.application.memory.MemoryCheck.FileEstimate;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
public class MemoryCheckText implements TextPresenter<MemoryCheck> {

    @Override
    public Class<MemoryCheck> presents() {
        return MemoryCheck.class;
    }

    @Override
    public String text(MemoryCheck check) {
        List<String> lines = new ArrayList<>(List.of("Startup memory: " + check.estimatedTokens() + " of " + check.budgetTokens()
                + " tokens, estimated as characters divided by four."));
        check.files().forEach(file -> lines.add("  " + fileLine(file)));
        lines.add(budgetVerdict(check));
        check.duplicatedHeadings().forEach(heading -> lines.add("The rule heading \"" + heading + "\" appears more than once; keep one."));
        lines.add(check.passes() ? "The memory check passes." : "The memory check fails: run /stow to curate the memory.");
        return String.join("\n", lines) + "\n";
    }

    private static String fileLine(FileEstimate file) {
        return file.name() + "  " + file.estimatedTokens() + " tokens  " + file.path();
    }

    private static String budgetVerdict(MemoryCheck check) {
        return check.fitsItsBudget()
                ? "It fits the budget, with " + (check.budgetTokens() - check.estimatedTokens()) + " tokens to spare."
                : "It is " + (check.estimatedTokens() - check.budgetTokens()) + " tokens over the budget.";
    }
}
