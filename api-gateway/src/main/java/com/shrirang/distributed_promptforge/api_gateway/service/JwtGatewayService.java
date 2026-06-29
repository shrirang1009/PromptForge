package com.shrirang.distributed_promptforge.api_gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class JwtGatewayService {

    @Value("${jwt.secret}")
    private String secretKey;

    private SecretKey cachedKey;

    @PostConstruct
    public void init() {
        cachedKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public void validateToken(String token) {
        Jwts.parser()
                .verifyWith(cachedKey)
                .build()
                .parseSignedClaims(token);
    }
}
