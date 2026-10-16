package io.github.ythibos.edi.web.text;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Table {

    private static final String GAP = "  ";

    private final String rowIndent;
    private final List<Line> lines = new ArrayList<>();

    public Table(String rowIndent) {
        this.rowIndent = rowIndent;
    }

    public Table row(String... cells) {
        lines.add(new Row(List.of(cells)));
        return this;
    }

    public Table line(String text) {
        lines.add(new Verbatim(text));
        return this;
    }

    public String text() {
        List<Integer> widths = columnWidths();
        return lines.stream().map(line -> rendered(line, widths)).collect(Collectors.joining("\n", "", "\n"));
    }

    private List<Integer> columnWidths() {
        int columns = rows().mapToInt(row -> row.cells().size()).max().orElse(0);
        return IntStream.range(0, columns).mapToObj(this::widthOf).toList();
    }

    private String rendered(Line line, List<Integer> widths) {
        return switch (line) {
            case Row row -> rowIndent + aligned(row.cells(), widths);
            case Verbatim verbatim -> verbatim.text();
        };
    }

    private Stream<Row> rows() {
        return lines.stream().filter(Row.class::isInstance).map(Row.class::cast);
    }

    private int widthOf(int column) {
        return rows()
                .filter(row -> row.cells().size() > column)
                .mapToInt(row -> row.cells().get(column).length())
                .max()
                .orElse(0);
    }

    private static String aligned(List<String> cells, List<Integer> widths) {
        return IntStream.range(0, cells.size())
                .mapToObj(column -> column == cells.size() - 1 ? cells.get(column) : padded(cells.get(column), widths.get(column)))
                .collect(Collectors.joining(GAP))
                .stripTrailing();
    }

    private static String padded(String cell, int width) {
        return cell + " ".repeat(width - cell.length());
    }

    private sealed interface Line {
    }

    private record Row(List<String> cells) implements Line {
    }

    private record Verbatim(String text) implements Line {
    }
}
