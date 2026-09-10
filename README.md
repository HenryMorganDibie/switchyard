# switchyard

Production-oriented ISO 8583 payment switch reference implementation, built with Java 21 and
Spring Boot 4.

**Status: bootstrap only.** This README will be filled in as each milestone below is actually
implemented and verified — nothing is documented here until it's been run and tested. See
`docs/production-hardening.md` (once written) for the full "implemented here" vs. "required for
real production" split.

## What this project is

A modular-monolith simulation of a card-payment switch: an ATM/POS simulator sends ISO 8583
messages over a real TCP connection to a Netty-based gateway, which decodes, validates, routes,
and processes them against simulated issuer/acquirer participants, backed by PostgreSQL, Redis,
and Kafka. It does not connect to any real bank, card scheme, or live payment network.

**This is a portfolio / learning artifact, not a certified payment processor.** It is not
PCI-DSS certified and does not implement real card-scheme certification. See `SECURITY.md`.

## Build status

- [ ] Milestone A — ISO 8583 codec, TCP gateway, transaction pipeline, golden-path 0200→0210
      over TCP, verified end-to-end
- [ ] Milestone B — failure simulation, idempotency, reversals, network management
- [ ] Milestone C — Kafka events, observability, security controls
- [ ] Milestone D — admin/simulation REST API, CLI simulator, performance benchmarks
- [ ] Milestone E — full documentation, chaos testing, production-readiness review

## Running locally

```
cp .env.example .env      # local-only dev credentials for the Postgres container - see .env.example
docker compose up -d      # Postgres, Redis, Kafka
./gradlew bootRun         # starts the switch
```

(End-to-end transaction instructions will be added once the golden path is implemented.)

## License

Apache-2.0 — see `LICENSE`.
