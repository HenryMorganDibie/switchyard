package com.henrymorgandibie.switchyard.messaging.kafka;

/** {@code status} is a {@code ParticipantStatus} name (e.g. {@code "UP"}, {@code "DOWN"}) as plain text. */
public record NetworkEventPayload(String institutionId, String status) {
}
