package io.github.ythibos.edi.cli;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "update", description = "Pulls the EDI repository, builds and tests it natively, installs it, restarts the core, refreshes the"
        + " primary's anchor and names it to re-read; running workers keep running.")
final class UpdateCommand implements Callable<Integer> {

    private static final int REFUSED = 1;
    private static final String NO_CORE_RUNS = "The new build is installed but no EDI core runs it; once the old core has exited, edi up --core-only starts it.";

    @ParentCommand
    private Edi edi;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            EdiCheckout checkout = new EdiCheckout(edi.checkout());
            checkout.requireACleanClone();
            checkout.pullTheLatest();
            checkout.buildTestAndInstall(edi.home());
            restartTheCore();
            refreshThePrimarysAnchor();
            report(checkout);
            return 0;
        } catch (CommandFailure | CoreUnavailable failed) {
            spec.commandLine().getErr().println(failed.getMessage());
            return REFUSED;
        }
    }

    private void restartTheCore() {
        LocalCore core = new LocalCore(edi.home(), edi.coreExecutable());
        if (core.health() == CoreHealth.UP) {
            askTheCoreToStop();
        }
        try {
            core.awaitItsStop();
            core.start();
        } catch (CommandFailure noCoreRuns) {
            throw new CommandFailure(noCoreRuns.getMessage() + "\n" + NO_CORE_RUNS);
        }
    }

    private void askTheCoreToStop() {
        CoreReply stopping = new CoreClient().post(edi.core(), "/api/core/stop", new JsonBody().with("restart", true).text(), "text/plain");
        if (!stopping.succeeded()) {
            throw new CommandFailure(stopping.body().strip());
        }
    }

    private void refreshThePrimarysAnchor() {
        CoreReply refreshed = new CoreClient().post(edi.core(), "/api/primary/launch", "{}", "text/plain");
        if (!refreshed.succeeded()) {
            throw new CommandFailure(refreshed.body().strip());
        }
    }

    private void report(EdiCheckout checkout) {
        PrintWriter out = spec.commandLine().getOut();
        out.println("Updated to " + checkout.head() + "; the core runs the new build.");
        out.println("The primary's output style now carries the new anchor, and the running primary loads it at its next compaction or start;"
                + " until then, re-read " + checkout.primaryAnchor() + ", for the primary's rules may have changed.");
        out.flush();
    }
}
