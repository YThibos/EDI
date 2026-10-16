package io.github.ythibos.edi.domain.worker;

public record BootId(String value) {

    public BootId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A machine boot is named by the boot id the kernel gives it.");
        }
        value = value.strip();
    }

    @Override
    public String toString() {
        return value;
    }
}
