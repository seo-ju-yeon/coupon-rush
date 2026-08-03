package dev.portfolio.couponrush.domain.coupon.repository;

import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {

    // 쿠폰 ID와 사용자 ID로 중복 발급 여부를 확인함
    boolean existsByCoupon_IdAndUser_Id(Long couponId, Long userId);

}
