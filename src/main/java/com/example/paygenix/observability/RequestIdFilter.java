package com.example.paygenix.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an id: the one nginx set in {@code X-Request-Id} when it is well formed, else a new UUID. It is
 * put in the log MDC as {@code requestId} (printed by {@code logging.pattern.correlation}) and echoed in the response
 * header, so a user's report, the nginx access log and the API logs can be matched. Runs first, so every log line of
 * the request (security included) carries it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    // nginx's $request_id is 32 hex characters; anything else a client made up (spaces, CR/LF, very long) is replaced
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    // error dispatches run on the same thread inside doFilter, so they keep the id; async dispatches get it again
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
