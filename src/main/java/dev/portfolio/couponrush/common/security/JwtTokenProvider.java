package dev.portfolio.couponrush.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
// 사용자 정보를 바탕으로 JWT Access Token을 생성함
public class JwtTokenProvider {

    // JwtConfig에서 등록한 JWT 생성기를 주입받음
    private final JwtEncoder jwtEncoder;

    // 토큰 발급자와 유효시간 설정을 주입받음
    private final JwtProperties jwtProperties;

    // 사용자 식별 정보와 권한을 포함한 Access Token을 생성함
    public String createAccessToken(
            Long userId,
            String email,
            String role
    ) {
        // 현재 시간을 UTC 기준 시각으로 생성함
        Instant issuedAt = Instant.now();

        // 현재 시간에 설정된 유효시간을 더하여 만료시간을 계산함
        Instant expiresAt = issuedAt.plusSeconds(
                jwtProperties.accessTokenExpirationSeconds()
        );

        /*
        JWT Header를 생성함
        - alg: HS256 서명 알고리즘 사용
        - typ: 생성하는 토큰의 형식이 JWT임을 나타냄
         */
        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        /*
        JWT Payload에 들어갈 Claim을 생성함
        - issuer: 토큰을 발급한 애플리케이션임
        - issuedAt: 토큰 발급 시각임
        - expiresAt: 토큰 만료 시각임
        - subject: 토큰의 주체인 사용자 ID임
        - email, role: 애플리케이션에서 사용할 사용자 정보임
         */
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .build();

        /*
        Header와 Payload를 JwtEncoder에 전달함
        JwtEncoder는 JwtConfig의 비밀 키로 서명을 생성함
         */
        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claimsSet)
        ).getTokenValue();
    }

    // API 응답에 포함할 Access Token 유효시간을 초 단위로 반환함
    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.accessTokenExpirationSeconds();
    }
}
