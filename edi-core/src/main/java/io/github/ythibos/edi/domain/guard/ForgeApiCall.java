package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

record ForgeApiCall(String method, List<String> fields, List<String> endpoints) {

    private static final Set<String> READING_METHODS = Set.of("GET", "HEAD");
    private static final List<Pattern> MERGE_ENDPOINTS = List.of(
            Pattern.compile("(^|/)pulls/[^/]+/merge$"),
            Pattern.compile("(^|/)repos/[^/]+/[^/]+/merges$"),
            Pattern.compile("(^|/)merge_requests/[^/]+/merge$"));
    private static final Pattern GRAPHQL_ENDPOINT = Pattern.compile("(^|/)graphql$");
    private static final Pattern GRAPHQL_STRINGS = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern MUTATION = Pattern.compile("(?<![\\w$])mutation(?!\\w)");
    private static final Pattern MERGING_MUTATION = Pattern.compile(
            "(?<![\\w$])(mergePullRequest|enablePullRequestAutoMerge|enqueuePullRequest|mergeBranch|mergeRequestAccept)(?!\\w)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MERGE_ARMING_SETTING = Pattern.compile("(^|[?&])(merge_when_pipeline_succeeds|auto_merge)(=|&|$)");
    private static final Pattern QUERY_OR_FRAGMENT = Pattern.compile("[?#].*$");
    private static final Pattern REPEATED_SLASHES = Pattern.compile("/{2,}");
    private static final Pattern TRAILING_SLASHES = Pattern.compile("/+$");

    static ForgeApiCall of(List<String> arguments) {
        return new Reading(arguments).call();
    }

    Optional<String> mergingAct() {
        if (isAMergingMutation()) {
            return Optional.of("a forge GraphQL mutation that merges or arms a merge");
        }
        if (writes() && endpoints.stream().anyMatch(ForgeApiCall::isAMergeEndpoint)) {
            return Optional.of("a forge API " + method + " to a merge endpoint");
        }
        if (writes() && armsAMerge()) {
            return Optional.of("a forge API " + method + " that arms a merge");
        }
        return Optional.empty();
    }

    private boolean isAMergingMutation() {
        return endpoints.stream().map(ForgeApiCall::pathOf).anyMatch(path -> GRAPHQL_ENDPOINT.matcher(path).find())
                && fields.stream().map(ForgeApiCall::withoutStrings).anyMatch(ForgeApiCall::namesAMergingMutation);
    }

    private static String pathOf(String endpoint) {
        String path = QUERY_OR_FRAGMENT.matcher(endpoint.toLowerCase(Locale.ROOT)).replaceFirst("");
        return TRAILING_SLASHES.matcher(REPEATED_SLASHES.matcher(path).replaceAll("/")).replaceFirst("");
    }

    private static String withoutStrings(String document) {
        return GRAPHQL_STRINGS.matcher(document).replaceAll(" ");
    }

    private static boolean namesAMergingMutation(String document) {
        return MUTATION.matcher(document).find() && MERGING_MUTATION.matcher(document).find();
    }

    private boolean writes() {
        return !READING_METHODS.contains(method);
    }

    private static boolean isAMergeEndpoint(String endpoint) {
        String path = pathOf(endpoint);
        return MERGE_ENDPOINTS.stream().anyMatch(merge -> merge.matcher(path).find());
    }

    private boolean armsAMerge() {
        return fields.stream().anyMatch(ForgeApiCall::armsAMerge) || endpoints.stream().map(ForgeApiCall::queryOf).anyMatch(ForgeApiCall::armsAMerge);
    }

    private static boolean armsAMerge(String settings) {
        return MERGE_ARMING_SETTING.matcher(settings.toLowerCase(Locale.ROOT)).find();
    }

    private static String queryOf(String endpoint) {
        int query = endpoint.indexOf('?');
        return query < 0 ? "" : endpoint.substring(query);
    }

    private static final class Reading {

        private static final String OPTIONS_END = "--";
        private static final String LONG_OPTION = "--";
        private static final String SHORT_OPTION = "-";
        private static final String JOINED_VALUE = "=";
        private static final Map<String, Option> LONG_OPTIONS = Map.of(
                "--method", Option.METHOD, "--field", Option.FIELD, "--raw-field", Option.FIELD, "--input", Option.BODY_FILE);
        private static final Map<Character, Option> SHORT_OPTIONS = Map.of(
                'X', Option.METHOD, 'f', Option.FIELD, 'F', Option.FIELD, 'H', Option.NOT_JUDGED, 'p', Option.NOT_JUDGED, 'q', Option.NOT_JUDGED, 't', Option.NOT_JUDGED);
        private static final String READ_WITHOUT_A_BODY = "GET";
        private static final String WRITE_WITH_A_BODY = "POST";

        private final List<String> fields = new ArrayList<>();
        private final List<String> endpoints = new ArrayList<>();
        private String method;
        private boolean body;

        Reading(List<String> arguments) {
            int index = 0;
            while (index < arguments.size()) {
                index = readAt(arguments, index);
            }
        }

        ForgeApiCall call() {
            return new ForgeApiCall(method != null ? method.toUpperCase(Locale.ROOT) : impliedMethod(), fields, endpoints);
        }

        private int readAt(List<String> arguments, int index) {
            String argument = arguments.get(index);
            if (argument.equals(OPTIONS_END)) {
                endpoints.addAll(arguments.subList(index + 1, arguments.size()));
                return arguments.size();
            }
            if (argument.startsWith(LONG_OPTION)) {
                return readLongOption(arguments, index);
            }
            if (argument.startsWith(SHORT_OPTION) && argument.length() > 1) {
                return readShortOptions(arguments, index);
            }
            endpoints.add(argument);
            return index + 1;
        }

        private int readLongOption(List<String> arguments, int index) {
            String argument = arguments.get(index);
            int joined = argument.indexOf(JOINED_VALUE);
            Option option = LONG_OPTIONS.get(joined < 0 ? argument : argument.substring(0, joined));
            if (option == null) {
                return index + 1;
            }
            if (joined >= 0) {
                take(option, argument.substring(joined + 1));
                return index + 1;
            }
            take(option, valueAt(arguments, index + 1));
            return index + 2;
        }

        private int readShortOptions(List<String> arguments, int index) {
            String cluster = arguments.get(index);
            for (int letter = 1; letter < cluster.length(); letter++) {
                Option option = SHORT_OPTIONS.get(cluster.charAt(letter));
                if (option != null) {
                    return readShortValue(arguments, index, option, cluster.substring(letter + 1));
                }
            }
            return index + 1;
        }

        private int readShortValue(List<String> arguments, int index, Option option, String rest) {
            if (option == Option.NOT_JUDGED) {
                return index + 1;
            }
            if (rest.isEmpty()) {
                take(option, valueAt(arguments, index + 1));
                return index + 2;
            }
            take(option, rest.startsWith(JOINED_VALUE) ? rest.substring(JOINED_VALUE.length()) : rest);
            return index + 1;
        }

        private void take(Option option, String value) {
            switch (option) {
                case METHOD -> method = value;
                case FIELD -> fields.add(value);
                case BODY_FILE -> body = true;
            }
        }

        private static String valueAt(List<String> arguments, int index) {
            return index < arguments.size() ? arguments.get(index) : "";
        }

        private String impliedMethod() {
            return body || !fields.isEmpty() ? WRITE_WITH_A_BODY : READ_WITHOUT_A_BODY;
        }

        private enum Option {
            METHOD,
            FIELD,
            BODY_FILE,
            NOT_JUDGED
        }
    }
}
