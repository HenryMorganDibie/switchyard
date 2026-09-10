package com.henrymorgandibie.switchyard.routing.domain;

/**
 * A named participant in the simulated payment network - an issuer or an acquirer. Status
 * tracking (DOWN/CONNECTING/UP/DEGRADED) is deliberately not included yet: nothing needs it
 * until network management (0800/0810 sign-on/echo) exists to actually change it, so it will be
 * added then rather than carried unused from here.
 */
public record NetworkParticipant(String code, String name, Role role) {

    public enum Role {
        ISSUER, ACQUIRER
    }
}
