package dev.portfolio.couponrush.domain.coupon.dto;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "쿠폰 응답")
// 쿠폰 정보를 API 응답 형태로 전달하는 DTO임
public class CouponResponse {

    // 쿠폰의 식별자를 반환함
    @Schema(description = "쿠폰 식별자", example = "1")
    private final Long id;

    // 쿠폰의 이름을 반환함
    @Schema(description = "쿠폰 이름", example = "선착순 5천 원 할인 쿠폰")
    private final String name;

    // 쿠폰의 할인 금액을 반환함
    @Schema(description = "쿠폰 할인 금액", example = "5000")
    private final Integer discountAmount;

    // 쿠폰의 전체 발급 가능 수량을 반환함
    @Schema(description = "쿠폰 전체 발급 가능 수량", example = "100")
    private final Integer totalQuantity;

    // 현재까지 발급된 쿠폰 수량을 반환함
    @Schema(description = "현재까지 발급된 수량", example = "0")
    private final Integer issuedQuantity;

    // 쿠폰 발급 시작 일시를 반환함
    @Schema(description = "쿠폰 발급 시작 일시", example = "2026-09-04T10:00:00")
    private final LocalDateTime startsAt;

    // 쿠폰 발급 종료 일시를 반환함
    @Schema(description = "쿠폰 발급 종료 일시", example = "2026-09-30T23:59:59")
    private final LocalDateTime endsAt;

    // 쿠폰의 현재 상태를 반환함
    @Schema(description = "쿠폰 상태", example = "READY")
    private final CouponStatus status;

    // 쿠폰 생성 일시를 반환함
    @Schema(description = "쿠폰 생성 일시", example = "2026-09-04T09:00:00")
    private final LocalDateTime createdAt;

    // 쿠폰 수정 일시를 반환함
    @Schema(description = "쿠폰 수정 일시", example = "2026-09-04T09:00:00")
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
        // Coupon 엔티티를 API 응답용 DTO로 변환함
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
