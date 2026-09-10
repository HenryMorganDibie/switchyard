package com.henrymorgandibie.switchyard.routing.domain;

/**
 * Maps an acquirer to the issuer transactions from it should be routed to. Config-backed
 * in-memory data for now, not a Postgres table: nothing yet needs to mutate routing rules at
 * runtime (there is no admin API), so persisting them now would add a table with no real
 * consumer other than hardcoded seed data. Add a routing_rules migration + entity when the
 * admin API milestone actually needs to manage rules dynamically.
 */
public record RoutingRule(String acquirerCode, String issuerCode, int priority, boolean active) {
}
