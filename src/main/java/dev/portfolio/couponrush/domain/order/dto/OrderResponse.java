package dev.portfolio.couponrush.domain.order.dto;

import dev.portfolio.couponrush.domain.order.entity.Order;
import dev.portfolio.couponrush.domain.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "주문 생성 결과 응답")
public class OrderResponse {
    // 주문 정보를 API 응답 형태로 전달하는 DTO임

    // 주문의 식별자를 반환함
    @Schema(description = "주문 식별자", example = "1")
    private final Long id;

    // 주문을 생성한 사용자의 ID를 반환함
    @Schema(description = "주문을 생성한 사용자 식별자", example = "1")
    private final Long userId;

    // 주문에 사용한 쿠폰 발급 내역의 ID를 반환함
    @Schema(description = "주문에 사용한 쿠폰 발급 내역 식별자", example = "1")
    private final Long couponIssueId;

    // 쿠폰 할인 전 주문 금액을 반환함
    @Schema(description = "쿠폰 할인 적용 전 주문 금액", example = "20000")
    private final Integer originalAmount;

    // 쿠폰 할인 금액을 반환함
    @Schema(description = "적용된 쿠폰 할인 금액", example = "5000")
    private final Integer discountAmount;

    // 할인 적용 후 최종 주문 금액을 반환함
    @Schema(description = "할인 적용 후 최종 결제 금액", example = "15000")
    private final Integer finalAmount;

    // 주문 상태를 반환함
    @Schema(description = "주문 상태", example = "CREATED")
    private final OrderStatus status;

    // 주문 생성 일시를 반환함
    @Schema(description = "주문 생성 일시", example = "2026-09-04T11:00:00")
    private final LocalDateTime createdAt;

    private OrderResponse(
            Long id,
            Long userId,
            Long couponIssueId,
            Integer originalAmount,
            Integer discountAmount,
            Integer finalAmount,
            OrderStatus status,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.couponIssueId = couponIssueId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static OrderResponse from(Order order) {
        // Order 엔티티를 API 응답용 DTO로 변환함
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getCouponIssue().getId(),
                order.getOriginalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }

}
