# Security

## Scope and status

**switchyard is a portfolio / reference implementation.** It is not a certified payment
processor, it has not undergone a PCI-DSS assessment, and it has not been certified by any card
scheme or payment network. Nothing in this repository should be interpreted as a claim of
PCI-DSS compliance or production readiness for real financial transactions.

## What this project does

- Never logs or persists a real PAN, PIN, CVV, or track data — all card data used anywhere in
  this repository (fixtures, tests, simulator scenarios) is synthetic test data.
- Masks payment identifiers (PAN, track 2 data) at every logging and persistence boundary.
- Keeps secrets out of source control; local development credentials in `docker-compose.yml`
  are placeholder values for a throwaway local container, not real secrets.
- Applies input validation on the ISO 8583 wire path and the REST API.
- Authenticates administrative/simulation REST endpoints and applies rate limiting to them.
- Writes an audit trail for transaction state transitions and administrative actions.

## What a real PCI-DSS-scoped payment system would additionally require

This list is deliberately explicit, since the gap between "reference implementation" and
"production payment system" is the point:

- Hardware Security Module (HSM) integration for key management and PIN block translation —
  this project's DE52 field is synthetic simulator data, never a real PIN block implementation.
- Formal PCI-DSS assessment and certification by a Qualified Security Assessor.
- Card-scheme and network certification (e.g. Visa/Mastercard conformance testing).
- Production-grade secrets management (vault/KMS-backed, rotated, access-audited).
- mTLS and network-level security controls between switch and participants.
- Settlement and reconciliation processes.
- Disaster recovery and business continuity planning.
- Documented operational runbooks and incident response procedures.
- Independent penetration testing and periodic security audits.
- Formal conformance to a specific card scheme's ISO 8583 implementation guide (this project
  uses a project-defined ISO 8583:1987-inspired profile — see `docs/iso8583.md` — not a
  reproduction of any proprietary specification).

## Reporting a concern

This is a personal portfolio project. Open a GitHub issue for any security concern found in the
code.
