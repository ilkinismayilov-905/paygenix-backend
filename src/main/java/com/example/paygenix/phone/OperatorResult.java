package com.example.paygenix.phone;

/** What the mobile operator says about one top-up reference. */
public enum OperatorResult {
    /** The phone's balance was topped up. */
    DELIVERED,
    /** Definitely not delivered (refused, or the operator has no such top-up); the held amount can be returned. */
    NOT_DELIVERED,
    /** No answer in time, or an answer that cannot be trusted: the money stays held until the operator is asked again. */
    UNKNOWN
}
