package com.example.paygenix.enums;

public enum PaymentStatus {
    PENDING,
    COMPLETED,
    EXPIRED // not confirmed within PaymentService.FLOW_TTL (T-132)
}
