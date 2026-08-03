package dev.portfolio.couponrush.domain.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CouponCreateRequest {

    @NotBlank(message = "쿠폰 이름은 필수입니다.")
    @Size(max = 100, message = "쿠폰 이름은 100자를 초과할 수 없습니다.")
    private String name;

    @NotNull(message = "할인 금액은 필수입니다.")
    @Positive(message = "할인 금액은 0보다 커야 합니다.")
    private Integer discountAmount;

    @NotNull(message = "총 발급 수량은 필수입니다.")
    @Positive(message = "총 발급 수량은 0보다 커야 합니다.")
    private Integer totalQuantity;

    @NotNull(message = "발급 시작 일시는 필수입니다.")
    private LocalDateTime startsAt;

    @NotNull(message = "발급 종료 일시는 필수입니다.")
    private LocalDateTime endsAt;
}
