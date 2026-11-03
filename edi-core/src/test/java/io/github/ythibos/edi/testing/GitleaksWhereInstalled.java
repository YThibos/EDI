package io.github.ythibos.edi.testing;

import java.util.List;

import io.github.ythibos.edi.adapter.gitleaks.Gitleaks;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.domain.publish.RequestText;
import io.github.ythibos.edi.domain.publish.TextFinding;
import tools.jackson.databind.json.JsonMapper;

public final class GitleaksWhereInstalled extends Gitleaks {

    private final boolean installed = InstalledPrograms.answers("gitleaks", "version");

    public GitleaksWhereInstalled(ExternalPrograms programs, JsonMapper json) {
        super(programs, json);
    }

    @Override
    public List<TextFinding> secretsIn(RequestText text) {
        return installed ? super.secretsIn(text) : List.of();
    }
}
