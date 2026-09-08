package dev.portfolio.couponrush.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "쿠폰을 사용한 주문 생성 요청")
public class OrderCreateRequest {
    // 주문 생성 API의 요청 데이터를 담는 DTO


    // 주문에 사용할 쿠폰 발급 내역의 ID를 검증함
    @Schema(
            description = "주문에 사용할 쿠폰 발급 내역 식별자",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "쿠폰 발급 내역 ID는 필수입니다.")
    private Long couponIssueId;

    // 쿠폰 할인 전 주문 금액을 검증함
    @Schema(
            description = "쿠폰 할인 적용 전 주문 금액",
            example = "20000",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "주문 금액은 필수입니다.")
    @PositiveOrZero(message = "주문 금액은 0 이상이어야 합니다.")
    private Integer originalAmount;

}
