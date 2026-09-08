package dev.portfolio.couponrush.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "JWT 토큰 응답")
public class TokenResponse {

    // 이후 API 요청의 Authorization 헤더에 사용할 JWT
    @Schema(description = "JWT Access Token")
    private final String accessToken;

    // Authorization 헤더에서 사용하는 인증 타입
    @Schema(description = "토큰 타입", example = "Bearer")
    private final String tokenType;

    // Access Token이 만료될 때까지 남은 시간을 초 단위로 반환함
    @Schema(description = "Access Token 유효시간(초)", example = "3600")
    private final long expiresIn;

    private TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    public static TokenResponse of(
            String accessToken,
            long expiresIn
    ) {
        return new TokenResponse(
                accessToken,
                "Bearer",
                expiresIn
        );
    }

}
