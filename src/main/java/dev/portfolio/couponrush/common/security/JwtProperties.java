package dev.portfolio.couponrush.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
// application.properties의 JWT 설정을 불변 객체로 관리함
// record: 설정값처럼 생성 후 변경할 필요가 없는 데이터를 표현하기 적합함
public record JwtProperties(

        // HS256 JWT의 생성과 검증에 사용할 비밀 키임
        @NotBlank
        String secret,

        // JWT를 발급한 애플리케이션을 식별하는 값임
        @NotBlank
        String issuer,

        // Access Token의 유효시간을 초 단위로 관리함
        @Positive
        long accessTokenExpirationSeconds
) {

}
