# Sentinel Gateway

[![CI](https://github.com/gajjela521/sentinel-gateway/actions/workflows/ci.yml/badge.svg)](https://github.com/gajjela521/sentinel-gateway/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)

**AI Agent Security Middleware** — policy enforcement, semantic drift detection, and financial circuit breakers for agentic systems.

Sentinel Gateway sits between your AI agents and downstream APIs, evaluating every request through a 6-layer pipeline before it executes. When an agent drifts off-course, attempts prompt injection, or exceeds its financial budget, Sentinel stops the request and returns a machine-readable rejection that the agent can reason about and self-correct from.

---

## Why Sentinel?

AI agents make API calls autonomously. Without guardrails:

- A coding agent deletes production data it was never meant to touch
- An indirect prompt injection in a fetched document turns your agent into an attacker
- A stuck agent loop burns thousands of dollars in API costs before anyone notices

Sentinel enforces your rules at the infrastructure layer — no agent framework modifications needed.

---

## Architecture

```
  Agent / LLM Framework
          │
          ▼
  ┌───────────────────────────────────────────────────┐
  │              Sentinel Gateway Pipeline            │
  │                                                   │
  │  ┌─────────────────────────────────────────────┐  │
  │  │ 1. Policy Engine        (YAML rules)        │  │
  │  ├─────────────────────────────────────────────┤  │
  │  │ 2. Injection Scanner    (regex + patterns)  │  │
  │  ├─────────────────────────────────────────────┤  │
  │  │ 3. Semantic Drift       (ONNX embeddings)   │  │
  │  ├─────────────────────────────────────────────┤  │
  │  │ 4. Financial CB         (budget + velocity) │  │
  │  ├─────────────────────────────────────────────┤  │
  │  │ 5. Custom Layers        (your extensions)   │  │
  │  └─────────────────────────────────────────────┘  │
  │                                                   │
  │  ALLOW → forward   BLOCK/QUARANTINE → reject      │
  └───────────────────────────────────────────────────┘
          │
          ▼
  Your Downstream API
```

Every request — allowed or blocked — is written to a structured audit log compatible with OpenTelemetry, Splunk, and Datadog.

---

## Quickstart

### Option A: Spring Boot (embedded middleware)

Add the starter to your existing Spring Boot application:

```xml
<dependency>
    <groupId>io.sentinelgateway</groupId>
    <artifactId>sentinel-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Configure via `application.yml`:

```yaml
sentinel:
  enabled: true
  injection-scanner:
    enabled: true
    block-threshold: 0.75
  financial-circuit-breaker:
    enabled: true
    max-spend-usd: 10.0
    max-state-mutations: 5
    velocity-window-ms: 60000
```

Agents must pass three headers on each call:

| Header | Purpose |
|--------|---------|
| `X-Agent-Id` | Identifies the agent |
| `X-Organization-Id` | Determines which policy baseline applies |
| `X-Execution-Thread-Id` | Tracks per-invocation budgets |

Sentinel intercepts all incoming HTTP requests automatically via servlet filter. No code changes needed.

### Option B: Standalone Proxy (no code changes to downstream API)

```bash
# Run locally
java -jar sentinel-proxy.jar \
  --port=8080 \
  --upstream=http://your-api.example.com \
  --policy=./policies.yaml

# Or via Docker Compose (includes Prometheus + Grafana)
docker-compose up
```

---

## Structured Rejection Envelope

When Sentinel blocks a request, it returns a machine-readable JSON envelope — not a raw 403. This lets agents reason about the rejection and self-correct:

```json
{
  "sentinel_decision": "BLOCK",
  "layer": "policy_engine",
  "rule_id": "no-bulk-delete-users",
  "reason": "DELETE operation on /users requires explicit human-approval policy",
  "safe_alternatives": [
    "GET /users/{id}",
    "POST /users/{id}/deactivate"
  ],
  "audit_trace_id": "trace_abc123",
  "agent_guidance": "Request human approval before proceeding with user deletions",
  "scores": {
    "driftScore": 0.0,
    "injectionScore": 0.0,
    "schemaAnomalyScore": 0.0,
    "mutationWeight": 1.0
  }
}
```

HTTP status codes: `403` (BLOCK/QUARANTINE), `451` (REQUIRE_APPROVAL).

---

## Policy Configuration

Policies are YAML-first. Drop a `sentinel-policies.yaml` file and point to it:

```yaml
sentinel:
  policy-file: classpath:sentinel-policies.yaml
```

Example policy:

```yaml
rules:
  - id: no-bulk-delete-users
    description: "DELETE on /users requires human approval"
    methods: [DELETE]
    endpoint_pattern: ".*/users(/.*)?$"
    action: REQUIRE_APPROVAL
    agent_guidance: "Request human approval before bulk deletions"
    safe_alternatives:
      - "GET /users/{id}"
      - "POST /users/{id}/deactivate"
    priority: 10

  - id: block-credentials-access
    description: "Agents cannot access credential endpoints"
    methods: [GET, POST, PUT, PATCH, DELETE]
    endpoint_pattern: ".*/secrets(/.*)?$|.*/credentials(/.*)?$"
    action: BLOCK
    priority: 5
```

Built-in default policies cover: bulk user deletion, billing mutations, credentials endpoints, and admin access.

---

## Semantic Drift Detection

Sentinel learns what "normal" API behavior looks like for your organization and flags requests that deviate. The drift score formula:

```
driftScore = α × (1 - cosineSim) + β × mutationWeight + γ × schemaAnomalyScore
```

Default weights: `α=0.5, β=0.3, γ=0.2`. Requests above the threshold (default `0.65`) are quarantined.

**Enable drift detection** (requires ONNX Runtime + all-MiniLM-L6-v2 model):

```yaml
sentinel:
  semantic-drift:
    enabled: true
    model-path: /path/to/all-MiniLM-L6-v2.onnx
    vocab-path: /path/to/vocab.txt
    threshold: 0.65
    min-baseline-samples: 50
```

Download the model from [HuggingFace](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) and export to ONNX. The model JAR adds ~50 MB — omit `onnxruntime` from the classpath to run in lightweight mode (drift checks skipped).

---

## Financial Circuit Breaker

Each agent execution thread gets an independent budget:

```yaml
sentinel:
  financial-circuit-breaker:
    max-spend-usd: 10.0          # hard cap on downstream API costs
    max-tokens-burned: 50000     # LLM token budget
    max-state-mutations: 5       # max destructive ops in velocity window
    velocity-window-ms: 60000    # 1-minute rolling window
```

The breaker has three states: **CLOSED** (normal), **OPEN** (blocking all requests), **HALF-OPEN** (one probe allowed). It trips to OPEN when any budget is exceeded and resets automatically when the velocity window expires.

Report actual spend after downstream calls complete:

```java
financialCircuitBreakerLayer.recordSpend(executionThreadId, 0.42, 1200);
```

---

## Module Structure

| Module | Description |
|--------|-------------|
| `sentinel-core` | Pure Java library — zero Spring dependency. The complete pipeline, all layers, all models. |
| `sentinel-spring-boot-starter` | One-dependency entry point for Spring Boot apps. Includes auto-configuration + actuator endpoint. |
| `sentinel-spring-boot-autoconfigure` | Spring Boot 3 auto-configuration, servlet filter, `SentinelProperties`. |
| `sentinel-proxy` | Standalone Netty reverse proxy. Fat JAR, zero-dependency deployment. |
| `sentinel-bom` | Bill of materials POM for consistent version management across your project. |

---

## Actuator Endpoint

When using the Spring Boot starter, Sentinel exposes `/actuator/sentinel`:

```json
{
  "status": "UP",
  "timestamp": "2025-05-23T10:00:00Z",
  "pipeline": "active"
}
```

---

## Building from Source

```bash
git clone https://github.com/gajjela521/sentinel-gateway.git
cd sentinel-gateway

# Build and run all tests
mvn install

# Build only the core library (fastest, no Spring)
mvn install -pl sentinel-bom,sentinel-core

# Build the standalone proxy fat JAR
mvn package -pl sentinel-proxy -am
```

**Requirements**: Java 21+, Maven 3.9+

---

## Roadmap

- [ ] React control plane dashboard (policy editor, audit log viewer, live budget gauge)
- [ ] OpenAPI schema diff layer (structural anomaly scoring)
- [ ] OPA (Open Policy Agent) native Rego backend
- [ ] Maven Central publication
- [ ] LangChain4j + Spring AI native integrations
- [ ] Per-org baseline persistence (Redis/PostgreSQL backends)

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). New injection patterns are especially welcome — add them to `InjectionPatternLibrary.java` in `sentinel-core`.

---

## Security

To report a vulnerability, see [SECURITY.md](SECURITY.md). Please do not open a public GitHub issue for security bugs.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
