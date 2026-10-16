package io.github.ythibos.edi.web.publish;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.publish.PublishFinding;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class PublishCheckText implements TextPresenter<PublishCheckResult> {

    @Override
    public Class<PublishCheckResult> presents() {
        return PublishCheckResult.class;
    }

    @Override
    public String text(PublishCheckResult result) {
        return Stream.concat(
                        result.verdict().findings().stream().map(PublishFinding::toString),
                        Stream.of(result.verdict().conclusion(result.range())))
                .collect(Collectors.joining("\n", "", "\n"));
    }
}
