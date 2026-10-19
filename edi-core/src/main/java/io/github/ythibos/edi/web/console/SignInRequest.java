package io.github.ythibos.edi.web.console;

import java.util.Optional;

record SignInRequest(String token) {

    Optional<String> presentedToken() {
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(token.strip());
    }
}
