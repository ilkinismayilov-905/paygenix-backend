package com.example.paygenix.dto.response;

import com.example.paygenix.entity.User;
import lombok.Builder;

@Builder
public record JwtTokenResponse (
        String token,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UserResponse user
){
}
