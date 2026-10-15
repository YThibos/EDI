package io.github.ythibos.edi.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

final class HandWrittenClasses implements ImportOption {

    private static final String AHEAD_OF_TIME_GENERATED = "__";
    private static final String PROXY_GENERATED = "$$SpringCGLIB$$";

    @Override
    public boolean includes(Location location) {
        String classFile = classFileName(location);
        return !classFile.contains(AHEAD_OF_TIME_GENERATED) && !classFile.contains(PROXY_GENERATED);
    }

    private static String classFileName(Location location) {
        String path = location.asURI().toString();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
