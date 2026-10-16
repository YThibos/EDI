# ADR-089: Work is landed when nothing is uncommitted and every commit is in the base by ancestry, patch or content

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-12: cleanup refuses while any commit or file is not provably landed; only the captain's explicit word discards.
Section 11.8: the worktree has no uncommitted changes and no untracked files beyond EDI's own ignored state; every commit on the branch is landed by ancestry of `origin/<base>`, by patch-id equivalence (rebuilt or rebased by the pipeline), or by content equality, where `git merge-tree --write-tree origin/<base> <branch>` yields the base's own tree (squash merges); for `local-branch` the branch head is present in the captain's checkout.
AT-12: not landed for an uncommitted change, an unpushed commit and an unmerged branch; landed for a squash-merged or pipeline-rebuilt branch.
U-12: a ship's copy with an untracked nested repository refuses cleanup as unlanded material.

**Decision:** `LandedWorkCheck` fetches the clone's origin and gathers the facts with git: the copy's uncommitted and untracked paths from `git status --porcelain` with every untracked file listed, nested repositories as the untracked entries git shows as directories, the commits `git cherry -v origin/<base> HEAD` lists with whether each has the same patch on the base, whether `merge-tree` of the base and the head gives the base's tree, and, for `local-branch`, whether the captain's checkout holds the head on the task's branch.
When the copy is gone, the branch in the clone stands in for it, and with neither left nothing local remains to lose.
`LandedWork` in the domain decides: landed exactly when nothing is uncommitted, no repository is nested, no commit is outside the base by all three tests, and the captain's checkout holds the head where it must; each failure is a finding that names the path, the repository or the commit's short id and subject.

**Motivation:** The rule that protects the captain's unlanded work is a few lines of domain code, tested in milliseconds, while git gathers the facts it judges.

**Alternatives considered:**
- Ancestry alone - every squash merge and every pipeline rebuild would look unlanded.
- Asking the forge whether the merge request was merged - a merged request can still leave unpushed commits behind in the copy.

**Consequences:**
- Good: AT-12 runs every case on real repositories, including a rebuilt commit whose content the base later changed again, landed by its patch alone.
- Bad: a branch whose change the base later reverted counts as landed by its patch; the revert is the base's own history.

**Revisit when:** A delivery mode lands work without its commits or its content reaching the base.

**Sources:** EDI rewrite specification, section 11.8, I-12 and U-12 (AT-12); `domain.landing.LandedWork`, `BranchCommit`, `CaptainsCopy`, `application.delivery.LandedWorkCheck`, `adapter.git.Git.uncommittedPaths`, `nestedRepositories`, `commitsBeyond`, `holdsTheContentOf`.
