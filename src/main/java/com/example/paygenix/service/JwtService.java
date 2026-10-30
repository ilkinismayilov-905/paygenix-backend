package com.example.paygenix.service;

import com.example.paygenix.entity.OnboardingFlow;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private final JwtEncoder jwtEncoder;

    JwsHeader jwsHeader = JwsHeader
            .with(MacAlgorithm.HS256)
            .build();

    // Which endpoints a token may call; JwtFilter only authenticates the matching use.
    public static final String TOKEN_USE = "token_use";
    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";
    public static final String ONBOARDING = "onboarding";
    // Device session (UserSession id) the access or refresh token belongs to.
    public static final String SESSION_ID = "sid";
    public static final String FLOW_ID = "flowId";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }


    public String generateJwtToken(UserDetails userDetails, Long userId, String fullName, UUID sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(SESSION_ID, sessionId.toString());
        claims.put("userId", userId);
        claims.put("fullName", fullName);
        claims.put("authorities", userDetails.getAuthorities());
        claims.put(TOKEN_USE, ACCESS);

        return createToken(claims, userDetails.getUsername(), jwtExpiration);
    }

    // jti makes every refresh token unique, even two issued in the same second
    public String generateJwtRefreshToken(UserDetails userDetails, UUID sessionId) {
        Map<String, Object> claims = new HashMap<>(Map.of(
                TOKEN_USE, REFRESH,
                SESSION_ID, sessionId.toString(),
                Claims.ID, UUID.randomUUID().toString()));
        return createToken(claims, userDetails.getUsername(), refreshExpiration);
    }


    public String generateOnboardingToken(OnboardingFlow flow) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("onboarding")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim(TOKEN_USE, ONBOARDING)
                .claim(FLOW_ID, flow.getId())
                .build();


        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims))
                .getTokenValue();
    }

    public String createToken(Map<String, Object> claims, String subject, Long jwtExpiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(jwtExpiration)))
                .signWith(getSigningKey())
                .compact();

    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String extractTokenUse(String token) {
        return extractClaim(token, claims -> claims.get(TOKEN_USE, String.class));
    }

    public UUID extractSessionId(String token) {
        String sid = extractClaim(token, claims -> claims.get(SESSION_ID, String.class));
        return sid == null ? null : UUID.fromString(sid);
    }

    public String extractFullName(String token) {
        return extractClaim(token, claims -> claims.get("fullName", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.debug("JWT token unsupported: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.debug("JWT token malformed: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            log.debug("JWT signature invalid: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.debug("JWT token compact of handler are invalid: {}", e.getMessage());
            throw e;
        }
    }

    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

}
