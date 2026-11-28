package com.example.paygenix.enums;

public enum PaymentStatus {
    PENDING,
    PROCESSING, // amount held, the operator's answer not known yet (T-144)
    COMPLETED,
    EXPIRED // not confirmed within PaymentService.FLOW_TTL (T-132)
}
