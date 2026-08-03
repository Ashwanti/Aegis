package com.aegisteam.aegis.security;
import com.aegisteam.aegis.core.user.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Signs and parses JWTs for both token types the auth flow needs:
 *
 * <ul>
 *   <li><b>access</b> — short-lived (default 15m), carried on every request,
 *       never checked against Redis (its short TTL is the revocation
 *       mechanism).</li>
 *   <li><b>refresh</b> — longer-lived (default 7d), carries a {@code jti} so
 *       a single token can be blocklisted in Redis on rotation/logout
 *       without touching every other session — see {@link RefreshTokenService}.</li>
 * </ul>
 *
 * The signing key comes from {@code jwt.secret} (env-backed — never commit a
 * real value) and must be at least 256 bits for HS256.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ORG_ID = "orgId";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-minutes:15}") long accessMinutes,
            @Value("${jwt.refresh-token-expiry-days:7}") long refreshDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshDays);
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ORG_ID, principal.getOrgId())
                .claim(CLAIM_ROLE, principal.getAuthorities().iterator().next().getAuthority())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getUsername())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.getSeconds();
    }

    /** Parses and validates signature + expiry. Throws JwtException on any problem. */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public String getSubject(Claims claims) {
        return claims.getSubject();
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }

    /** Time remaining until this token's own expiry — used as the Redis blocklist TTL. */
    public Duration remainingTtl(Claims claims) {
        Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /** Narrow re-throw so callers only need to catch one type of "token is bad." */
    public boolean isExpired(JwtException e) {
        return e instanceof ExpiredJwtException;
    }
}
