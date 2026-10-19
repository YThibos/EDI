package io.github.ythibos.edi.application.migration;

public record ArchiveSurvey(String path, int added, int unchanged, int replaced, long bytes) {

    public int files() {
        return added + unchanged + replaced;
    }
}
