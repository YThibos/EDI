package io.github.ythibos.edi.web.report;

import java.util.List;

import io.github.ythibos.edi.domain.project.ProjectName;

record ProjectSet(List<String> names) {

    static final String FLEET = "Fleet";
    private static final String JOINER = " + ";

    ProjectSet {
        names = names.stream().map(Glyphs::safe).toList();
    }

    static ProjectSet of(List<ProjectName> projects) {
        return new ProjectSet(projects.stream().map(ProjectName::value).toList());
    }

    String title() {
        return names.isEmpty() ? FLEET : String.join(JOINER, names);
    }
}
