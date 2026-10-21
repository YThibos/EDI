package io.github.ythibos.edi.application.port;

import java.util.Optional;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.DraftProposal;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;

public interface Forge {

    ForgeKind kind();

    Optional<MergeRequestView> mergeRequestFrom(RegisteredForge forge, BranchName branch);

    MergeRequestView view(RegisteredForge forge, MergeRequestAddress address);

    MergeRequestView openDraft(RegisteredForge forge, DraftProposal proposal);

    boolean mayChangeWorkflows(RegisteredForge forge);

    Optional<String> credentialHelperFor(RegisteredForge forge);
}
