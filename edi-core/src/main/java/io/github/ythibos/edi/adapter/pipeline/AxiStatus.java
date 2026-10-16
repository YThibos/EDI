package io.github.ythibos.edi.adapter.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.delivery.PipelineRun;

final class AxiStatus {

    private static final String RUN_BLOCK = "run:";
    private static final String FIELD_INDENT = "  ";
    private static final String ROW_INDENT = "    ";
    private static final String ACTIVE_STEPS = "active_steps[";
    private static final String FIELD_SEPARATOR = ": ";
    private static final String CELL_SEPARATOR = ",";
    private static final char QUOTE = '"';
    private static final char ESCAPE = '\\';

    private AxiStatus() {
    }

    static Optional<PipelineRun> currentRunIn(String status, String branch) {
        List<String> block = runBlockOf(status.lines().toList());
        Map<String, String> fields = fieldsOf(block);
        if (!branch.equals(fields.get("branch")) || !fields.containsKey("id") || !fields.containsKey("status")) {
            return Optional.empty();
        }
        return Optional.of(new PipelineRun(fields.get("id"), fields.get("status"), activeStepIn(block), fields.get("head_sha"), fields.get("pr")));
    }

    private static List<String> runBlockOf(List<String> lines) {
        int start = lines.indexOf(RUN_BLOCK);
        return start < 0 ? List.of() : lines.stream().skip(start + 1L).takeWhile(line -> line.startsWith(FIELD_INDENT)).toList();
    }

    private static Map<String, String> fieldsOf(List<String> block) {
        return block.stream()
                .filter(line -> !line.startsWith(ROW_INDENT))
                .map(line -> fieldOf(line.strip()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first));
    }

    private static String activeStepIn(List<String> block) {
        return block.stream()
                .dropWhile(line -> !line.strip().startsWith(ACTIVE_STEPS))
                .skip(1)
                .takeWhile(line -> line.startsWith(ROW_INDENT))
                .findFirst()
                .map(row -> firstCellOf(row.strip()))
                .orElse(null);
    }

    private static Optional<Map.Entry<String, String>> fieldOf(String line) {
        int separator = line.indexOf(FIELD_SEPARATOR);
        return separator < 1
                ? Optional.empty()
                : Optional.of(Map.entry(line.substring(0, separator), unquoted(line.substring(separator + FIELD_SEPARATOR.length()))));
    }

    private static String firstCellOf(String row) {
        return unquoted(row.charAt(0) == QUOTE ? row.substring(0, closingQuoteOf(row) + 1) : row.split(CELL_SEPARATOR, 2)[0]);
    }

    private static String unquoted(String value) {
        if (value.length() < 2 || value.charAt(0) != QUOTE || value.charAt(value.length() - 1) != QUOTE) {
            return value;
        }
        StringBuilder text = new StringBuilder();
        for (int index = 1; index < value.length() - 1; index++) {
            boolean escaped = value.charAt(index) == ESCAPE && index + 1 < value.length() - 1;
            text.append(value.charAt(escaped ? ++index : index));
        }
        return text.toString();
    }

    private static int closingQuoteOf(String row) {
        for (int index = 1; index < row.length(); index++) {
            if (row.charAt(index) == ESCAPE) {
                index++;
            } else if (row.charAt(index) == QUOTE) {
                return index;
            }
        }
        return row.length() - 1;
    }
}
