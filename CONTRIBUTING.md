# Contributing to Sentinel Gateway

Thank you for your interest in contributing. This document covers how to build, test, and submit changes.

## Building Locally

Requirements: **Java 21+**, **Maven 3.9+**

```bash
git clone https://github.com/gajjela521/sentinel-gateway.git
cd sentinel-gateway

# Full build with all tests
mvn install

# Core library only (fast, no Spring, recommended for most contributions)
mvn install -pl sentinel-bom,sentinel-core

# Run only core tests
mvn test -pl sentinel-core
```

## Branch Naming

| Prefix | Use for |
|--------|---------|
| `feat/` | New feature or layer |
| `fix/` | Bug fix |
| `docs/` | Documentation only |
| `refactor/` | Refactoring (no behavior change) |
| `test/` | Adding or fixing tests |

Example: `feat/opa-rego-backend`, `fix/circuit-breaker-half-open`

## Pull Request Process

1. Fork the repo and create your branch from `main`
2. Make your changes
3. Add or update tests — every new layer must have a corresponding test class
4. Ensure `mvn test -pl sentinel-core` passes locally
5. Fill out the PR template completely
6. Link any related issues in the PR description

## Where to Contribute

### Adding Injection Detection Patterns

The most common contribution. Add new `InjectionPattern` entries to:

```
sentinel-core/src/main/java/io/sentinelgateway/core/injection/InjectionPatternLibrary.java
```

Pattern format:
```java
pattern("your-pattern-id",
        "Short human-readable description",
        "(?i)your_regex_here",
        0.85)  // severity: 0.0–1.0
```

Include a test case in `PromptInjectionLayerTest.java` demonstrating both a match and a non-match.

### Adding a New Pipeline Layer

1. Implement `SentinelLayer` in `sentinel-core/src/main/java/io/sentinelgateway/core/`
2. Add your layer to `SentinelPipelineBuilder` as an optional, disabled-by-default step
3. Store any computed scores in `PipelineContext` using a namespaced key constant
4. Write a focused unit test class for the layer

### Fixing a Bug

1. Write a failing test that reproduces the bug first
2. Fix the bug
3. Confirm the test now passes

## Code Style

- No comments unless the **why** is non-obvious (a hidden constraint, a workaround, a subtle invariant)
- No multi-line docstrings — one short line max
- No unused code, backwards-compat shims, or `_unused` variables
- Records and sealed interfaces preferred for immutable data
- All mutable shared state must be thread-safe (virtual threads are used throughout)

## Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(injection): add SSRF pattern detection
fix(circuit-breaker): half-open probe not resetting window correctly
docs: update semantic drift threshold guidance in README
```

## Reporting Issues

- **Security vulnerabilities** → see [SECURITY.md](SECURITY.md), do not open a public issue
- **Bugs** → use the Bug Report issue template
- **Feature requests** → use the Feature Request issue template
