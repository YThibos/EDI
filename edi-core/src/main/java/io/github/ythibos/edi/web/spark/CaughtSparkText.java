package io.github.ythibos.edi.web.spark;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class CaughtSparkText implements TextPresenter<CaughtSpark> {

    @Override
    public Class<CaughtSpark> presents() {
        return CaughtSpark.class;
    }

    @Override
    public String text(CaughtSpark caught) {
        return SparkSentences.caught(caught.spark()) + "\n";
    }
}
