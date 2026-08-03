package dev.portfolio.couponrush.domain.coupon.dto;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CouponResponse {

    private final Long id;
    private final String name;
    private final Integer discountAmount;
    private final Integer totalQuantity;
    private final Integer issuedQuantity;
    private final LocalDateTime startsAt;
    private final LocalDateTime endsAt;
    private final CouponStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private CouponResponse(
            Long id,
            String name,
            Integer discountAmount,
            Integer totalQuantity,
            Integer issuedQuantity,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            CouponStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.discountAmount = discountAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = issuedQuantity;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountAmount(),
                coupon.getTotalQuantity(),
                coupon.getIssuedQuantity(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                coupon.getStatus(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }
}
