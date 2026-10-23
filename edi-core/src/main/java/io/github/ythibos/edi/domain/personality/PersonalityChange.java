package io.github.ythibos.edi.domain.personality;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public sealed interface PersonalityChange permits Preset, PersonalityOverride {

    String HOW_TO_CHANGE = "A personality change names a preset, such as balanced, or scales with their levels, such as humour 6 formality 8.";

    Personality appliedTo(Personality own);

    static PersonalityChange parse(List<String> words) {
        if (words.size() == 1) {
            return presetNamed(words.getFirst());
        }
        if (words.isEmpty() || words.size() % 2 != 0) {
            throw new IllegalArgumentException(HOW_TO_CHANGE);
        }
        return levelsIn(words);
    }

    private static Preset presetNamed(String word) {
        return Preset.named(word).orElseThrow(() -> new IllegalArgumentException(word + " is not a preset; the presets are " + Preset.listed() + "."));
    }

    private static PersonalityOverride levelsIn(List<String> words) {
        Map<Scale, Level> levels = new EnumMap<>(Scale.class);
        for (int scale = 0; scale < words.size(); scale += 2) {
            Scale named = Scale.named(words.get(scale));
            if (levels.put(named, Level.parse(words.get(scale + 1))) != null) {
                throw new IllegalArgumentException("The change names " + named.word() + " twice; name each scale once.");
            }
        }
        return new PersonalityOverride(levels);
    }
}
