package io.github.ythibos.edi.architecture;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

public final class NameAMergeOperation extends ArchCondition<JavaClass> {

    private static final List<String> AUTO_MERGE = List.of("auto", "merge");
    private static final List<String> MERGE_WHEN_PIPELINE_SUCCEEDS = List.of("merge", "when", "pipeline", "succeeds");
    private static final String MERGE = "merge";
    private static final Set<String> VERBS_THAT_PERFORM_A_MERGE =
            Set.of("accept", "do", "execute", "force", "perform", "rebase", "run", "squash", "start", "trigger");

    public NameAMergeOperation() {
        super("name a merge operation");
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents events) {
        Stream.concat(Stream.of(javaClass.getSimpleName()), javaClass.getMethods().stream().map(JavaMethod::getName))
                .filter(NameAMergeOperation::namesAMergeOperation)
                .forEach(name -> events.add(SimpleConditionEvent.satisfied(javaClass,
                        javaClass.getName() + " names a merge operation: " + name)));
    }

    public static boolean namesAMergeOperation(String name) {
        List<String> words = wordsOf(name);
        return startsWithTheVerbMerge(words)
                || endsWithTheVerbMerge(words)
                || followsAVerbThatPerformsAMerge(words)
                || words.contains("automerge")
                || Collections.indexOfSubList(words, AUTO_MERGE) >= 0
                || Collections.indexOfSubList(words, MERGE_WHEN_PIPELINE_SUCCEEDS) >= 0;
    }

    private static List<String> wordsOf(String name) {
        return Arrays.stream(name.split("(?<=[a-z0-9])(?=[A-Z])|_|-"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static boolean startsWithTheVerbMerge(List<String> words) {
        boolean namesAMergeRequest = words.size() > 1 && words.get(1).startsWith("request");
        return !words.isEmpty() && words.getFirst().equals(MERGE) && !namesAMergeRequest;
    }

    private static boolean endsWithTheVerbMerge(List<String> words) {
        return !words.isEmpty() && words.getLast().equals(MERGE);
    }

    private static boolean followsAVerbThatPerformsAMerge(List<String> words) {
        return IntStream.range(1, words.size())
                .anyMatch(index -> words.get(index).equals(MERGE) && VERBS_THAT_PERFORM_A_MERGE.contains(words.get(index - 1)));
    }
}
