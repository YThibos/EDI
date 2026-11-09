package io.github.ythibos.edi.web.spark;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class SortedSparkText implements TextPresenter<SortedSpark> {

    @Override
    public Class<SortedSpark> presents() {
        return SortedSpark.class;
    }

    @Override
    public String text(SortedSpark sorted) {
        return SparkSentences.sorted(sorted.spark()) + "\n";
    }
}
