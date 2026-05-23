package io.sentinelgateway.spring.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinelgateway.core.model.HttpMethod;
import io.sentinelgateway.core.model.SentinelDecision;
import io.sentinelgateway.core.model.SentinelRequest;
import io.sentinelgateway.core.pipeline.SentinelPipeline;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servlet filter that intercepts every inbound request and runs it through
 * the Sentinel pipeline before passing to the application.
 *
 * On BLOCK/QUARANTINE → responds with 403 + machine-readable JSON envelope.
 * On REQUIRE_APPROVAL → responds with 451 (Unavailable For Legal Reasons — used here as "pending approval").
 * On ALLOW → passes through to the next filter / servlet.
 */
public final class SentinelFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SentinelFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final SentinelPipeline pipeline;

    public SentinelFilter(SentinelPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq = (HttpServletRequest)  req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        SentinelRequest sentinelRequest = buildSentinelRequest(httpReq);
        SentinelDecision decision = pipeline.evaluate(sentinelRequest);

        if (decision.isAllowed()) {
            chain.doFilter(req, res);
            return;
        }

        int statusCode = switch (decision.sentinelDecision()) {
            case BLOCK            -> 403;
            case QUARANTINE       -> 403;
            case REQUIRE_APPROVAL -> 451;
            case ALLOW            -> 200; // unreachable
        };

        httpRes.setStatus(statusCode);
        httpRes.setContentType("application/json");
        httpRes.setCharacterEncoding("UTF-8");
        byte[] body = MAPPER.writeValueAsBytes(decision);
        httpRes.setContentLength(body.length);
        httpRes.getOutputStream().write(body);

        log.info("Sentinel {} request {} → {} (rule={})",
                decision.sentinelDecision(), sentinelRequest.requestId(),
                sentinelRequest.endpoint(), decision.ruleId());
    }

    private SentinelRequest buildSentinelRequest(HttpServletRequest req) throws IOException {
        Map<String, String> headers = Collections.list(req.getHeaderNames()).stream()
                .collect(Collectors.toMap(h -> h, req::getHeader, (a, b) -> a));

        String body = req.getReader().lines().collect(Collectors.joining("\n"));

        return SentinelRequest.builder()
                .agentId(req.getHeader("X-Agent-Id"))
                .organizationId(resolveOrgId(req))
                .executionThreadId(req.getHeader("X-Execution-Thread-Id"))
                .method(HttpMethod.valueOf(req.getMethod().toUpperCase()))
                .endpoint(req.getRequestURI())
                .headers(headers)
                .body(body.isBlank() ? null : body)
                .build();
    }

    private String resolveOrgId(HttpServletRequest req) {
        String orgId = req.getHeader("X-Organization-Id");
        return orgId != null ? orgId : "default";
    }
}
