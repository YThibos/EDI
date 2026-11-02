package io.github.ythibos.edi.domain.authority;

public record CitedWords(String verbatim, CaptainMessage source) {

    public boolean comeFromADialog() {
        return source instanceof DialogAnswer;
    }
}
