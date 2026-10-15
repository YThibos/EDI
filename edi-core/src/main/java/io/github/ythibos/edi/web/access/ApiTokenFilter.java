package io.github.ythibos.edi.web.access;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class ApiTokenFilter extends OncePerRequestFilter {

    private static final String API = "/api/";
    private static final String HEALTH = "/api/health";
    private static final String BEARER = "Bearer ";
    private static final String REFUSAL = "This call needs the EDI token from state/core.token in the EDI home.";

    private final ApiToken token;

    ApiTokenFilter(ApiToken token) {
        this.token = token;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith(API) || path.equals(HEALTH) || path.startsWith(HEALTH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token.admits(bearerTokenOf(request))) {
            chain.doFilter(request, response);
        } else {
            refuse(response);
        }
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
