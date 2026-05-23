# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.1.x (SNAPSHOT) | Yes — active development |

Once stable releases begin, only the latest minor version will receive security fixes.

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please report security bugs by emailing:

**gajjelasuryateja2021@gmail.com**

Include in your report:
- A description of the vulnerability
- Steps to reproduce or a proof-of-concept
- The affected module(s) and version
- Potential impact assessment

### Response SLA

| Action | Target |
|--------|--------|
| Acknowledgement | Within 48 hours |
| Initial assessment | Within 5 business days |
| Fix or mitigation | Within 30 days for critical issues |
| Public disclosure | Coordinated with reporter after fix is available |

We follow responsible disclosure. If you discover a vulnerability and report it privately, we will credit you in the release notes (unless you prefer anonymity).

## Scope

Security bugs in this project include:

- Bypass of the policy engine or injection scanner (an agent request that should be blocked but is allowed)
- Memory corruption or JVM crash via malformed input
- Information disclosure in audit logs (e.g., secrets appearing in log output)
- Race conditions in the financial circuit breaker that could allow budget overrun
- Arbitrary code execution via the ONNX model loading path

Out of scope:
- Vulnerabilities in third-party dependencies (report those to the upstream project and we will upgrade)
- Issues that require physical access to the deployment host
- Social engineering of maintainers

## Security Design Notes

Sentinel is itself a security component. Key design decisions relevant to operators:

- The `PromptInjectionLayer` uses regex — it is not a complete defense on its own; use it as a layer in a defense-in-depth strategy
- ONNX model files are loaded from a path you control; ensure that path is not writable by untrusted processes
- Audit logs may contain request bodies; ensure your log pipeline is treated as sensitive
- The financial circuit breaker is per-JVM-process; in multi-instance deployments, budgets are not coordinated across instances
