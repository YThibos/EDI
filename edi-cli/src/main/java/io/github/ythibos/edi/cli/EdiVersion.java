package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

final class EdiVersion implements IVersionProvider {

    @Override
    public String[] getVersion() throws IOException {
        try (InputStream build = EdiVersion.class.getResourceAsStream("/edi-version.properties")) {
            Properties version = new Properties();
            version.load(build);
            return new String[] {"edi " + version.getProperty("version")};
        }
    }
}
