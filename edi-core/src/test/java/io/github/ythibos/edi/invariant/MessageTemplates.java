package io.github.ythibos.edi.invariant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class MessageTemplates {

    private static final List<String> JAVA_SOURCES = List.of("edi-core/src/main/java", "edi-cli/src/main/java");
    private static final Set<String> READ_BY_WORKERS = Set.of("BriefSections.java");
    private static final String CONSOLE_SOURCES = "edi-console/src";
    private static final List<String> PRIMARY_LAYER = List.of("claude/primary", "claude/skills");
    private static final String PLACEHOLDER = "{}";

    private MessageTemplates() {
    }

    static List<MessageTemplate> readByThePrimaryOrTheCaptain() {
        return Stream.of(javaSources().flatMap(MessageTemplates::javaTemplatesIn), consoleSources().flatMap(MessageTemplates::linesIn),
                        primaryLayerSources().flatMap(MessageTemplates::linesIn))
                .flatMap(templates -> templates)
                .toList();
    }

    static List<MessageTemplate> javaTemplatesIn(String source, String code) {
        return new JavaLiterals(source, code).templates();
    }

    private static Stream<Path> javaSources() {
        return JAVA_SOURCES.stream().flatMap(root -> filesUnder(root, name -> name.endsWith(".java") && !READ_BY_WORKERS.contains(name)));
    }

    private static Stream<Path> filesUnder(String root, Predicate<String> named) {
        try (Stream<Path> files = Files.walk(RepositoryRoot.path().resolve(root))) {
            return files.filter(file -> named.test(file.getFileName().toString())).sorted().toList().stream();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Stream<MessageTemplate> javaTemplatesIn(Path file) {
        return javaTemplatesIn(relative(file), read(file)).stream();
    }

    private static String relative(Path file) {
        return RepositoryRoot.path().relativize(file).toString();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Stream<Path> consoleSources() {
        return filesUnder(CONSOLE_SOURCES, name -> (name.endsWith(".html") || name.endsWith(".ts")) && !name.endsWith(".spec.ts")
                && !name.endsWith(".fixture.ts"));
    }

    private static Stream<MessageTemplate> linesIn(Path file) {
        List<String> lines = read(file).lines().toList();
        return IntStream.range(0, lines.size()).mapToObj(index -> new MessageTemplate(relative(file), index + 1, lines.get(index).strip()));
    }

    private static Stream<Path> primaryLayerSources() {
        return PRIMARY_LAYER.stream().flatMap(root -> filesUnder(root, name -> name.endsWith(".md")));
    }

    record MessageTemplate(String source, int line, String text) {

        @Override
        public String toString() {
            return source + ":" + line + ": " + text;
        }
    }

    private static final class JavaLiterals {

        private final String source;
        private final String code;
        private final List<MessageTemplate> templates = new ArrayList<>();
        private final List<String> statement = new ArrayList<>();
        private int position;
        private int statementLine;

        JavaLiterals(String source, String code) {
            this.source = source;
            this.code = code;
        }

        List<MessageTemplate> templates() {
            while (position < code.length()) {
                readNext();
            }
            endStatement();
            return templates;
        }

        private void readNext() {
            if (startsWith("//")) {
                skipPast("\n");
            } else if (startsWith("/*")) {
                skipPast("*/");
            } else if (startsWith("\"\"\"")) {
                literal(position, textBlock());
            } else if (startsWith("\"")) {
                literal(position, stringLiteral());
            } else if (startsWith("'")) {
                skipCharacterLiteral();
            } else if (startsWith("@") && annotationWithValues()) {
                skipAnnotationValues();
            } else {
                endStatementAt(code.charAt(position++));
            }
        }

        private boolean startsWith(String prefix) {
            return code.startsWith(prefix, position);
        }

        private void skipPast(String end) {
            int found = code.indexOf(end, position);
            position = found < 0 ? code.length() : found + end.length();
        }

        private String textBlock() {
            int start = code.indexOf('\n', position) + 1;
            int end = code.indexOf("\"\"\"", start);
            position = end + 3;
            return code.substring(start, end).stripIndent();
        }

        private String stringLiteral() {
            StringBuilder text = new StringBuilder();
            position++;
            while (code.charAt(position) != '"') {
                text.append(code.charAt(position) == '\\' ? unescaped(code.charAt(++position)) : code.charAt(position));
                position++;
            }
            position++;
            return text.toString();
        }

        private static char unescaped(char escaped) {
            return switch (escaped) {
                case 'n' -> '\n';
                case 't' -> '\t';
                default -> escaped;
            };
        }

        private void skipCharacterLiteral() {
            position += code.charAt(position + 1) == '\\' ? 3 : 2;
            skipPast("'");
        }

        private boolean annotationWithValues() {
            int after = position + 1;
            while (after < code.length() && (Character.isJavaIdentifierPart(code.charAt(after)) || code.charAt(after) == '.')) {
                after++;
            }
            return after < code.length() && code.charAt(after) == '(';
        }

        private void skipAnnotationValues() {
            position = code.indexOf('(', position) + 1;
            int depth = 1;
            while (depth > 0) {
                if (startsWith("\"")) {
                    stringLiteral();
                } else {
                    char next = code.charAt(position++);
                    depth += next == '(' ? 1 : next == ')' ? -1 : 0;
                }
            }
        }

        private void literal(int start, String text) {
            if (statement.isEmpty()) {
                statementLine = lineAt(start);
            }
            statement.add(text);
        }

        private int lineAt(int offset) {
            return (int) code.substring(0, offset).chars().filter(character -> character == '\n').count() + 1;
        }

        private void endStatementAt(char character) {
            if (character == ';' || character == '{' || character == '}') {
                endStatement();
            }
        }

        private void endStatement() {
            if (!statement.isEmpty()) {
                templates.add(new MessageTemplate(source, statementLine, String.join(PLACEHOLDER, statement)));
                statement.clear();
            }
        }
    }
}
