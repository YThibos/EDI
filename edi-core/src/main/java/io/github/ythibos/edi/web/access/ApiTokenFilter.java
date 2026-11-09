package io.github.ythibos.edi.web.access;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.ythibos.edi.application.access.ConsoleAccess;
import io.github.ythibos.edi.application.primary.PrimarySessions;
import io.github.ythibos.edi.application.worker.WorkerTokens;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleOutcome;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class ApiTokenFilter extends OncePerRequestFilter {

    private static final String API = "/api";
    private static final Set<String> OPEN_PATHS = Set.of("/api/health", "/api/console/session");
    private static final Set<String> READING_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String BEARER = "Bearer ";
    private static final String SESSION = "EDI-Session";
    private static final String REFUSAL = "This call needs the EDI token from state/core.token in the EDI home, the token a worker got at launch, "
            + "or a console session opened through edi console.";
    private static final String CONSOLE_HEADER_REFUSAL = "A console request that changes the fleet carries the console's own header, "
            + ConsoleSessionCookie.HEADER + ".";

    private final ApiToken token;
    private final WorkerTokens workerTokens;
    private final PrimarySessions primarySessions;
    private final ConsoleAccess consoleAccess;
    private final CallerFirings firings;

    ApiTokenFilter(ApiToken token, WorkerTokens workerTokens, PrimarySessions primarySessions, ConsoleAccess consoleAccess, CallerFirings firings) {
        this.token = token;
        this.workerTokens = workerTokens;
        this.primarySessions = primarySessions;
        this.consoleAccess = consoleAccess;
        this.firings = firings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = decodedPathOf(request);
        return !isApi(path) || OPEN_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bearer = bearerTokenOf(request);
        Optional<Caller> caller = bearer == null ? captainSignedInBy(request) : callerHolding(bearer, sessionOf(request));
        if (caller.isEmpty()) {
            firings.refused(Rule.API_TOKEN, request, REFUSAL);
            refuse(response, HttpStatus.UNAUTHORIZED, REFUSAL);
        } else if (isAConsoleChangeWithoutItsHeader(caller.get(), request)) {
            firings.fired(Rule.CONSOLE_HEADER, RuleOutcome.REFUSED, caller.get(), CONSOLE_HEADER_REFUSAL);
            refuse(response, HttpStatus.FORBIDDEN, CONSOLE_HEADER_REFUSAL);
        } else {
            request.setAttribute(Caller.ATTRIBUTE, caller.get());
            chain.doFilter(request, response);
        }
    }

    private Optional<Caller> callerHolding(String presented, UUID session) {
        if (token.admits(presented)) {
            return Optional.of(Caller.holdingTheHomeToken(session, standingOf(session)));
        }
        return workerTokens.workerHolding(presented).map(worker -> new Caller.WorkerCaller(worker.id()));
    }

    private Optional<Caller> captainSignedInBy(HttpServletRequest request) {
        return ConsoleSessionCookie.presentedIn(request).filter(consoleAccess::admits).map(_ -> new Caller.Captain());
    }

    private static boolean isAConsoleChangeWithoutItsHeader(Caller caller, HttpServletRequest request) {
        return caller instanceof Caller.Captain && !READING_METHODS.contains(request.getMethod()) && !ConsoleSessionCookie.headerCarriedBy(request);
    }

    private Standing standingOf(UUID session) {
        return session == null ? Standing.UNCLAIMED : primarySessions.standingOf(session);
    }

    private static String decodedPathOf(HttpServletRequest request) {
        return request.getServletPath() + Objects.requireNonNullElse(request.getPathInfo(), "");
    }

    private static boolean isApi(String path) {
        return path.equals(API) || path.startsWith(API + "/");
    }

    private static UUID sessionOf(HttpServletRequest request) {
        String session = request.getHeader(SESSION);
        try {
            return session == null || session.isBlank() ? null : UUID.fromString(session.strip());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static String bearerTokenOf(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith(BEARER) ? authorization.substring(BEARER.length()) : null;
    }

    private static void refuse(HttpServletResponse response, HttpStatus status, String refusal) throws IOException {
        response.setStatus(status.value());
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().println(refusal);
    }
}
