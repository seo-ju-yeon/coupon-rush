package dev.portfolio.couponrush.common.security;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Log4j2
@SpringJUnitConfig(classes = {
        JwtConfig.class,
        JwtTokenProvider.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "security.jwt.issuer=coupon-rush",
        "security.jwt.access-token-expiration-seconds=3600"
})
// 실제 DB 없이 JWT 생성과 검증에 필요한 Spring Bean만 불러와 테스트함
class JwtTokenProviderTest {

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void createAndDecodeAccessToken() {
        String accessToken = jwtTokenProvider.createAccessToken(
                1L,
                "jwt-test@example.com",
                "USER"
        );

        Jwt decodedJwt = jwtDecoder.decode(accessToken);

        // JWT가 Header, Payload, Signature의 세 부분으로 생성되었는지 검증함
        assertThat(accessToken.split("\\.")).hasSize(3);

        // 토큰을 해석하여 사용자 정보와 발급자 Claim이 올바른지 검증함
        assertThat(decodedJwt.getSubject()).isEqualTo("1");
        assertThat(decodedJwt.getClaimAsString("email"))
                .isEqualTo("jwt-test@example.com");
        assertThat(decodedJwt.getClaimAsString("role"))
                .isEqualTo("USER");
        assertThat(decodedJwt.getClaimAsString("iss"))
                .isEqualTo("coupon-rush");
        assertThat(decodedJwt.getIssuedAt()).isNotNull();
        assertThat(decodedJwt.getExpiresAt()).isNotNull();

        // 발급 시각과 만료 시각의 차이가 설정한 유효시간과 같은지 검증함
        long expirationSeconds = Duration.between(
                decodedJwt.getIssuedAt(),
                decodedJwt.getExpiresAt()
        ).getSeconds();

        assertThat(expirationSeconds).isEqualTo(3600L);
        assertThat(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .isEqualTo(3600L);

        // jwt.io에서 구조와 서명을 학습 목적으로 확인하기 위한 테스트 전용 토큰을 출력함
//        log.info("학습용 JWT: {}", accessToken);
    }

    @Test
    void decodeTamperedAccessTokenFails() {
        String accessToken = jwtTokenProvider.createAccessToken(
                1L,
                "jwt-test@example.com",
                "USER"
        );

        String[] tokenParts = accessToken.split("\\.");
        String signature = tokenParts[2];
        char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';
        String tamperedSignature = replacement + signature.substring(1);

        String tamperedToken =
                tokenParts[0] + "." + tokenParts[1] + "." + tamperedSignature;

        // 서명이 한 글자라도 변경된 토큰은 검증에 실패하는지 확인함
        assertThatThrownBy(() -> jwtDecoder.decode(tamperedToken))
                .isInstanceOf(JwtException.class);
    }
}
