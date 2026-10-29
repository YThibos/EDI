package io.github.ythibos.edi.testing;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.browser.CaptainsBrowser;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;

public final class RecordingBrowser extends CaptainsBrowser {

    private static final String NO_BROWSER = "";
    private static final String NO_DESKTOP = "";
    private static final String NO_WSL_DISTRIBUTION = "";

    private final List<URI> opened = new CopyOnWriteArrayList<>();

    public RecordingBrowser(ExternalPrograms programs, EdiHome home) {
        super(programs, home, NO_BROWSER, home.root().resolve("no-windows"), NO_DESKTOP, NO_WSL_DISTRIBUTION);
    }

    @Override
    public boolean open(URI page) {
        opened.add(page);
        return true;
    }

    public List<URI> opened() {
        return List.copyOf(opened);
    }

    public URI lastOpened() {
        return opened.getLast();
    }
}
