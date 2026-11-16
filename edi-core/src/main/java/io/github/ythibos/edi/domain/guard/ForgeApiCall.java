package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final Pattern GRAPHQL_STRINGS_AND_COMMENTS = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|#[^\\n]*");
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
                && fields.stream().map(ForgeApiCall::withoutStringsAndComments).anyMatch(ForgeApiCall::namesAMergingMutation);
    }

    private static String pathOf(String endpoint) {
        String path = QUERY_OR_FRAGMENT.matcher(endpoint.toLowerCase(Locale.ROOT)).replaceFirst("");
        return TRAILING_SLASHES.matcher(REPEATED_SLASHES.matcher(path).replaceAll("/")).replaceFirst("");
    }

    private static String withoutStringsAndComments(String document) {
        return GRAPHQL_STRINGS_AND_COMMENTS.matcher(document).replaceAll(" ");
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

        private static final Set<String> METHOD_OPTIONS = Set.of("-X", "--method");
        private static final List<String> METHOD_OPTIONS_GIVEN = List.of("--method=", "-X");
        private static final Set<String> FIELD_OPTIONS = Set.of("-f", "-F", "--field", "--raw-field");
        private static final List<String> FIELD_OPTIONS_GIVEN = List.of("--field=", "--raw-field=", "-f", "-F");
        private static final String BODY_FILE_OPTION = "--input";
        private static final String BODY_FILE_OPTION_GIVEN = "--input=";
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
            String next = index + 1 < arguments.size() ? arguments.get(index + 1) : "";
            if (METHOD_OPTIONS.contains(argument)) {
                method = next;
                return index + 2;
            }
            if (FIELD_OPTIONS.contains(argument)) {
                fields.add(next);
                return index + 2;
            }
            if (argument.equals(BODY_FILE_OPTION)) {
                body = true;
                return index + 2;
            }
            readJoined(argument);
            return index + 1;
        }

        private void readJoined(String argument) {
            Optional<String> method = valueAfter(argument, METHOD_OPTIONS_GIVEN);
            Optional<String> field = valueAfter(argument, FIELD_OPTIONS_GIVEN);
            if (method.isPresent()) {
                this.method = method.get();
            } else if (field.isPresent()) {
                fields.add(field.get());
            } else if (argument.startsWith(BODY_FILE_OPTION_GIVEN)) {
                body = true;
            } else {
                endpoints.add(argument);
            }
        }

        private static Optional<String> valueAfter(String argument, List<String> options) {
            return options.stream().filter(argument::startsWith).findFirst().map(option -> argument.substring(option.length()));
        }

        private String impliedMethod() {
            return body || !fields.isEmpty() ? WRITE_WITH_A_BODY : READ_WITHOUT_A_BODY;
        }
    }
}
