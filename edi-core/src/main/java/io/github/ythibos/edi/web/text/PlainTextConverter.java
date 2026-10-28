package io.github.ythibos.edi.web.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;

@Component
class PlainTextConverter extends AbstractHttpMessageConverter<Object> {

    private final List<TextPresenter<?>> presenters;

    PlainTextConverter(List<TextPresenter<?>> presenters) {
        super(StandardCharsets.UTF_8, MediaType.TEXT_PLAIN);
        this.presenters = List.copyOf(presenters);
    }

    @Override
    protected boolean supports(Class<?> type) {
        return presenterOf(type).isPresent();
    }

    @Override
    protected boolean canRead(MediaType mediaType) {
        return false;
    }

    @Override
    protected Object readInternal(Class<?> type, HttpInputMessage input) {
        throw new HttpMessageNotReadableException("Requests are read as JSON, not plain text.", input);
    }

    @Override
    protected void writeInternal(Object model, HttpOutputMessage output) throws IOException {
        TextPresenter<?> presenter = presenterOf(model.getClass()).orElseThrow();
        output.getBody().write(textOf(presenter, model).getBytes(StandardCharsets.UTF_8));
    }

    private Optional<TextPresenter<?>> presenterOf(Class<?> type) {
        return presenters.stream().filter(presenter -> presenter.presents().isAssignableFrom(type)).findFirst();
    }

    private static <T> String textOf(TextPresenter<T> presenter, Object model) {
        return presenter.text(presenter.presents().cast(model));
    }
}
