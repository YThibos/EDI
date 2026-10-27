package io.github.ythibos.edi.domain.project;

public enum NativeImageInput {
    REFLECTION("reflection or serialisation-bound types"),
    RESOURCES("resources the image reads"),
    SPRING_CONFIGURATION("Spring configuration"),
    NATIVE_IMAGE_CONFIGURATION("native-image configuration"),
    DEPENDENCIES("dependencies"),
    BUILD_FILES("build files");

    private final String words;

    NativeImageInput(String words) {
        this.words = words;
    }

    public String words() {
        return words;
    }
}
