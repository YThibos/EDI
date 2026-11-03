package io.github.ythibos.edi.domain.authority;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.task.TaskId;

public record DialogAnswer(UUID session, int number, AnsweredQuestion answer, Instant sentAt) implements CaptainMessage {

    private static final String WORD_EDGE_BEFORE = "(?<![\\p{Alnum}_-])";
    private static final String WORD_EDGE_AFTER = "(?![\\p{Alnum}_-])";

    public DialogAnswer {
        Objects.requireNonNull(session, "A dialog answer is given in a primary session.");
        Objects.requireNonNull(answer, "A dialog answer holds the captain's choice.");
        Objects.requireNonNull(sentAt, "A dialog answer records when it was given.");
    }

    @Override
    public String wholeText() {
        return String.join("\n", lines().stream().map(MessageLine::text).toList());
    }

    @Override
    public List<MessageLine> lines() {
        List<String> said = Stream.concat(answer.chosen().strip().lines(), answer.notes().lines()).toList();
        return IntStream.range(0, said.size()).mapToObj(index -> new MessageLine(index + 1, said.get(index), false)).toList();
    }

    public boolean authorises(CaptainWordAct act, TaskId task, Set<TaskId> knownTasks) {
        return choiceOpensWith(act) && choiceNames(task.value()) && namesNoOtherTask(task, knownTasks);
    }

    public boolean authorisesOnTheHome(CaptainWordAct act) {
        return choiceOpensWith(act);
    }

    private boolean choiceOpensWith(CaptainWordAct act) {
        return wordPattern("^\\s*", act.word()).matcher(answer.chosen()).find();
    }

    private boolean choiceNames(String word) {
        return wordPattern("", word).matcher(answer.chosen()).find();
    }

    private boolean namesNoOtherTask(TaskId task, Set<TaskId> knownTasks) {
        return knownTasks.stream().filter(other -> !other.equals(task)).noneMatch(other -> choiceNames(other.value()));
    }

    private static Pattern wordPattern(String anchor, String word) {
        return Pattern.compile(anchor + WORD_EDGE_BEFORE + Pattern.quote(word) + WORD_EDGE_AFTER, Pattern.CASE_INSENSITIVE);
    }
}
