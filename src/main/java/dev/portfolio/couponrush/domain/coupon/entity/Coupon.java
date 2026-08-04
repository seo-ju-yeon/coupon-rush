package dev.portfolio.couponrush.domain.coupon.entity;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "discount_amount", nullable = false)
    private Integer discountAmount;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "issued_quantity", nullable = false)
    private Integer issuedQuantity;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * 같은 쿠폰을 여러 트랜잭션이 수정할 때 변경 충돌을 감지함
     * 저장 시 조회 시점의 version도 함께 비교하며, 먼저 변경된 뒤의 요청은 예외와 함께 롤백됨
     * DB 행을 선점하지 않고 저장 시점에 충돌을 판단하는 낙관적 락 방식임
     */
    @Version
    @Column(nullable = false)
    private Long version;

    public Coupon(
            String name,
            Integer discountAmount,
            Integer totalQuantity,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            CouponStatus status
    ) {
        this.name = name;
        this.discountAmount = discountAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 쿠폰 발급 수량을 1 증가시킴
    public void increaseIssueQuantity() {
        if (issuedQuantity >= totalQuantity) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        issuedQuantity++;
    }
}
