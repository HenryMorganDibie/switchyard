package com.henrymorgandibie.switchyard.routing.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each network participant's connection status, keyed by institution id - the same
 * numeric identifier carried on the wire in DE32 (Acquiring Institution ID) - not by
 * {@link NetworkParticipant#code()}. Those are two different identifier namespaces in this
 * codebase today: {@code NetworkParticipant.code()} is a human-readable display code used by
 * routing ({@code "ACQ-DEMO"}), while DE32 is a numeric field (see {@code Iso8583Profile}) and is
 * what a real 0800 sign-on message actually carries to identify who is signing on. Reconciling
 * the two - so a routing decision could look up a participant's status by its display code - is a
 * natural next step for whenever routing actually consumes participant status; nothing does yet,
 * so this registry is keyed by whatever identifies a participant on the wire, not invented to
 * match a namespace nothing reads it through.
 *
 * <p>Kept as its own class rather than a mutable field on {@link NetworkParticipant} itself,
 * which is an immutable record describing a participant's fixed identity - status is mutable,
 * connection-lifecycle state with a different reason to change (a 0800 sign-on/sign-off arriving)
 * than identity ever would, so conflating the two would mean every status change either replaces
 * the "identity" record project-wide or requires {@link NetworkParticipant} to become mutable
 * shared state. This registry is in-memory only - a real deployment would need this to survive a
 * restart and be visible across switch instances, which is out of scope for this reference
 * implementation (see {@code docs/production-hardening.md}).
 *
 * <p>An institution not yet recorded here is treated as {@link ParticipantStatus#DOWN} - the safe
 * default (it hasn't proven it's reachable), not {@code UNKNOWN} - there is nothing this switch
 * would do differently for "never seen" versus "explicitly signed off."
 *
 * <p>Thread-safe: {@link ConcurrentHashMap} under an in-memory, single-JVM deployment is
 * sufficient here - there is no cross-instance coordination to do.
 */
public final class NetworkParticipantStatusRegistry {

    private final Map<String, ParticipantStatus> statusByInstitutionId = new ConcurrentHashMap<>();

    public ParticipantStatus statusOf(String institutionId) {
        return statusByInstitutionId.getOrDefault(institutionId, ParticipantStatus.DOWN);
    }

    public void markUp(String institutionId) {
        statusByInstitutionId.put(institutionId, ParticipantStatus.UP);
    }

    public void markDown(String institutionId) {
        statusByInstitutionId.put(institutionId, ParticipantStatus.DOWN);
    }
}
