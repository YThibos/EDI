package io.github.ythibos.edi.web.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.web.project.ProjectList.ListedProject;

class ProjectListTextTest {

    @Test
    void listsEachProjectWithItsWorkKeysAndALoginOtherThanTheDefault() {
        ProjectList list = new ProjectList(List.of(new ListedProject(new ProjectName("harness"), new ProjectKey("HRN"), LoginName.DEFAULT),
                new ListedProject(new ProjectName("garden"), null, LoginName.DEFAULT),
                new ListedProject(new ProjectName("kite"), new ProjectKey("KITE"), new LoginName("hobby"))));

        assertThat(new ProjectListText().text(list)).isEqualTo("""
                Projects (3)
                  harness  work keys HRN-<n>
                  garden
                  kite     work keys KITE-<n>  on the Claude login hobby
                """);
    }
}
