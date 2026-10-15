package io.github.ythibos.edi.domain.publish;

public sealed interface FindingPlace {

    String describedIn(String commit);

    sealed interface NamedPath extends FindingPlace {

        String path();

        FindingPlace hiddenAs(int position);
    }

    record CommitPart(String part) implements FindingPlace {

        @Override
        public String describedIn(String commit) {
            return part + " of commit " + commit;
        }
    }

    record File(String path) implements NamedPath {

        @Override
        public String describedIn(String commit) {
            return path + " in commit " + commit;
        }

        @Override
        public FindingPlace hiddenAs(int position) {
            return new HiddenFile(position);
        }
    }

    record Line(String path, int number) implements NamedPath {

        @Override
        public String describedIn(String commit) {
            return path + ":" + number + " in commit " + commit;
        }

        @Override
        public FindingPlace hiddenAs(int position) {
            return new HiddenLine(position, number);
        }
    }

    record HiddenFile(int position) implements FindingPlace {

        @Override
        public String describedIn(String commit) {
            return "path " + position + " of commit " + commit;
        }
    }

    record HiddenLine(int position, int number) implements FindingPlace {

        @Override
        public String describedIn(String commit) {
            return "line " + number + " of path " + position + " of commit " + commit;
        }
    }
}
