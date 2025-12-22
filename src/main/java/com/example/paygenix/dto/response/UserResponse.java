package com.example.paygenix.dto.response;

import lombok.Builder;

import java.util.Set;

@Builder
public record UserResponse (
         Long id,
         String email,
         String fullName,
         Boolean active,
         Set<String> roles
){
}
