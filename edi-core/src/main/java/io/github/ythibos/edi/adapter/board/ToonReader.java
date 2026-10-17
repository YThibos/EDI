package io.github.ythibos.edi.adapter.board;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

final class ToonReader {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final int INDENT = 2;
    private static final char QUOTE = '"';
    private static final char ESCAPE = '\\';
    private static final char COMMA = ',';
    private static final String LIST_ITEM = "- ";
    private static final String EMPTY_LIST_ITEM = "-";
    private static final char FIELD_SEPARATOR = ':';
    private static final Pattern UNQUOTED_KEY = Pattern.compile("[A-Za-z_][\\w.]*");
    private static final Pattern ARRAY_HEADER = Pattern.compile("\\[(\\d+)([\\t|])?](?:\\{([^}]*)})?:(.*)");
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");
    private static final int HEADER_LENGTH = 1;
    private static final int HEADER_DELIMITER = 2;
    private static final int HEADER_FIELDS = 3;
    private static final int HEADER_INLINE_VALUES = 4;

    private final List<Line> lines;
    private int next;

    private ToonReader(List<Line> lines) {
        this.lines = lines;
    }

    static ObjectNode read(String text) {
        ToonReader reader = new ToonReader(linesOf(text));
        ObjectNode document = reader.objectAt(0);
        if (reader.next < reader.lines.size()) {
            throw reader.unreadable(reader.lines.get(reader.next), "it is indented deeper than anything above it allows");
        }
        return document;
    }

    private static List<Line> linesOf(String text) {
        List<Line> lines = new ArrayList<>();
        String[] rows = text.split("\n", -1);
        for (int index = 0; index < rows.length; index++) {
            String row = rows[index].stripTrailing();
            if (!row.isBlank()) {
                lines.add(Line.of(row, index + 1));
            }
        }
        return lines;
    }

    private ObjectNode objectAt(int depth) {
        ObjectNode object = NODES.objectNode();
        while (next < lines.size() && lines.get(next).depth() == depth && !lines.get(next).isListItem()) {
            Line line = lines.get(next++);
            fieldInto(object, line, line.content(), depth);
        }
        return object;
    }

    private void fieldInto(ObjectNode object, Line line, String content, int depth) {
        int keyLength = keyLengthOf(content);
        if (keyLength == 0) {
            throw unreadable(line, "it is neither a key and its value nor an array header");
        }
        String key = keyOf(content.substring(0, keyLength), line);
        Matcher header = ARRAY_HEADER.matcher(content.substring(keyLength));
        if (header.matches()) {
            object.set(key, arrayOf(header, line, depth));
        } else if (isFieldSeparatorAt(content, keyLength)) {
            String value = content.substring(keyLength + 1).strip();
            object.set(key, value.isEmpty() ? objectAt(depth + 1) : primitive(value, line));
        } else {
            throw unreadable(line, "it is neither a key and its value nor an array header");
        }
    }

    private static int keyLengthOf(String content) {
        if (content.charAt(0) == QUOTE) {
            return closingQuoteOf(content) + 1;
        }
        Matcher key = UNQUOTED_KEY.matcher(content);
        return key.lookingAt() ? key.end() : 0;
    }

    private static int closingQuoteOf(String content) {
        for (int index = 1; index < content.length(); index++) {
            if (content.charAt(index) == ESCAPE) {
                index++;
            } else if (content.charAt(index) == QUOTE) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isFieldSeparatorAt(String content, int position) {
        return position < content.length() && content.charAt(position) == FIELD_SEPARATOR;
    }

    private ArrayNode arrayOf(Matcher header, Line line, int depth) {
        int length = Integer.parseInt(header.group(HEADER_LENGTH));
        char delimiter = header.group(HEADER_DELIMITER) == null ? COMMA : header.group(HEADER_DELIMITER).charAt(0);
        String inlineValues = header.group(HEADER_INLINE_VALUES).strip();
        ArrayNode array;
        if (header.group(HEADER_FIELDS) != null) {
            array = rowsAt(depth + 1, fieldNames(header.group(HEADER_FIELDS), delimiter, line), delimiter, length, line);
        } else if (!inlineValues.isEmpty()) {
            array = primitivesIn(inlineValues, delimiter, line);
        } else {
            array = itemsAt(depth + 1);
        }
        if (array.size() != length) {
            throw unreadable(line, "its header promises " + length + " entries and " + array.size() + " follow");
        }
        return array;
    }

    private List<String> fieldNames(String header, char delimiter, Line line) {
        return splitAt(header, delimiter, line).stream().map(name -> keyOf(name, line)).toList();
    }

    private ArrayNode rowsAt(int depth, List<String> fields, char delimiter, int length, Line header) {
        ArrayNode rows = NODES.arrayNode();
        while (rows.size() < length && next < lines.size() && lines.get(next).depth() == depth) {
            Line row = lines.get(next++);
            List<String> values = splitAt(row.content(), delimiter, row);
            if (values.size() != fields.size()) {
                throw unreadable(row, "the row has " + values.size() + " values for the " + fields.size() + " fields of line " + header.number());
            }
            ObjectNode entry = NODES.objectNode();
            for (int index = 0; index < fields.size(); index++) {
                entry.set(fields.get(index), primitive(values.get(index), row));
            }
            rows.add(entry);
        }
        return rows;
    }

    private ArrayNode itemsAt(int depth) {
        ArrayNode items = NODES.arrayNode();
        while (next < lines.size() && lines.get(next).depth() == depth && lines.get(next).isListItem()) {
            items.add(itemOf(lines.get(next++), depth));
        }
        return items;
    }

    private JsonNode itemOf(Line line, int depth) {
        String content = line.content().equals(EMPTY_LIST_ITEM) ? "" : line.content().substring(LIST_ITEM.length());
        if (content.isEmpty()) {
            return NODES.objectNode();
        }
        Matcher headerWithoutKey = ARRAY_HEADER.matcher(content);
        if (headerWithoutKey.matches()) {
            return arrayOf(headerWithoutKey, line, depth);
        }
        if (opensAField(content)) {
            ObjectNode object = NODES.objectNode();
            fieldInto(object, line, content, depth + 1);
            object.setAll(objectAt(depth + 1));
            return object;
        }
        return primitive(content, line);
    }

    private static boolean opensAField(String content) {
        int keyLength = keyLengthOf(content);
        return keyLength > 0 && (isFieldSeparatorAt(content, keyLength) || ARRAY_HEADER.matcher(content.substring(keyLength)).matches());
    }

    private ArrayNode primitivesIn(String values, char delimiter, Line line) {
        ArrayNode array = NODES.arrayNode();
        splitAt(values, delimiter, line).forEach(value -> array.add(primitive(value, line)));
        return array;
    }

    private List<String> splitAt(String values, char delimiter, Line line) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < values.length(); index++) {
            char character = values.charAt(index);
            if (quoted && character == ESCAPE && index + 1 < values.length()) {
                part.append(character).append(values.charAt(++index));
            } else if (character == QUOTE) {
                quoted = !quoted;
                part.append(character);
            } else if (!quoted && character == delimiter) {
                parts.add(part.toString().strip());
                part.setLength(0);
            } else {
                part.append(character);
            }
        }
        if (quoted) {
            throw unreadable(line, "a quoted value is never closed");
        }
        parts.add(part.toString().strip());
        return parts;
    }

    private String keyOf(String key, Line line) {
        return key.startsWith(String.valueOf(QUOTE)) ? unquoted(key, line) : key;
    }

    private JsonNode primitive(String token, Line line) {
        if (token.startsWith(String.valueOf(QUOTE))) {
            return NODES.stringNode(unquoted(token, line));
        }
        return switch (token) {
            case "null" -> NODES.nullNode();
            case "true" -> NODES.booleanNode(true);
            case "false" -> NODES.booleanNode(false);
            case "[]" -> NODES.arrayNode();
            default -> NUMBER.matcher(token).matches() ? number(token) : NODES.stringNode(token);
        };
    }

    private static JsonNode number(String token) {
        BigDecimal value = new BigDecimal(token);
        try {
            return NODES.numberNode(value.intValueExact());
        } catch (ArithmeticException notAnInt) {
            return wholeOrFractional(value);
        }
    }

    private static JsonNode wholeOrFractional(BigDecimal value) {
        try {
            return NODES.numberNode(value.longValueExact());
        } catch (ArithmeticException notALong) {
            return NODES.numberNode(value.doubleValue());
        }
    }

    private String unquoted(String token, Line line) {
        if (token.length() < 2 || token.charAt(token.length() - 1) != QUOTE) {
            throw unreadable(line, "the quoted value " + token + " does not end in a quote");
        }
        StringBuilder value = new StringBuilder();
        for (int index = 1; index < token.length() - 1; index++) {
            char character = token.charAt(index);
            if (character == QUOTE) {
                throw unreadable(line, "a quote inside the value " + token + " is not escaped");
            }
            if (character == ESCAPE) {
                index = appendEscaped(token, index, value, line);
            } else {
                value.append(character);
            }
        }
        return value.toString();
    }

    private int appendEscaped(String token, int escape, StringBuilder value, Line line) {
        char escaped = token.charAt(escape + 1);
        switch (escaped) {
            case 'n' -> value.append('\n');
            case 'r' -> value.append('\r');
            case 't' -> value.append('\t');
            case '"' -> value.append('"');
            case '\\' -> value.append('\\');
            case 'u' -> {
                value.append(unicodeAt(token, escape + 2, line));
                return escape + 5;
            }
            default -> throw unreadable(line, "the escape \\" + escaped + " is not one TOON knows");
        }
        return escape + 1;
    }

    private char unicodeAt(String token, int start, Line line) {
        if (start + 4 > token.length() - 1) {
            throw unreadable(line, "a \\u escape needs four hexadecimal digits");
        }
        try {
            return (char) Integer.parseInt(token.substring(start, start + 4), 16);
        } catch (NumberFormatException notHexadecimal) {
            throw unreadable(line, "a \\u escape needs four hexadecimal digits");
        }
    }

    private UnreadableResult unreadable(Line line, String reason) {
        return new UnreadableResult("Line " + line.number() + " of the Lavish result cannot be read: " + reason + ".");
    }

    private record Line(int depth, String content, int number) {

        static Line of(String row, int number) {
            int spaces = 0;
            while (spaces < row.length() && row.charAt(spaces) == ' ') {
                spaces++;
            }
            if (spaces % INDENT != 0) {
                throw new UnreadableResult("Line " + number + " of the Lavish result cannot be read: it is indented by " + spaces
                        + " spaces, not a multiple of " + INDENT + ".");
            }
            return new Line(spaces / INDENT, row.substring(spaces), number);
        }

        boolean isListItem() {
            return content.equals(EMPTY_LIST_ITEM) || content.startsWith(LIST_ITEM);
        }
    }
}
