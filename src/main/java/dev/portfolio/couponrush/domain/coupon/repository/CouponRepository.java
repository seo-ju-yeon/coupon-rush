package dev.portfolio.couponrush.domain.coupon.repository;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // 쿠폰 발급 중 다른 요청이 같은 쿠폰을 수정하지 못하도록 DB 행을 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :couponId")
    Optional<Coupon> findByIdWithPessimisticLock(
            @Param("couponId") Long couponId
    );
}
