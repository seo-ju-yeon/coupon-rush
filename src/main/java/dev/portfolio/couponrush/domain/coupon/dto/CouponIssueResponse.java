package dev.portfolio.couponrush.domain.coupon.dto;

import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssueStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
// 쿠폰 발급 내역을 API 응답 형태로 전달하는 DTO임
public class CouponIssueResponse {

    // 쿠폰 발급 내역의 식별자를 반환함
    private final Long id;
    // 발급된 쿠폰의 식별자를 반환함
    private final Long couponId;
    // 쿠폰을 발급받은 사용자의 식별자를 반환함
    private final Long userId;
    // 쿠폰 발급 내역의 현재 상태를 반환함
    private final CouponIssueStatus status;
    // 쿠폰이 발급된 일시를 반환함
    private final LocalDateTime issuedAt;
    // 쿠폰이 사용된 일시를 반환함
    private final LocalDateTime usedAt;
    // 쿠폰 사용으로 생성된 주문의 식별자를 반환함
    private final Long usedOrderId;

    private CouponIssueResponse(
            Long id,
            Long couponId,
            Long userId,
            CouponIssueStatus status,
            LocalDateTime issuedAt,
            LocalDateTime usedAt,
            Long usedOrderId
    ) {
        this.id = id;
        this.couponId = couponId;
        this.userId = userId;
        this.status = status;
        this.issuedAt = issuedAt;
        this.usedAt = usedAt;
        this.usedOrderId = usedOrderId;
    }

    public static CouponIssueResponse from(CouponIssue couponIssue) {
        // 엔티티의 연관 객체에서는 ID만 꺼내 응답 DTO로 변환함
        return new CouponIssueResponse(
                couponIssue.getId(),
                couponIssue.getCoupon().getId(),
                couponIssue.getUser().getId(),
                couponIssue.getStatus(),
                couponIssue.getIssuedAt(),
                couponIssue.getUsedAt(),
                couponIssue.getUsedOrderId()
        );
    }
}
