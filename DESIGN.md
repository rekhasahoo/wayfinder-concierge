# Wayfinder: Design

## Goal
Build an agentic travel concierge on synthetic data that is reliable by design: deterministic
services own correctness, MCP standardises the tools, and an open-source model orchestrates.
Everything runs locally or on free tiers.

## Objectives
1. Correct booking logic: sold-out, double-cancel and wrong-date cases are tested, and the
   database can never hold negative inventory.
2. Proactive cab offers: the gap detector returns the exact expected offers for every leg combination.
3. MCP architecture: tools are exposed over MCP and verified in the MCP Inspector before any LLM uses them.
4. Working agent: the local model completes multi-step requests end to end.
5. Grounded answers: menu and policy questions are answered from retrieved documents, with citations.
6. Measured quality: 50+ eval cases report tool-call accuracy, task success and hallucination rate.
7. Own model: a fine-tuned open-source model is compared with its base model on a held-out set.
8. Showcase: deployed demo, README with architecture and results, 2-minute video.

## Scope
**In:** trains, flights, hotels, restaurant tables and cabs across 6 cities (Chennai, Bengaluru,
Mumbai, Delhi, Hyderabad, Kolkata); 3 days of schedules; chat UI with a tool-trace view.

**Out (non-goals):** real payments, real travel APIs, user authentication, multi-currency,
seat maps, mobile app.

## Synthetic data
Seeded generator (seed 42) with a fixed start date (2026-11-22). Per city: 4 hotels with
2 room types each, 3 restaurants, 1 airport, 1 station, 1 home location and a cab fleet.
Two flights and two trains per day for every city pair. Zero-availability rows are included on
purpose, so sold-out paths are always exercised.

## Fixed decisions
- One time zone (IST) everywhere, stored as ISO-8601 strings.
- One currency (INR), stored as integers.
- Every booking is a "leg" with start, end, origin and destination, so gap detection is one
  loop over sorted legs with no per-type special cases.
- SQLite is the source of truth, and its CHECK constraints are a second line of defence.
- Business rules live in `core` as plain Java with no LLM dependency. The model never computes them.
- All state-changing actions (book, cancel) require an explicit confirmation step, enforced in code.
- Model access goes through one interface so models are swappable (Ollama first, hosted free tiers as fallback).
- Java owns services, MCP and the agent. Python owns training data, fine-tuning and evals.

## Architecture
Chat UI -> agent (Spring AI, MCP client, router) -> Ollama. The agent calls an MCP server that
wraps the core services. A vector store holds menus and policies for RAG.
The router first selects a domain, then exposes only that domain's few tools, because small
local models handle short tool lists far more reliably.

## Evaluation and fine-tuning
- The eval set is written before fine-tuning and is never used for training.
- Training data is split by scenario into train, validation and test, so paraphrases of one
  scenario never appear on both sides of the split.
- Results are reported for the base model, the fine-tuned model and a hosted model.
  Negative or small gains are reported honestly.

## Risks
| Risk | Mitigation |
|---|---|
| Small local model is unreliable at tool calls | Router, small tool sets, argument validation with one retry |
| Free GPU limits interrupt training | Small base model, QLoRA, frequent checkpoints |
| Fine-tuning shows no gain | Report it honestly |
| Test data leaks into training | Split by scenario |
| Scope creep | Check every new idea against the objectives |
| Fast-moving libraries (Spring AI, MCP) | Pin versions, record them in the README |

## Definition of done
All objectives checked off, README has an architecture diagram and a results table with real
numbers, and someone can clone the repo and run the demo in under 15 minutes.