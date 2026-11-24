package io.github.ythibos.edi.domain.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DeliveryNote(List<String> stepsAfterLanding, String landingOrder) {

    public static final DeliveryNote BLANK = new DeliveryNote(List.of(), null);

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s{0,3}(?:[-*+]|\\d{1,3}[.)])\\s+(.*)$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(?:```|~~~).*$");
    private static final Pattern WORKED_LINE = Pattern.compile("^worked:\\s.*->.*$");
    private static final String STEPS_HEADING = "after the merge";
    private static final String ORDER_HEADING = "merge order";

    public DeliveryNote {
        stepsAfterLanding = List.copyOf(stepsAfterLanding);
        landingOrder = landingOrder == null || landingOrder.isBlank() ? null : landingOrder.strip();
    }

    public static DeliveryNote of(String markdown) {
        List<Section> sections = sectionsOf(markdown.lines().toList());
        return new DeliveryNote(stepsIn(sectionHeaded(sections, STEPS_HEADING)), landingOrderIn(sectionHeaded(sections, ORDER_HEADING)));
    }

    private static List<Section> sectionsOf(List<String> lines) {
        List<Section> sections = new ArrayList<>();
        Section current = null;
        boolean inAFence = false;
        for (String line : lines) {
            Matcher heading = HEADING.matcher(line);
            if (!inAFence && heading.matches()) {
                current = new Section(heading.group(2).toLowerCase(Locale.ROOT), heading.group(1).length(), new ArrayList<>());
                sections.add(current);
            } else if (current != null) {
                current.body().add(line);
            }
            inAFence ^= isAFence(line);
        }
        return sections;
    }

    private static boolean isAFence(String line) {
        return FENCE.matcher(line).matches();
    }

    private static List<String> sectionHeaded(List<Section> sections, String words) {
        List<String> body = new ArrayList<>();
        int level = 0;
        for (Section section : sections) {
            if (level > 0 && section.level() <= level) {
                break;
            }
            if (level == 0 && section.title().contains(words)) {
                level = section.level();
            }
            if (level > 0) {
                body.addAll(section.body());
            }
        }
        return body.stream().filter(line -> !isTheWorkedLine(line) && !isAFence(line)).toList();
    }

    private static boolean isTheWorkedLine(String line) {
        return WORKED_LINE.matcher(line.strip()).matches();
    }

    private static List<String> stepsIn(List<String> body) {
        List<String> steps = new ArrayList<>();
        boolean inAnItem = false;
        for (String line : body) {
            Matcher item = LIST_ITEM.matcher(line);
            if (item.matches()) {
                steps.add(item.group(1).strip());
                inAnItem = true;
            } else if (inAnItem && continuesAnItem(line)) {
                steps.set(steps.size() - 1, steps.getLast() + " " + line.strip());
            } else {
                inAnItem = false;
                if (!line.isBlank()) {
                    steps.add(line.strip());
                }
            }
        }
        return steps;
    }

    private static boolean continuesAnItem(String line) {
        return !line.isBlank() && Character.isWhitespace(line.charAt(0));
    }

    private static String landingOrderIn(List<String> body) {
        return String.join(" ", stepsIn(body));
    }

    private record Section(String title, int level, List<String> body) {
    }
}
