package io.github.ythibos.edi.web.text;

public interface TextPresenter<T> {

    Class<T> presents();

    String text(T model);
}
