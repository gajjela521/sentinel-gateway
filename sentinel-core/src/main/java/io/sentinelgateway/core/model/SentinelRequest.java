package io.sentinelgateway.core.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of an inbound agent-originated API call, enriched as it
 * passes through the pipeline layers.
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
        this.requestId = b.requestId;
        this.agentId = b.agentId;
        this.organizationId = b.organizationId;
        this.executionThreadId = b.executionThreadId;
        this.method = b.method;
        this.endpoint = b.endpoint;
        this.headers = Map.copyOf(b.headers);
        this.body = b.body;
        this.receivedAt = b.receivedAt;
        this.metadata = Map.copyOf(b.metadata);
    }

    public String requestId()         { return requestId; }
    public String agentId()           { return agentId; }
    public String organizationId()    { return organizationId; }
    public String executionThreadId() { return executionThreadId; }
    public HttpMethod method()        { return method; }
    public String endpoint()          { return endpoint; }
    public Map<String, String> headers() { return headers; }
    public String body()              { return body; }
    public Instant receivedAt()       { return receivedAt; }
    public Map<String, Object> metadata() { return metadata; }

    /** Serialized text fed to the embedding model. */
    public String toEmbeddingText() {
        return method.name() + " " + endpoint + "\n" + (body != null ? body : "");
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String requestId = UUID.randomUUID().toString();
        private String agentId;
        private String organizationId;
        private String executionThreadId;
        private HttpMethod method;
        private String endpoint;
        private final Map<String, String> headers = new HashMap<>();
        private String body;
        private Instant receivedAt = Instant.now();
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder requestId(String v)         { requestId = v; return this; }
        public Builder agentId(String v)           { agentId = v; return this; }
        public Builder organizationId(String v)    { organizationId = v; return this; }
        public Builder executionThreadId(String v) { executionThreadId = v; return this; }
        public Builder method(HttpMethod v)        { method = v; return this; }
        public Builder endpoint(String v)          { endpoint = v; return this; }
        public Builder header(String k, String v)  { headers.put(k, v); return this; }
        public Builder headers(Map<String, String> m) { headers.putAll(m); return this; }
        public Builder body(String v)              { body = v; return this; }
        public Builder receivedAt(Instant v)       { receivedAt = v; return this; }
        public Builder metadata(String k, Object v) { metadata.put(k, v); return this; }

        public SentinelRequest build() {
            if (method == null)   throw new IllegalStateException("method is required");
            if (endpoint == null) throw new IllegalStateException("endpoint is required");
            if (organizationId == null) throw new IllegalStateException("organizationId is required");
            return new SentinelRequest(this);
        }
    }
}
