package dev.portfolio.couponrush.domain.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderCreateRequest {
    // 주문 생성 API의 요청 데이터를 담는 DTO

    // 주문을 생성할 사용자의 ID를 검증함
    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    // 주문에 사용할 쿠폰 발급 내역의 ID를 검증함
    @NotNull(message = "쿠폰 발급 내역 ID는 필수입니다.")
    private Long couponIssueId;

    // 쿠폰 할인 전 주문 금액을 검증함
    @NotNull(message = "주문 금액은 필수입니다.")
    @PositiveOrZero(message = "주문 금액은 0 이상이어야 합니다.")
    private Integer originalAmount;

}
