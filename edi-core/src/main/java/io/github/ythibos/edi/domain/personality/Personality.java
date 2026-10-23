package io.github.ythibos.edi.domain.personality;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

public record Personality(Map<Scale, Level> levels) {

    private static final Level BAD_NEWS_FLAVOUR_CEILING = Level.of(3);
    private static final Level BAD_NEWS_DIRECTNESS_FLOOR = Level.of(7);
    private static final Level BAD_NEWS_VERBOSITY_FLOOR = Level.of(3);

    public Personality {
        EnumMap<Scale, Level> everyScale = new EnumMap<>(Scale.class);
        everyScale.putAll(levels);
        if (everyScale.size() != Scale.values().length) {
            throw new IllegalArgumentException("A personality sets every scale; this one lacks " + missingFrom(everyScale) + ".");
        }
        levels = Collections.unmodifiableMap(everyScale);
    }

    public static Personality of(int humour, int formality, int verbosity, int warmth, int directness, int initiative, int depth, int enthusiasm,
            int flavour) {
        return new Personality(Map.of(Scale.HUMOUR, Level.of(humour), Scale.FORMALITY, Level.of(formality), Scale.VERBOSITY, Level.of(verbosity),
                Scale.WARMTH, Level.of(warmth), Scale.DIRECTNESS, Level.of(directness), Scale.INITIATIVE, Level.of(initiative), Scale.DEPTH,
                Level.of(depth), Scale.ENTHUSIASM, Level.of(enthusiasm), Scale.FLAVOUR, Level.of(flavour)));
    }

    public Level levelOf(Scale scale) {
        return levels.get(scale);
    }

    public Personality overriddenBy(PersonalityOverride override) {
        EnumMap<Scale, Level> overridden = new EnumMap<>(levels);
        overridden.putAll(override.levels());
        return new Personality(overridden);
    }

    public Personality forBadNews() {
        return overriddenBy(new PersonalityOverride(Map.of(
                Scale.HUMOUR, Level.LOWEST,
                Scale.ENTHUSIASM, Level.LOWEST,
                Scale.FLAVOUR, levelOf(Scale.FLAVOUR).atMost(BAD_NEWS_FLAVOUR_CEILING),
                Scale.DIRECTNESS, levelOf(Scale.DIRECTNESS).atLeast(BAD_NEWS_DIRECTNESS_FLOOR),
                Scale.VERBOSITY, levelOf(Scale.VERBOSITY).atLeast(BAD_NEWS_VERBOSITY_FLOOR))));
    }

    public Addressing addressing() {
        return Addressing.at(levelOf(Scale.FORMALITY));
    }

    private static String missingFrom(Map<Scale, Level> levels) {
        return Arrays.stream(Scale.values()).filter(scale -> !levels.containsKey(scale)).map(Scale::word).collect(Collectors.joining(", "));
    }
}
