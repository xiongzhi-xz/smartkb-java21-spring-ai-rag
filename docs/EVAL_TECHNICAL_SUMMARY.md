# Eval Technical Summary

## 30-second Version

SmartKB v2 uses TicketRush as a real Java project sample to evaluate whether the Agent can work in an engineering context, not just answer knowledge-base questions. Each eval run records the case id, status, score, verification commands, evidence paths, duration, tool calls, and human interventions. The report API then aggregates success rate, score rate, failure reasons, and latest case status, so I can explain both what worked and what failed without beautifying the result.

## 2-minute Version

The problem I wanted to solve is that Agent demos often look convincing in a chat window but are hard to verify. For Java maintenance work, I need to know whether the Agent can read the project state, identify evidence, make a bounded change, run checks, and record the result.

So I added an eval layer around the existing Agent platform. TicketRush is the sample project because it has high-concurrency ticketing flows, Redis Lua and locks, MySQL optimistic locking, RocketMQ async ordering, Sentinel, Docker Compose, Prometheus, Grafana, and unfinished k6 benchmark tasks. That gives the eval cases enough real-world complexity.

Each run is recorded as structured data. The important fields are not only pass or fail. I also record the verification commands, evidence paths, duration, tool calls, human intervention count, and failure reason. This lets me distinguish "the Agent completed the task independently" from "the Agent needed manual correction" or "the Agent produced a partial answer but lacked proof."

The report API aggregates those records into success rate, score rate, failed/partial/passed counts, failure reason distribution, and the latest status per case. The frontend panel makes the workflow visible: add a run, inspect run history, and view the report immediately.

The key point is that eval makes Agent work measurable. It turns "the answer looked good" into "this task passed these checks with these evidence files and this amount of human intervention."

## What To Emphasize

- Use real projects, not toy prompts.
- Record evidence paths and commands, not only model output.
- Count human interventions honestly.
- Keep partial and failed cases visible.
- Use aggregate reports to decide the next engineering investment.

## Expected Follow-up Questions

### Why not just rely on tests?

Tests only prove the code path that was executed. Agent eval also records whether the Agent found the right files, followed the workflow, avoided unrelated changes, and produced evidence. The two are complementary: tests verify software behavior; eval verifies the Agent workflow.

### Why use TicketRush?

TicketRush has concurrency, messaging, rate limiting, Docker Compose, monitoring, and real unfinished performance work. It is large enough to expose context and verification problems, but small enough to run locally.

### What does human intervention count mean?

It is the number of times a human had to redirect, correct, or unblock the Agent. A passed case with many interventions is not as strong as a passed case with zero interventions.

### What is the next improvement?

Persist eval runs instead of keeping them in memory, then seed the report from the existing `docs/agent-eval-report.md` so historical manual results and new API-recorded runs can be compared.
