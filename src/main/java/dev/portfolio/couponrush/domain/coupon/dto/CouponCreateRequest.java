package dev.portfolio.couponrush.domain.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Schema(description = "쿠폰 생성 요청")
// 쿠폰 생성 API의 요청 데이터를 담는 DTO임
public class CouponCreateRequest {

    // 쿠폰 생성 시 입력받을 쿠폰 이름을 검증함
    @Schema(description = "쿠폰 이름", example = "선착순 5천 원 할인 쿠폰",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "쿠폰 이름은 필수입니다.")
    @Size(max = 100, message = "쿠폰 이름은 100자를 초과할 수 없습니다.")
    private String name;

    // 쿠폰 1장에 적용할 할인 금액을 검증함
    @Schema(description = "쿠폰 할인 금액", example = "5000",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "할인 금액은 필수입니다.")
    @Positive(message = "할인 금액은 0보다 커야 합니다.")
    private Integer discountAmount;

    // 쿠폰을 발급할 수 있는 전체 수량을 검증함
    @Schema(description = "쿠폰 전체 발급 가능 수량", example = "100",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "총 발급 수량은 필수입니다.")
    @Positive(message = "총 발급 수량은 0보다 커야 합니다.")
    private Integer totalQuantity;

    // 쿠폰 발급 시작 일시를 검증함
    @Schema(description = "쿠폰 발급 시작 일시", example = "2026-09-04T10:00:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "발급 시작 일시는 필수입니다.")
    private LocalDateTime startsAt;

    // 쿠폰 발급 종료 일시를 검증함
    @Schema(description = "쿠폰 발급 종료 일시", example = "2026-09-30T23:59:59",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "발급 종료 일시는 필수입니다.")
    private LocalDateTime endsAt;
}
