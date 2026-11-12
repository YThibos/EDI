package io.github.ythibos.edi.web.access;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.ythibos.edi.domain.rule.Rule;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class LoopbackHostFilter extends OncePerRequestFilter {

    private static final List<String> LOOPBACK_NAMES = List.of("127.0.0.1", "localhost");
    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String NO_FRAME_ANCESTORS = "frame-ancestors 'none'";
    private static final String FRAME_OPTIONS = "X-Frame-Options";
    private static final String DENY = "DENY";

    private final CallerFirings firings;

    LoopbackHostFilter(CallerFirings firings) {
        this.firings = firings;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        forbidFraming(response);
        if (isAddressedToThisCore(request)) {
            chain.doFilter(request, response);
        } else {
            refuse(request, response);
        }
    }

    private static void forbidFraming(HttpServletResponse response) {
        response.setHeader(CONTENT_SECURITY_POLICY, NO_FRAME_ANCESTORS);
        response.setHeader(FRAME_OPTIONS, DENY);
    }

    private static boolean isAddressedToThisCore(HttpServletRequest request) {
        String host = request.getHeader(HttpHeaders.HOST);
        return host != null && hostsOf(request.getLocalPort()).contains(host.strip().toLowerCase(Locale.ROOT));
    }

    private static List<String> hostsOf(int port) {
        return LOOPBACK_NAMES.stream().map(name -> name + ":" + port).toList();
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String refusal = "This core answers only a request addressed to " + String.join(" or ", hostsOf(request.getLocalPort())) + ".";
        firings.turnedAway(Rule.LOOPBACK_HOST, request, refusal);
        response.setStatus(HttpStatus.MISDIRECTED_REQUEST.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().println(refusal);
    }
}
