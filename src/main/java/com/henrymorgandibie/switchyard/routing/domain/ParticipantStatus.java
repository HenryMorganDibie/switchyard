package com.henrymorgandibie.switchyard.routing.domain;

/**
 * A network participant's connection status, as tracked by
 * {@link NetworkParticipantStatusRegistry} and changed by network management (0800/0810)
 * sign-on/sign-off - see the registry's Javadoc for why this lives separately from the immutable
 * {@link NetworkParticipant} record itself.
 *
 * <p>Only the two states this milestone's sign-on/sign-off messages actually produce are
 * modeled. A real network management implementation typically also tracks an intermediate
 * CONNECTING state and a DEGRADED state driven by downstream health signals (fault injection,
 * timeouts) feeding back into routing - deliberately not added here since nothing in the current
 * pipeline would ever set them.
 */
public enum ParticipantStatus {
    /** Never signed on, or explicitly signed off - the default for an unknown participant. */
    DOWN,
    UP
}
