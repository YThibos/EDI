package io.github.ythibos.edi.web.access;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.ResponseCookie;

import io.github.ythibos.edi.domain.console.ConsoleSession;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class ConsoleSessionCookie {

    public static final String HEADER = "X-EDI-Console";
    private static final String NAME_PREFIX = "EDI-Console-";
    private static final String HEADER_VALUE = "1";

    private ConsoleSessionCookie() {
    }

    public static String nameServedOn(int port) {
        return NAME_PREFIX + port;
    }

    public static String issued(String session, int port) {
        return ResponseCookie.from(nameServedOn(port), session).path("/").maxAge(ConsoleSession.LIFETIME).httpOnly(true).sameSite("Strict").build().toString();
    }

    public static boolean headerCarriedBy(HttpServletRequest request) {
        return HEADER_VALUE.equals(request.getHeader(HEADER));
    }

    static Optional<String> presentedIn(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String name = nameServedOn(request.getLocalPort());
        return cookies == null
                ? Optional.empty()
                : Arrays.stream(cookies).filter(cookie -> name.equals(cookie.getName())).map(Cookie::getValue).filter(value -> !value.isBlank()).findFirst();
    }
}
