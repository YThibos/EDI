package io.github.ythibos.edi.web.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.memory.StowFinding;
import io.github.ythibos.edi.domain.memory.StowList;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class StowListText implements TextPresenter<StowList> {

    @Override
    public Class<StowList> presents() {
        return StowList.class;
    }

    @Override
    public String text(StowList list) {
        List<String> lines = new ArrayList<>(List.of("Startup memory: " + list.estimatedTokens() + " of " + list.budgetTokens() + " tokens"
                + (list.tokensOverBudget() > 0 ? ", " + list.tokensOverBudget() + " over the budget." : ".")));
        lines.add(Words.counted("To curate", list.findings().size()));
        list.findings().forEach(finding -> lines.add("  " + findingLine(finding)));
        return String.join("\n", lines) + "\n";
    }

    private static String findingLine(StowFinding finding) {
        String where = finding.file().isEmpty() ? "" : finding.file() + ": ";
        return where + finding.entry() + " - " + finding.why();
    }
}
