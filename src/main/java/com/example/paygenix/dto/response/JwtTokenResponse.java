package com.example.paygenix.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;

@Builder
public record JwtTokenResponse (
        String token,
        // only ever sent as the httpOnly refresh cookie (ADR 0001), never in a JSON body
        @JsonIgnore String refreshToken,
        String tokenType,
        Long expiresIn,
        UserResponse user
){
    // a record's generated toString would print the refresh token into any log line that formats this
    @Override
    public String toString() {
        return "JwtTokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
