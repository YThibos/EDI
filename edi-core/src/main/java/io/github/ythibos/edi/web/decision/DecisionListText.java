package io.github.ythibos.edi.web.decision;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.decision.DecisionList;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
public class DecisionListText implements TextPresenter<DecisionList> {

    @Override
    public Class<DecisionList> presents() {
        return DecisionList.class;
    }

    @Override
    public String text(DecisionList list) {
        List<String> lines = new ArrayList<>();
        lines.add(Words.counted(list.openOnly() ? "Open decisions" : "Decisions", list.decisions().size()));
        list.decisions().forEach(decision -> lines.addAll(DecisionLines.of(decision)));
        return lines.stream().collect(Collectors.joining("\n", "", "\n"));
    }
}
