package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record InPlaceEditor(String lettersTakingTheRest, Map<Character, Pattern> lettersTakingAnOptionalValue) {

    private static final Map<String, InPlaceEditor> BY_PROGRAM = Map.of(
            "sed", new InPlaceEditor("efl", Map.of()),
            "perl", new InPlaceEditor("eEMmIxCDF", Map.of(
                    '0', Pattern.compile("[xX][0-9a-fA-F]*|[0-7]*"),
                    'l', Pattern.compile("[0-7]*"),
                    'd', Pattern.compile("t?(:.*)?"))));
    private static final String IN_PLACE_OPTION = "--in-place";
    private static final String LONG_OPTION = "--";
    private static final String SHORT_OPTION = "-";
    private static final char IN_PLACE = 'i';

    static Optional<InPlaceEditor> named(String program) {
        return Optional.ofNullable(BY_PROGRAM.get(program));
    }

    boolean editsInPlace(List<String> arguments) {
        return arguments.stream().anyMatch(argument -> argument.startsWith(IN_PLACE_OPTION)
                || argument.startsWith(SHORT_OPTION) && !argument.startsWith(LONG_OPTION) && namesInPlace(argument));
    }

    private boolean namesInPlace(String cluster) {
        int letter = 1;
        while (letter < cluster.length()) {
            char option = cluster.charAt(letter);
            if (option == IN_PLACE) {
                return true;
            }
            if (lettersTakingTheRest.indexOf(option) >= 0) {
                return false;
            }
            letter += 1 + optionalValueLength(option, cluster.substring(letter + 1));
        }
        return false;
    }

    private int optionalValueLength(char option, String rest) {
        Pattern value = lettersTakingAnOptionalValue.get(option);
        if (value == null) {
            return 0;
        }
        Matcher taken = value.matcher(rest);
        return taken.lookingAt() ? taken.end() : 0;
    }
}
