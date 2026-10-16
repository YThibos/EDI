package io.github.ythibos.edi.web.dispatch;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class BriefText implements TextPresenter<Brief> {

    @Override
    public Class<Brief> presents() {
        return Brief.class;
    }

    @Override
    public String text(Brief brief) {
        return brief.text();
    }
}
