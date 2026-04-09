English | [简体中文](FINDINGS.zh-CN.md)

# Findings

## What we observed

The local demo checks endpoints returning 200, 500 and TIMEOUT and prints total elapsed time. The checker supports sequential, fixed-thread, virtual-thread and asynchronous modes, with per-request and whole-run time budgets. The virtual mode bounds in-flight requests with a semaphore; results are sorted by latency.

## Backend decision

On-call can inspect several endpoints before an on-sale without letting one slow endpoint hold the whole run indefinitely. This is evidence for bounded concurrent checks and deadline design, not a measurement of production capacity. See [backend ADR 0003 — Server execution strategy](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0003-server-execution-strategy.md).
