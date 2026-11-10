package io.github.ythibos.edi.web.project;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.web.project.ProjectList.ListedProject;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class ProjectListText implements TextPresenter<ProjectList> {

    @Override
    public Class<ProjectList> presents() {
        return ProjectList.class;
    }

    @Override
    public String text(ProjectList list) {
        Table table = new Table("  ").line(Words.counted("Projects", list.projects().size()));
        list.projects().forEach(project -> table.row(project.name().value(), workKeysOf(project), loginOf(project)));
        return table.text();
    }

    private static String workKeysOf(ListedProject project) {
        return project.workKeys() == null ? "" : "work keys " + project.workKeys() + "-<n>";
    }

    private static String loginOf(ListedProject project) {
        return project.login().isTheDefault() ? "" : "on the Claude login " + project.login();
    }
}
