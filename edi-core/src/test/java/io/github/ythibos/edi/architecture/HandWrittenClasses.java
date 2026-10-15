package io.github.ythibos.edi.architecture;

import java.util.regex.Pattern;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

final class HandWrittenClasses implements ImportOption {

    private static final Pattern AHEAD_OF_TIME_GENERATED = Pattern.compile(".*/[^/]*__[^/]*\\.class");
    private static final Pattern PROXY_GENERATED = Pattern.compile(".*/[^/]*\\$\\$SpringCGLIB\\$\\$[^/]*\\.class");

    @Override
    public boolean includes(Location location) {
        return !location.matches(AHEAD_OF_TIME_GENERATED) && !location.matches(PROXY_GENERATED);
    }
}
