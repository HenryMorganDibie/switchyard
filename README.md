# switchyard

Production-oriented ISO 8583 payment switch reference implementation, built with Java 21 and
Spring Boot 4.

**Status: Milestone B complete.** A real ISO 8583 0200 sent over a real TCP socket is decoded,
validated, routed, authorized by a simulated issuer, persisted through an explicit transaction
state machine, and answered with a real 0210 — proven by an unmocked, Testcontainers-backed
end-to-end test (`GoldenPathIntegrationTest`). On top of that golden path, the switch now also
simulates realistic downstream failures with deliberate ISO response codes, guarantees idempotency
under concurrent duplicate/retried requests (Postgres unique constraint as source of truth, Redis
as a fast-path cache), processes reversals — including switch-assigned RRNs and a real,
unmocked-Postgres proof that concurrent reversals for the same transaction are resolved correctly
under genuine optimistic-lock contention — and handles network management (0800/0810) sign-on,
sign-off, and echo. Kafka, observability, security, the REST API, and performance benchmarking
are not built yet — see the checklist below. This README will keep growing as each milestone is
actually implemented and verified — nothing is documented here until it's been run and tested.
See `docs/production-hardening.md` (once written) for the full "implemented here" vs. "required
for real production" split.

## What this project is

A modular-monolith simulation of a card-payment switch: an ATM/POS simulator sends ISO 8583
messages over a real TCP connection to a Netty-based gateway, which decodes, validates, routes,
and processes them against simulated issuer/acquirer participants, backed by PostgreSQL, Redis,
and Kafka. It does not connect to any real bank, card scheme, or live payment network.

**This is a portfolio / learning artifact, not a certified payment processor.** It is not
PCI-DSS certified and does not implement real card-scheme certification. See `SECURITY.md`.

## Build status

- [x] Milestone A — ISO 8583 codec, TCP gateway, transaction pipeline, golden-path 0200→0210
      over TCP, verified end-to-end
- [x] Milestone B — failure simulation, idempotency, reversals, network management
- [ ] Milestone C — Kafka events, observability, security controls
- [ ] Milestone D — admin/simulation REST API, CLI simulator, performance benchmarks
- [ ] Milestone E — full documentation, chaos testing, production-readiness review

## Running locally

```
cp .env.example .env      # local-only dev credentials for the Postgres container - see .env.example
docker compose up -d      # Postgres, Redis, Kafka
./gradlew bootRun         # starts the switch
```

`bootRun` starts a real switch listening for ISO 8583 traffic on port 8583 (length-prefixed, see
`docs/networking.md` once written). There's no CLI simulator to drive it by hand yet — that's
Milestone D — but `./gradlew test --tests "*.GoldenPathIntegrationTest"` runs a real client
against a real instance of the switch end to end and is the current proof it works.

## License

Apache-2.0 — see `LICENSE`.
