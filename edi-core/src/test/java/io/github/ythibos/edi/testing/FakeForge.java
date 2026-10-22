package io.github.ythibos.edi.testing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Forge;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.DraftProposal;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.task.BaseBranch;

public final class FakeForge implements Forge {

    private final ForgeKind kind;
    private final Clock clock;
    private final Map<Integer, MergeRequestView> mergeRequests = new HashMap<>();
    private final Map<String, String> pushedHeads = new HashMap<>();
    private final List<DraftProposal> openedDrafts = new ArrayList<>();
    private final Set<MergeRequestAddress> unreachable = new HashSet<>();
    private boolean workflowScope = true;
    private int lastNumber;

    public FakeForge(ForgeKind kind, Clock clock) {
        this.kind = kind;
        this.clock = clock;
    }

    @Override
    public ForgeKind kind() {
        return kind;
    }

    @Override
    public synchronized Optional<MergeRequestView> mergeRequestFrom(RegisteredForge forge, BranchName branch) {
        return MergeRequestView.deliveryAmong(mergeRequests.values().stream()
                .filter(request -> request.address().repository().equals(forge.repository()))
                .filter(request -> branch.toString().equals(request.sourceBranch()))
                .sorted(Comparator.comparingInt((MergeRequestView request) -> request.address().number()).reversed())
                .toList());
    }

    @Override
    public synchronized MergeRequestView view(RegisteredForge forge, MergeRequestAddress address) {
        if (unreachable.contains(address)) {
            throw new ProgramFailure("gh pr view " + address.number() + " failed: the repository is unreachable");
        }
        MergeRequestView request = mergeRequests.get(address.number());
        if (request == null || !request.address().equals(address)) {
            throw new IllegalStateException("The fake forge holds no " + address + ".");
        }
        return request;
    }

    @Override
    public synchronized MergeRequestView openDraft(RegisteredForge forge, DraftProposal proposal) {
        openedDrafts.add(proposal);
        return opened(forge, proposal.branch(), proposal.base(), pushedHeads.get(proposal.branch().toString()), true);
    }

    @Override
    public synchronized boolean mayChangeWorkflows(RegisteredForge forge) {
        return workflowScope;
    }

    @Override
    public Optional<String> credentialHelperFor(RegisteredForge forge) {
        return Optional.empty();
    }

    public synchronized MergeRequestView opensADraft(RegisteredForge forge, BranchName branch, BaseBranch base, String head) {
        return opened(forge, branch, base, head, true);
    }

    public synchronized MergeRequestView opensAReadyMergeRequest(RegisteredForge forge, BranchName branch, BaseBranch base, String head) {
        return opened(forge, branch, base, head, false);
    }

    public synchronized void pushed(BranchName branch, String head) {
        pushedHeads.put(branch.toString(), head);
    }

    public synchronized MergeRequestView marksReady(MergeRequestAddress address) {
        MergeRequestView request = mergeRequests.get(address.number());
        MergeRequestView ready = new MergeRequestView(address, request.state(), false, request.sourceBranch(), request.targetBranch(),
                request.headCommit(), request.landedCommit(), request.mergedAt());
        mergeRequests.put(address.number(), ready);
        return ready;
    }

    public synchronized MergeRequestView merges(MergeRequestAddress address, String landedCommit) {
        return changed(address, MergeRequestState.MERGED, landedCommit, clock.now());
    }

    public synchronized MergeRequestView closes(MergeRequestAddress address) {
        return changed(address, MergeRequestState.CLOSED, null, null);
    }

    public synchronized void cannotShow(MergeRequestAddress address) {
        unreachable.add(address);
    }

    public synchronized void withoutTheWorkflowScope() {
        workflowScope = false;
    }

    public synchronized List<DraftProposal> openedDrafts() {
        return List.copyOf(openedDrafts);
    }

    private MergeRequestView opened(RegisteredForge forge, BranchName branch, BaseBranch base, String head, boolean draft) {
        MergeRequestAddress address = mergeRequestFrom(forge, branch).filter(request -> request.state() == MergeRequestState.OPEN)
                .map(MergeRequestView::address)
                .orElseGet(() -> new MergeRequestAddress(forge.repository(), ++lastNumber));
        MergeRequestView request = new MergeRequestView(address, MergeRequestState.OPEN, draft, branch.toString(), base.name(), head, null, null);
        mergeRequests.put(address.number(), request);
        return request;
    }

    private MergeRequestView changed(MergeRequestAddress address, MergeRequestState state, String landedCommit, Instant mergedAt) {
        MergeRequestView request = mergeRequests.get(address.number());
        MergeRequestView changed = new MergeRequestView(address, state, request.draft(), request.sourceBranch(), request.targetBranch(),
                request.headCommit(), landedCommit, mergedAt);
        mergeRequests.put(address.number(), changed);
        return changed;
    }
}
