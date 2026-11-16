package io.github.ythibos.edi.web.report;

public record ReportWidth(int columns) {

    private static final int STANDARD_COLUMNS = 120;
    private static final int FEWEST_COLUMNS = 60;
    private static final int CLAUDE_CODE_INDENT = 2;

    public ReportWidth {
        if (columns < FEWEST_COLUMNS) {
            throw new IllegalArgumentException("The captain report is at least " + FEWEST_COLUMNS + " columns wide, not " + columns + ".");
        }
    }

    public static ReportWidth standard() {
        return new ReportWidth(STANDARD_COLUMNS);
    }

    public static ReportWidth forPaneIfKnown(Integer paneWidth) {
        return paneWidth == null ? standard() : forPane(paneWidth);
    }

    public static ReportWidth forPane(int paneWidth) {
        return new ReportWidth(Math.clamp((long) paneWidth - CLAUDE_CODE_INDENT, FEWEST_COLUMNS, STANDARD_COLUMNS));
    }
}
