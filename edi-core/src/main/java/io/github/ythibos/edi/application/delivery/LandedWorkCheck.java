package io.github.ythibos.edi.application.delivery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.landing.BranchCommit;
import io.github.ythibos.edi.domain.landing.CaptainsCopy;
import io.github.ythibos.edi.domain.landing.LandedWork;
import io.github.ythibos.edi.domain.project.DeliveryMode;

@Component
public class LandedWorkCheck {

    private static final String BRANCHES = "refs/heads/";

    private final Git git;
    private final DeliveryStore deliveries;
    private final TransactionTemplate transaction;

    public LandedWorkCheck(Git git, DeliveryStore deliveries, TransactionTemplate transaction) {
        this.git = git;
        this.deliveries = deliveries;
        this.transaction = transaction;
    }

    public LandedWork of(Workplace workplace) {
        git.fetchOrigin(workplace.project().projectClone());
        return Files.isDirectory(workplace.worktree()) ? ofTheCopy(workplace) : ofTheBranchAlone(workplace);
    }

    public Optional<String> headOf(Workplace workplace) {
        return Files.isDirectory(workplace.worktree())
                ? Optional.of(git.head(workplace.worktree()))
                : git.commitAt(workplace.project().projectClone(), BRANCHES + workplace.dispatch().branch());
    }

    public List<BranchCommit> commitsAfter(Workplace workplace, String mergedHead) {
        git.fetchOrigin(workplace.project().projectClone());
        Path repository = Files.isDirectory(workplace.worktree()) ? workplace.worktree() : workplace.project().projectClone();
        Optional<String> head = headOf(workplace);
        if (head.isEmpty() || git.commitAt(repository, mergedHead).isEmpty()) {
            return List.of();
        }
        return git.commitsBeyond(repository, mergedHead, head.get()).stream().filter(commit -> !commit.sameChangeInTheBase()).toList();
    }

    private LandedWork ofTheCopy(Workplace workplace) {
        Path copy = workplace.worktree();
        String base = workplace.baseRef();
        return new LandedWork(base, git.uncommittedPaths(copy), git.nestedRepositories(copy), git.commitsBeyond(copy, base, "HEAD"),
                commitsUpToTheMergedHead(workplace, copy), git.holdsTheContentOf(copy, base, "HEAD"), captainsCopyOf(workplace, git.head(copy)));
    }

    private Set<String> commitsUpToTheMergedHead(Workplace workplace, Path repository) {
        return mergedHeadOf(workplace, repository).map(head -> git.commitIdsBeyond(repository, workplace.baseRef(), head)).orElse(Set.of());
    }

    private Optional<String> mergedHeadOf(Workplace workplace, Path repository) {
        return transaction.execute(_ -> deliveries.of(workplace.task().id()))
                .filter(Delivery::isLanded)
                .filter(merged -> git.isAncestor(repository, merged.landedCommit(), workplace.baseRef()))
                .map(Delivery::headCommit)
                .filter(head -> git.commitAt(repository, head).isPresent());
    }

    private LandedWork ofTheBranchAlone(Workplace workplace) {
        Path clone = workplace.project().projectClone();
        String base = workplace.baseRef();
        Optional<String> head = git.commitAt(clone, BRANCHES + workplace.dispatch().branch());
        if (head.isEmpty()) {
            return new LandedWork(base, List.of(), List.of(), List.of(), Set.of(), true, CaptainsCopy.NOT_DELIVERED_THERE);
        }
        return new LandedWork(base, List.of(), List.of(), git.commitsBeyond(clone, base, head.get()), commitsUpToTheMergedHead(workplace, clone),
                git.holdsTheContentOf(clone, base, head.get()), captainsCopyOf(workplace, head.get()));
    }

    private CaptainsCopy captainsCopyOf(Workplace workplace, String head) {
        Path checkout = workplace.project().captainCheckout();
        if (workplace.delivery() != DeliveryMode.LOCAL_BRANCH) {
            return CaptainsCopy.NOT_DELIVERED_THERE;
        }
        boolean holdsTheHead = checkout != null && git.commitAt(checkout, BRANCHES + workplace.dispatch().branch()).filter(head::equals).isPresent();
        return holdsTheHead ? CaptainsCopy.HOLDS_THE_HEAD : CaptainsCopy.LACKS_THE_HEAD;
    }
}
