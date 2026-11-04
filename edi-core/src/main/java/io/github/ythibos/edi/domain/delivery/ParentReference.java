package io.github.ythibos.edi.domain.delivery;

public record ParentReference(MavenCoordinates coordinates, int firstLine, int lastLine) {

    boolean spans(int line) {
        return line >= firstLine && line <= lastLine;
    }
}
