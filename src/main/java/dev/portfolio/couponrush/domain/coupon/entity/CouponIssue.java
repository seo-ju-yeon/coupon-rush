package dev.portfolio.couponrush.domain.coupon.entity;

import dev.portfolio.couponrush.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "coupon_issues",
        uniqueConstraints = {
                // 한 사용자는 같은 쿠폰을 한 번만 발급받을 수 있게 제한함
                @UniqueConstraint(
                        name = "uk_coupon_issues_coupon_user",
                        columnNames = {"coupon_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponIssueStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // Order 엔티티와의 양방향 매핑은 사용 처리 로직에서 다루고, 현재는 사용된 주문 ID만 저장함
    @Column(name = "used_order_id")
    private Long usedOrderId;

    // 생성 시 기본 상태를 ISSUED로 설정함
    public CouponIssue(Coupon coupon, User user) {
        this.coupon = coupon;
        this.user = user;
        this.status = CouponIssueStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
    }
}
