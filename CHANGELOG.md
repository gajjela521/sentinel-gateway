# Changelog

All notable changes to Sentinel Gateway are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

## [0.1.0-SNAPSHOT] — 2025-05-23

### Added

**sentinel-core**
- `SentinelPipeline` — chainable 6-layer processing pipeline with short-circuit on first terminal decision
- `SentinelPipelineBuilder` — fluent builder for assembling the pipeline with sensible defaults
- `SentinelRequest` / `SentinelDecision` / `LayerDecision` — immutable request/response models
- `AuditEvent` — OpenTelemetry-compatible audit record emitted for every request
- `PolicyEngine` — YAML-first rule evaluation (ALLOW / BLOCK / QUARANTINE / REQUIRE_APPROVAL)
- `PolicyLoader` — parses `sentinel-policies.yaml`; built-in defaults for bulk-delete, billing, credentials, admin endpoints
- `PromptInjectionLayer` — 10 built-in patterns covering: DAN jailbreaks, role override, system prompt extraction, delimiter escaping, token smuggling, exfiltration URLs, privilege escalation, resource exhaustion loops
- `SemanticDriftLayer` — per-org cosine similarity baseline using Welford's online algorithm; drift score = α*(1−sim) + β*mutationWeight + γ*schemaAnomaly
- `OrgBaseline` — thread-safe incremental centroid with ReentrantReadWriteLock
- `OnnxEmbeddingModel` — all-MiniLM-L6-v2 via ONNX Runtime Java binding with mean-pooling and L2 normalization
- `SimpleTokenizer` — WordPiece tokenizer for BERT-family models
- `NoOpEmbeddingModel` — stub for lightweight deployments (skips drift check)
- `FinancialCircuitBreakerLayer` — three-state circuit breaker (CLOSED/OPEN/HALF_OPEN) per execution thread
- `ExecutionBudget` — atomic per-thread spend/token/mutation tracking with `recordSpend()` API
- `BudgetConfig` — presets: `defaults()`, `strict()`, `permissive()`
- `PipelineContext` — per-request mutable context bag for sharing computed artefacts between layers
- 20+ unit tests across pipeline, policy, injection, financial, and semantic modules

**sentinel-spring-boot**
- `SentinelAutoConfiguration` — zero-code Spring Boot 3 auto-configuration
- `SentinelFilter` — servlet filter intercepting all HTTP requests; returns structured JSON rejection envelope
- `SentinelProperties` — full `sentinel.*` configuration namespace bound from `application.yml`
- `@EnableSentinel` — explicit opt-in annotation
- `SentinelActuatorEndpoint` — `/actuator/sentinel` health/status endpoint

**sentinel-proxy**
- Standalone Netty HTTP reverse proxy with `--port`, `--upstream`, `--policy` CLI args
- Maven Shade fat JAR for zero-dependency deployment
- Dockerfile (eclipse-temurin:21-jre-alpine base)
- `docker-compose.yml` with Prometheus + Grafana observability stack

**sentinel-bom**
- Bill of Materials POM for consistent dependency version management
