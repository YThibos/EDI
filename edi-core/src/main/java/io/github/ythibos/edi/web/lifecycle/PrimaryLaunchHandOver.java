package io.github.ythibos.edi.web.lifecycle;

import io.github.ythibos.edi.application.primary.PrimaryLaunch;

record PrimaryLaunchHandOver(PrimaryLaunch launch, String launchKey) {
}
