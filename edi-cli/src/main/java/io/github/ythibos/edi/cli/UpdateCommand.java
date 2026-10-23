package io.github.ythibos.edi.cli;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "update", description = "Pulls the EDI repository, builds and tests it natively, installs it, restarts the core and names the anchor to"
        + " re-read; running workers keep running.")
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

    private void report(EdiCheckout checkout) {
        PrintWriter out = spec.commandLine().getOut();
        out.println("EDI is updated to " + checkout.head() + " and its core runs the new build.");
        out.println("Re-read " + checkout.primaryAnchor() + ": the primary's rules may have changed, and a new session loads them.");
        out.flush();
    }
}
