package com.henrymorgandibie.switchyard.iso8583.message;

import com.henrymorgandibie.switchyard.iso8583.exception.UnsupportedMtiException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/** The eight Message Type Indicators switchyard supports. */
public enum Mti {
    AUTHORIZATION_REQUEST("0100"),
    AUTHORIZATION_RESPONSE("0110"),
    FINANCIAL_REQUEST("0200"),
    FINANCIAL_RESPONSE("0210"),
    REVERSAL_REQUEST("0400"),
    REVERSAL_RESPONSE("0410"),
    NETWORK_MANAGEMENT_REQUEST("0800"),
    NETWORK_MANAGEMENT_RESPONSE("0810");

    private static final Map<String, Mti> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Mti::code, mti -> mti));

    private final String code;

    Mti(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Mti fromCode(String code) {
        Mti mti = BY_CODE.get(code);
        if (mti == null) {
            throw new UnsupportedMtiException(code);
        }
        return mti;
    }

    public static boolean isSupported(String code) {
        return BY_CODE.containsKey(code);
    }

    /** The response MTI for a request MTI (e.g. FINANCIAL_REQUEST -&gt; FINANCIAL_RESPONSE). */
    public Mti responseMti() {
        return switch (this) {
            case AUTHORIZATION_REQUEST -> AUTHORIZATION_RESPONSE;
            case FINANCIAL_REQUEST -> FINANCIAL_RESPONSE;
            case REVERSAL_REQUEST -> REVERSAL_RESPONSE;
            case NETWORK_MANAGEMENT_REQUEST -> NETWORK_MANAGEMENT_RESPONSE;
            default -> throw new IllegalStateException(this + " is a response MTI, it has no response MTI of its own");
        };
    }
}
