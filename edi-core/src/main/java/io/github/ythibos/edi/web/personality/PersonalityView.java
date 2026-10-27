package io.github.ythibos.edi.web.personality;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.ythibos.edi.application.personality.PersonalityInForce;
import io.github.ythibos.edi.domain.personality.Personality;
import io.github.ythibos.edi.domain.personality.Preset;

public record PersonalityView(String address, boolean chosen, boolean demoHolds, Map<String, Integer> levels, List<PresetView> presets) {

    public PersonalityView {
        presets = List.copyOf(presets);
    }

    static PersonalityView of(PersonalityInForce personality) {
        return new PersonalityView(personality.address().word(), personality.captains().isChosen(), personality.demoHolds(),
                levelsOf(personality.captains().own()), Arrays.stream(Preset.values()).map(PresetView::of).toList());
    }

    private static Map<String, Integer> levelsOf(Personality personality) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        personality.levels().forEach((scale, level) -> levels.put(scale.word(), level.value()));
        return levels;
    }

    public record PresetView(String name, Map<String, Integer> levels) {

        static PresetView of(Preset preset) {
            return new PresetView(preset.title(), levelsOf(preset.personality()));
        }
    }
}
