package com.henrymorgandibie.switchyard.participant.simulator;

import java.util.Map;

/**
 * Maps designated test PANs (ISO 8583 DE2) to the {@link FaultScenario} they trigger - the same
 * pattern real card-network sandboxes use (a fixed set of published test card numbers, each
 * documented to always produce one specific outcome), rather than a side-channel test hook a
 * client would have no way to exercise for real. A PAN not in this table, or a request with no
 * PAN at all, defaults to {@link FaultScenario#APPROVE}, matching the switch's behavior before
 * this milestone.
 *
 * <p>Where possible, the last two digits of each test PAN echo the ISO 8583 response code that
 * scenario produces (...51 -&gt; response code 51, ...14 -&gt; response code 14) as a memorable,
 * documented convention - never a real card number, never derived from one.
 */
public final class FaultInjectionRegistry {

    private static final Map<String, FaultScenario> BY_PAN = Map.ofEntries(
            Map.entry("4111111111111111", FaultScenario.APPROVE),
            Map.entry("4000000000000005", FaultScenario.DECLINE),
            Map.entry("4000000000000051", FaultScenario.INSUFFICIENT_FUNDS),
            Map.entry("4000000000000014", FaultScenario.INVALID_ACCOUNT),
            Map.entry("4000000000000091", FaultScenario.ISSUER_UNAVAILABLE),
            Map.entry("4000000000000096", FaultScenario.CONNECTION_RESET),
            Map.entry("4000000000000001", FaultScenario.TIMEOUT),
            Map.entry("4000000000000002", FaultScenario.DELAYED_APPROVAL),
            Map.entry("4000000000000003", FaultScenario.MALFORMED_RESPONSE)
    );

    private FaultInjectionRegistry() {
    }

    public static FaultScenario resolve(String pan) {
        if (pan == null) {
            return FaultScenario.APPROVE;
        }
        return BY_PAN.getOrDefault(pan, FaultScenario.APPROVE);
    }
}
