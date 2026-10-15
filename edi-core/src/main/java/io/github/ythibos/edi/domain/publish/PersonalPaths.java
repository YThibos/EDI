package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.stream.Stream;

public record PersonalPaths(List<PersonalArea> areas) {

    public PersonalPaths {
        areas = List.copyOf(areas);
    }

    public static PersonalPaths personalLayer() {
        return new PersonalPaths(List.of(new PersonalArea("personal/", "the personal layer")));
    }

    public PersonalPaths andEdiHomeAt(String prefix) {
        return new PersonalPaths(Stream.concat(areas.stream(), Stream.of(new PersonalArea(prefix, "the EDI home"))).toList());
    }

    public List<PublishFinding> findIn(OutgoingChanges changes) {
        return changes.commits().stream().flatMap(this::findIn).toList();
    }

    private Stream<PublishFinding> findIn(OutgoingCommit commit) {
        return commit.paths().stream().flatMap(path -> areas.stream()
                .filter(area -> area.holds(path))
                .map(area -> PublishFinding.onPath(PublishRule.PERSONAL_PATH, path, commit.id(), "inside " + area.name())));
    }

    public record PersonalArea(String prefix, String name) {

        boolean holds(String path) {
            return path.startsWith(prefix);
        }
    }
}
