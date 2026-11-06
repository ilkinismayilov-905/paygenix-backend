package com.example.paygenix.phone;

import com.example.paygenix.exceptions.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Duration;

public final class PhoneTopUpException {

    private PhoneTopUpException() {
    }

    public static ApiException rejected() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "PHONE_TOPUP_REJECTED",
                "The operator refused this number; nothing was charged");
    }

    public static ApiException timeout() {
        return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PROVIDER_TIMEOUT",
                "The operator did not answer; nothing was charged, try again", Duration.ofSeconds(30));
    }
}
