package io.github.ythibos.edi.adapter.home;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.ythibos.edi.domain.personality.CaptainsPersonality;
import io.github.ythibos.edi.domain.personality.Level;
import io.github.ythibos.edi.domain.personality.PersonalityOverride;
import io.github.ythibos.edi.domain.personality.Preset;
import io.github.ythibos.edi.domain.personality.Scale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

final class PersonalitySection {

    static final String NAME = "edi-personality";
    private static final String DEMO = "demo";

    private PersonalitySection() {
    }

    static CaptainsPersonality personalityIn(JsonNode section) {
        if (section == null || section.isMissingNode() || section.isNull()) {
            return CaptainsPersonality.UNCHOSEN;
        }
        if (!section.isObject()) {
            throw new IllegalArgumentException(NAME + " holds the personality's scales and their levels, such as humour: 3.");
        }
        return new CaptainsPersonality(Optional.of(Preset.BALANCED.personality().overriddenBy(levelsIn(section, NAME))), demoIn(section));
    }

    static ObjectNode sectionOf(CaptainsPersonality personality, YAMLMapper writer) {
        ObjectNode section = writer.createObjectNode();
        personality.own().levels().forEach((scale, level) -> section.put(scale.word(), level.value()));
        ObjectNode demo = section.putObject(DEMO);
        personality.demo().levels().forEach((scale, level) -> demo.put(scale.word(), level.value()));
        return section;
    }

    private static PersonalityOverride demoIn(JsonNode section) {
        List<JsonNode> demos = demosIn(section);
        if (demos.size() > 1) {
            throw new IllegalArgumentException(NAME + " names " + DEMO + " twice; name the demo once.");
        }
        if (demos.isEmpty() || demos.getFirst().isNull()) {
            return PersonalityOverride.SHIPPED_DEMO;
        }
        JsonNode demo = demos.getFirst();
        if (!demo.isObject()) {
            throw new IllegalArgumentException(NAME + "." + DEMO + " holds the scales a demo overrides, such as formality: 7.");
        }
        return levelsIn(demo, NAME + "." + DEMO);
    }

    private static List<JsonNode> demosIn(JsonNode section) {
        return section.properties().stream().filter(entry -> isDemo(entry.getKey())).map(Map.Entry::getValue).toList();
    }

    private static boolean isDemo(String key) {
        return key.equalsIgnoreCase(DEMO);
    }

    private static PersonalityOverride levelsIn(JsonNode levels, String path) {
        Map<Scale, Level> written = new EnumMap<>(Scale.class);
        levels.properties().stream()
                .filter(entry -> !isDemo(entry.getKey()))
                .forEach(entry -> writeOnce(written, Scale.named(entry.getKey()), levelOf(entry.getValue(), path + "." + entry.getKey()), path));
        return new PersonalityOverride(written);
    }

    private static void writeOnce(Map<Scale, Level> written, Scale scale, Level level, String path) {
        if (written.put(scale, level) != null) {
            throw new IllegalArgumentException(path + " names " + scale.word() + " twice; name each scale once.");
        }
    }

    private static Level levelOf(JsonNode written, String path) {
        if (!written.isIntegralNumber() || !Level.admits(written.asInt())) {
            throw new IllegalArgumentException(path + " holds " + written + ", which is not a level from 0 to 10.");
        }
        return Level.of(written.asInt());
    }
}
