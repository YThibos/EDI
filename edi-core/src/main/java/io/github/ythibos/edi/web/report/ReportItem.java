package io.github.ythibos.edi.web.report;

record ReportItem(ProjectSet projects, String text) {

    ReportItem {
        text = Glyphs.safe(text);
    }
}
