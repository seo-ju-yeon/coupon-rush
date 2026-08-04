package dev.portfolio.couponrush.domain.order.dto;

import dev.portfolio.couponrush.domain.order.entity.Order;
import dev.portfolio.couponrush.domain.order.entity.OrderStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OrderResponse {
    // 주문 정보를 API 응답 형태로 전달하는 DTO임

    // 주문의 식별자를 반환함
    private final Long id;

    // 주문을 생성한 사용자의 ID를 반환함
    private final Long userId;

    // 주문에 사용한 쿠폰 발급 내역의 ID를 반환함
    private final Long couponIssueId;

    // 쿠폰 할인 전 주문 금액을 반환함
    private final Integer originalAmount;

    // 쿠폰 할인 금액을 반환함
    private final Integer discountAmount;

    // 할인 적용 후 최종 주문 금액을 반환함
    private final Integer finalAmount;

    // 주문 상태를 반환함
    private final OrderStatus status;

    // 주문 생성 일시를 반환함
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
