package io.github.ythibos.edi.adapter.fork;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class BacklogLines {

    private static final String SECTION_MARK = "##";
    private static final String DONE_BULLET = "- [x] ";
    private static final String OPEN_BULLET = "- [ ] ";
    private static final String BOLD_BULLET = "- **";
    private static final String BOLD_END = "**";
    private static final String TITLE_SEPARATOR = " - ";
    private static final String BODY_INDENT = "  ";

    private final List<String> lines;
    private final List<RawEntry> entries = new ArrayList<>();
    private final List<SetAsideEntry> setAside = new ArrayList<>();
    private final List<StrayLine> strayLines = new ArrayList<>();
    private boolean inPreamble = true;
    private String sectionTitle;
    private ForkSection section;

    private BacklogLines(List<String> lines) {
        this.lines = lines;
    }

    static BacklogLines read(String markdown) {
        BacklogLines backlog = new BacklogLines(markdown.lines().toList());
        int index = 0;
        while (index < backlog.lines.size()) {
            index = backlog.readFrom(index);
        }
        return backlog;
    }

    List<RawEntry> entries() {
        return entries;
    }

    List<SetAsideEntry> setAside() {
        return setAside;
    }

    List<StrayLine> strayLines() {
        return strayLines;
    }

    private int readFrom(int index) {
        String line = lines.get(index);
        if (isSectionHeader(line)) {
            enterSection(line.substring(SECTION_MARK.length()).strip());
            return index + 1;
        }
        if (inPreamble) {
            return index + 1;
        }
        if (section == null) {
            setAsideWhenAnEntry(index, line);
            return index + 1;
        }
        Optional<IdAndRest> bullet = bulletIn(line);
        if (bullet.isPresent()) {
            return readEntry(index, bullet.get());
        }
        if (!line.isBlank()) {
            strayLines.add(new StrayLine(index + 1, line));
        }
        return index + 1;
    }

    private static boolean isSectionHeader(String line) {
        return line.startsWith(SECTION_MARK) && line.length() > SECTION_MARK.length() && Character.isWhitespace(line.charAt(SECTION_MARK.length()));
    }

    private void enterSection(String title) {
        inPreamble = false;
        sectionTitle = title;
        section = ForkSection.titled(title).orElse(null);
    }

    private void setAsideWhenAnEntry(int index, String line) {
        Optional.of(line)
                .filter(text -> text.startsWith(DONE_BULLET) || text.startsWith(OPEN_BULLET))
                .flatMap(text -> idAndRest(text.substring(OPEN_BULLET.length())))
                .ifPresent(entry -> setAside.add(new SetAsideEntry(index + 1, sectionTitle, entry.id())));
    }

    private Optional<IdAndRest> bulletIn(String line) {
        return switch (section) {
            case DONE -> afterPrefix(line, DONE_BULLET).flatMap(BacklogLines::idAndRest);
            case QUEUED -> afterPrefix(line, OPEN_BULLET).flatMap(BacklogLines::idAndRest);
            case IN_FLIGHT -> afterPrefix(line, BOLD_BULLET).flatMap(BacklogLines::boldIdAndRest)
                    .or(() -> afterPrefix(line, OPEN_BULLET).flatMap(BacklogLines::idAndRest));
        };
    }

    private int readEntry(int index, IdAndRest bullet) {
        List<String> body = new ArrayList<>();
        int next = index + 1;
        while (next < lines.size() && isBodyLine(lines.get(next))) {
            String line = lines.get(next);
            body.add(line.isBlank() ? "" : line.substring(BODY_INDENT.length()));
            next++;
        }
        while (!body.isEmpty() && body.getLast().isEmpty()) {
            body.removeLast();
        }
        entries.add(new RawEntry(index + 1, section, bullet.id(), bullet.rest(), body));
        return next;
    }

    private static Optional<String> afterPrefix(String line, String prefix) {
        return line.startsWith(prefix) ? Optional.of(line.substring(prefix.length())) : Optional.empty();
    }

    private static Optional<IdAndRest> idAndRest(String text) {
        int space = text.indexOf(' ');
        if (space < 0 || !text.startsWith(TITLE_SEPARATOR, space)) {
            return Optional.empty();
        }
        return identified(text.substring(0, space), text.substring(space + TITLE_SEPARATOR.length()));
    }

    private static Optional<IdAndRest> boldIdAndRest(String text) {
        int end = text.indexOf(BOLD_END + TITLE_SEPARATOR);
        if (end < 0) {
            return Optional.empty();
        }
        return identified(text.substring(0, end), text.substring(end + BOLD_END.length() + TITLE_SEPARATOR.length()));
    }

    private static boolean isBodyLine(String line) {
        return line.isBlank() || line.startsWith(BODY_INDENT);
    }

    private static Optional<IdAndRest> identified(String id, String rest) {
        return TailTags.isForkId(id) ? Optional.of(new IdAndRest(id, rest)) : Optional.empty();
    }

    record RawEntry(int line, ForkSection section, String id, String rest, List<String> body) {
    }

    private record IdAndRest(String id, String rest) {
    }
}
