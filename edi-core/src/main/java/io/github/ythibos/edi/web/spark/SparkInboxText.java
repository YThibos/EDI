package io.github.ythibos.edi.web.spark;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.spark.Focus;
import io.github.ythibos.edi.domain.spark.Spark;
import io.github.ythibos.edi.domain.spark.SparkInbox;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class SparkInboxText implements TextPresenter<SparkInbox> {

    private static final String CONTINUATION = "      ";

    private final Moments moments;

    SparkInboxText(Moments moments) {
        this.moments = moments;
    }

    @Override
    public Class<SparkInbox> presents() {
        return SparkInbox.class;
    }

    @Override
    public String text(SparkInbox inbox) {
        Table table = new Table("  ").line(Words.counted("Open sparks", inbox.open().size()));
        inbox.open().forEach(spark -> add(spark, table));
        return table.text();
    }

    private void add(Spark spark, Table table) {
        List<String> lines = spark.text().lines().toList();
        table.row(String.valueOf(spark.number()), moments.minute(spark.caughtAt()), spark.author().word(), focusCell(spark.focus()), lines.getFirst());
        lines.stream().skip(1).forEach(line -> table.line(CONTINUATION + line));
    }

    private static String focusCell(Focus focus) {
        if (focus.task() != null) {
            return "task " + focus.task();
        }
        if (focus.project() != null) {
            return "project " + focus.project();
        }
        return "no focus";
    }
}
