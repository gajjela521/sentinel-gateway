package io.sentinelgateway.core.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of one inbound agent-originated API call.
 *
 * <p>All fields are captured at construction time. Headers and metadata are defensively
 * copied so no external mutation can affect a request already in the pipeline.
 *
 * <p>Required fields: {@code organizationId}, {@code method}, {@code endpoint}.
 * Optional fields: {@code agentId} (may be null for unauthenticated callers),
 * {@code executionThreadId} (null disables budget tracking for this request),
 * {@code body} (null for GET/HEAD/DELETE requests with no body).
 */
public final class SentinelRequest {

    private final String requestId;
    private final String agentId;
    private final String organizationId;
    private final String executionThreadId;
    private final HttpMethod method;
    private final String endpoint;
    private final Map<String, String> headers;
    private final String body;
    private final Instant receivedAt;
    private final Map<String, Object> metadata;

    private SentinelRequest(Builder b) {
        this.requestId         = b.requestId;
        this.agentId           = b.agentId;
        this.organizationId    = b.organizationId;
        this.executionThreadId = b.executionThreadId;
        this.method            = b.method;
        this.endpoint          = b.endpoint;
        this.headers           = Map.copyOf(b.headers);
        this.body              = b.body;
        this.receivedAt        = b.receivedAt;
        this.metadata          = Map.copyOf(b.metadata);
    }

    public String requestId()                { return requestId; }
    /** May be {@code null} for unauthenticated callers. */
    public String agentId()                  { return agentId; }
    public String organizationId()           { return organizationId; }
    /** May be {@code null}; null disables per-thread budget tracking. */
    public String executionThreadId()        { return executionThreadId; }
    public HttpMethod method()               { return method; }
    public String endpoint()                 { return endpoint; }
    public Map<String, String> headers()     { return headers; }
    /** May be {@code null} for requests with no body (GET, HEAD, DELETE). */
    public String body()                     { return body; }
    public Instant receivedAt()              { return receivedAt; }
    public Map<String, Object> metadata()    { return metadata; }

    /**
     * Returns the text representation fed to the embedding model.
     * Format: {@code METHOD /endpoint\nbody} — stable across releases.
     */
    public String toEmbeddingText() {
        String bodyPart = (body != null && !body.isBlank()) ? body : "";
        return method.name() + " " + endpoint + "\n" + bodyPart;
    }

    public static Builder builder() { return new Builder(); }

    /** Fluent builder. All setters return {@code this} for chaining. */
    public static final class Builder {

        private String requestId         = UUID.randomUUID().toString();
        private String agentId;
        private String organizationId;
        private String executionThreadId;
        private HttpMethod method;
        private String endpoint;
        private final Map<String, String> headers  = new HashMap<>();
        private String body;
        private Instant receivedAt       = Instant.now();
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder requestId(String v) {
            if (v == null || v.isBlank())
                throw new IllegalArgumentException("requestId must not be null or blank");
            requestId = v;
            return this;
        }

        /** Agent identifier; null is accepted (anonymous callers). */
        public Builder agentId(String v)           { agentId = v; return this; }

        public Builder organizationId(String v) {
            if (v == null || v.isBlank())
                throw new IllegalArgumentException("organizationId must not be null or blank");
            organizationId = v;
            return this;
        }

        /** Execution thread ID; null disables budget tracking. */
        public Builder executionThreadId(String v) { executionThreadId = v; return this; }

        public Builder method(HttpMethod v) {
            method = Objects.requireNonNull(v, "method must not be null");
            return this;
        }

        public Builder endpoint(String v) {
            if (v == null || v.isBlank())
                throw new IllegalArgumentException("endpoint must not be null or blank");
            endpoint = v;
            return this;
        }

        public Builder header(String k, String v) {
            Objects.requireNonNull(k, "header name must not be null");
            Objects.requireNonNull(v, "header value must not be null");
            headers.put(k, v);
            return this;
        }

        public Builder headers(Map<String, String> m) {
            Objects.requireNonNull(m, "headers map must not be null");
            headers.putAll(m);
            return this;
        }

        /** Request body; null is accepted (bodyless requests). */
        public Builder body(String v)              { body = v; return this; }

        public Builder receivedAt(Instant v) {
            receivedAt = Objects.requireNonNull(v, "receivedAt must not be null");
            return this;
        }

        public Builder metadata(String k, Object v) {
            Objects.requireNonNull(k, "metadata key must not be null");
            Objects.requireNonNull(v, "metadata value must not be null");
            metadata.put(k, v);
            return this;
        }

        /**
         * @throws IllegalStateException if any required field ({@code organizationId}, {@code method},
         *                               {@code endpoint}) is missing
         */
        public SentinelRequest build() {
            if (organizationId == null) throw new IllegalStateException("organizationId is required");
            if (method == null)         throw new IllegalStateException("method is required");
            if (endpoint == null)       throw new IllegalStateException("endpoint is required");
            return new SentinelRequest(this);
        }
    }
}
