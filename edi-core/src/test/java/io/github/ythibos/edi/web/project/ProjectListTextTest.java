package io.github.ythibos.edi.web.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;

class ProjectListTextTest {

    @Test
    void listsEachProjectWithItsWorkKeys() {
        ProjectList list = new ProjectList(List.of(new Project(new ProjectName("harness"), new ProjectKey("HRN")), new Project(new ProjectName("garden"), null)));

        assertThat(new ProjectListText().text(list)).isEqualTo("""
                Projects (2)
                  harness  work keys HRN-<n>
                  garden
                """);
    }
}
