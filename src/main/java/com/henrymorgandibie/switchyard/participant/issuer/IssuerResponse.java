package com.henrymorgandibie.switchyard.participant.issuer;

/**
 * An issuer's decision on an authorization/financial/reversal request. {@code responseCode}
 * matches ISO 8583 DE39 (e.g. "00" for approved); {@code authorizationId} matches DE38 and is
 * only meaningful when approved.
 */
public record IssuerResponse(String responseCode, String authorizationId) {

    public boolean approved() {
        return "00".equals(responseCode);
    }
}
