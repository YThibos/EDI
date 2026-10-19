package io.github.ythibos.edi.adapter.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

public final class ConfigurationYaml {

    private ConfigurationYaml() {
    }

    public static YAMLMapper writer() {
        return YAMLMapper.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES, YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS, YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }
}
