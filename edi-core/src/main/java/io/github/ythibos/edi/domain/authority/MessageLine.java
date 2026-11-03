package io.github.ythibos.edi.domain.authority;

public record MessageLine(int number, String text, boolean pasted) {

    public boolean isCitable() {
        return !pasted && !text.isBlank();
    }
}
