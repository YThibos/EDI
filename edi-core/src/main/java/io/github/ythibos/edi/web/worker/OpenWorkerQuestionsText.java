package io.github.ythibos.edi.web.worker;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.worker.OpenWorkerQuestions;
import io.github.ythibos.edi.application.worker.OpenWorkerQuestions.OpenQuestion;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class OpenWorkerQuestionsText implements TextPresenter<OpenWorkerQuestions> {

    @Override
    public Class<OpenWorkerQuestions> presents() {
        return OpenWorkerQuestions.class;
    }

    @Override
    public String text(OpenWorkerQuestions open) {
        if (open.questions().isEmpty()) {
            return "No worker waits on an answer.\n";
        }
        return Words.counted("Workers waiting on an answer", open.questions().size()) + ":\n"
                + open.questions().stream().map(OpenWorkerQuestionsText::line).collect(Collectors.joining());
    }

    private static String line(OpenQuestion question) {
        return "  " + question.worker() + " (" + question.question().key() + "), " + Words.quantity((int) question.waited().toMinutes(), "minute")
                + ": " + question.question().text() + "\n";
    }
}
