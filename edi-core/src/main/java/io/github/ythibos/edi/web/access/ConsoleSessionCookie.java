package io.github.ythibos.edi.web.access;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.ResponseCookie;

import io.github.ythibos.edi.domain.console.ConsoleSession;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class ConsoleSessionCookie {

    public static final String NAME = "EDI-Console";
    public static final String HEADER = "X-EDI-Console";
    private static final String HEADER_VALUE = "1";

    private ConsoleSessionCookie() {
    }

    public static String issued(String session) {
        return ResponseCookie.from(NAME, session).path("/").maxAge(ConsoleSession.LIFETIME).httpOnly(true).sameSite("Strict").build().toString();
    }

    public static boolean headerCarriedBy(HttpServletRequest request) {
        return HEADER_VALUE.equals(request.getHeader(HEADER));
    }

    static Optional<String> presentedIn(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies == null
                ? Optional.empty()
                : Arrays.stream(cookies).filter(cookie -> NAME.equals(cookie.getName())).map(Cookie::getValue).filter(value -> !value.isBlank()).findFirst();
    }
}
