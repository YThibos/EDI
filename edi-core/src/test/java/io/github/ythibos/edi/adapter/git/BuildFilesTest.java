package io.github.ythibos.edi.adapter.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.delivery.BuildFile;
import io.github.ythibos.edi.domain.delivery.MavenCoordinates;
import io.github.ythibos.edi.domain.delivery.ParentReference;

class BuildFilesTest {

    private static final MavenCoordinates REACTOR = new MavenCoordinates("example.harness", "harness", "3.1.0-SNAPSHOT");

    @Test
    void readsAModuleThatInheritsItsGroupAndVersionFromItsParent() {
        String module = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <parent>
                    <groupId>example.harness</groupId>
                    <artifactId>harness</artifactId>
                    <version>3.1.0-SNAPSHOT</version>
                  </parent>
                  <artifactId>lamp</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>example.other</groupId>
                      <artifactId>bulb</artifactId>
                      <version>2.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """;

        assertThat(BuildFiles.read("lamp/pom.xml", module)).contains(new BuildFile("lamp/pom.xml",
                new MavenCoordinates("example.harness", "lamp", "3.1.0-SNAPSHOT"), Optional.of(new ParentReference(REACTOR, 3, 7))));
    }

    @Test
    void readsAReactorWithoutAParent() {
        String reactor = """
                <project>
                  <groupId>example.harness</groupId>
                  <artifactId>harness</artifactId>
                  <version>3.1.0-SNAPSHOT</version>
                </project>
                """;

        assertThat(BuildFiles.read("pom.xml", reactor)).contains(new BuildFile("pom.xml", REACTOR, Optional.empty()));
    }

    @Test
    void readsNothingFromAFileItCannotParseOrThatDeclaresADoctype() {
        String withAnEntity = """
                <?xml version="1.0"?>
                <!DOCTYPE project [<!ENTITY lamp SYSTEM "file:///etc/hostname">]>
                <project><groupId>&lamp;</groupId><artifactId>harness</artifactId><version>1.0.0-SNAPSHOT</version></project>
                """;

        assertThat(BuildFiles.read("pom.xml", withAnEntity)).isEmpty();
        assertThat(BuildFiles.read("pom.xml", "<project><artifactId>harness</project>")).isEmpty();
    }
}
