package dev.portfolio.couponrush.domain.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "쿠폰 발급 요청")
// 쿠폰 발급 API의 요청 데이터를 담는 DTO임
public class CouponIssueCreateRequest {

    // 쿠폰을 발급받을 사용자의 ID를 검증함
    @Schema(
            description = "쿠폰을 발급받을 사용자 식별자",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;
}
