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
        // T-144: the amount stays held (not paid) until the operator's answer is known
        return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PROVIDER_TIMEOUT",
                "The operator did not answer yet; the amount is held, not paid. Confirm again to retry; "
                        + "if the top-up did not go through, the amount is returned", Duration.ofSeconds(30));
    }
}
