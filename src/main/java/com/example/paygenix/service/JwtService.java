package com.example.paygenix.service;

import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {
    private final JwtEncoder jwtEncoder;

    JwsHeader jwsHeader = JwsHeader
            .with(MacAlgorithm.HS256) // 🔴 BU SƏTİR VACİBDİR
            .build();

    public String generateToken(OnboardingFlow flow) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("onboarding")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("scope", "onboarding")
                .claim("flowId", flow.getId())
                .build();



        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader,claims))
                .getTokenValue();
    }

    public String generateUserToken(User user){
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("roles", user.getRoles())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader,claims))
                .getTokenValue();
    }
}
