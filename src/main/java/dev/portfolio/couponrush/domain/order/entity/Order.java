package dev.portfolio.couponrush.domain.order.entity;

import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                // 같은 발급 쿠폰으로 주문을 여러 번 만들 수 없게 제한함
                @UniqueConstraint(
                        name = "uk_orders_coupon_issue",
                        columnNames = "coupon_issue_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_amount", nullable = false)
    private Integer originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Integer discountAmount;

    @Column(name = "final_amount", nullable = false)
    private Integer finalAmount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_issue_id", nullable = false)
    private CouponIssue couponIssue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Order(User user, CouponIssue couponIssue, Integer originalAmount, Integer discountAmount) {
        this.user = user;
        this.couponIssue = couponIssue;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = originalAmount - discountAmount;
        // 생성 시 기본 상태를 CREATED로 설정함
        this.status = OrderStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }
}
