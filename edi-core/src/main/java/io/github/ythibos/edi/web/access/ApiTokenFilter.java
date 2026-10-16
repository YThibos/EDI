package io.github.ythibos.edi.web.access;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.ythibos.edi.application.worker.WorkerTokens;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class ApiTokenFilter extends OncePerRequestFilter {

    private static final String API = "/api";
    private static final String HEALTH = "/api/health";
    private static final String BEARER = "Bearer ";
    private static final String REFUSAL = "This call needs the EDI token from state/core.token in the EDI home, or the token EDI gave a worker at launch.";

    private final ApiToken token;
    private final WorkerTokens workerTokens;

    ApiTokenFilter(ApiToken token, WorkerTokens workerTokens) {
        this.token = token;
        this.workerTokens = workerTokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = decodedPathOf(request);
        return !isApi(path) || path.equals(HEALTH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Caller> caller = callerHolding(bearerTokenOf(request));
        if (caller.isPresent()) {
            request.setAttribute(Caller.ATTRIBUTE, caller.get());
            chain.doFilter(request, response);
        } else {
            refuse(response);
        }
    }

    private Optional<Caller> callerHolding(String presented) {
        if (presented == null) {
            return Optional.empty();
        }
        if (token.admits(presented)) {
            return Optional.of(new Caller.Primary());
        }
        return workerTokens.workerHolding(presented).map(worker -> new Caller.WorkerCaller(worker.id()));
    }

    private static String decodedPathOf(HttpServletRequest request) {
        return request.getServletPath() + Objects.requireNonNullElse(request.getPathInfo(), "");
    }

    private static boolean isApi(String path) {
        return path.equals(API) || path.startsWith(API + "/");
    }

    private static String bearerTokenOf(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith(BEARER) ? authorization.substring(BEARER.length()) : null;
    }

    private static void refuse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().println(REFUSAL);
    }
}
