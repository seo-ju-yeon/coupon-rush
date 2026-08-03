package dev.portfolio.couponrush.domain.coupon.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.coupon.dto.CouponCreateRequest;
import dev.portfolio.couponrush.domain.coupon.dto.CouponResponse;
import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        log.info("쿠폰 생성 요청: name={}", request.getName());

        validateCouponPeriod(request);

        Coupon coupon = new Coupon(
                request.getName(),
                request.getDiscountAmount(),
                request.getTotalQuantity(),
                request.getStartsAt(),
                request.getEndsAt(),
                CouponStatus.READY
        );

        Coupon savedCoupon = couponRepository.save(coupon);

        log.info(
                "쿠폰 생성 완료: id={}, name={}, status={}",
                savedCoupon.getId(),
                savedCoupon.getName(),
                savedCoupon.getStatus()
        );

        return CouponResponse.from(savedCoupon);
    }

    public CouponResponse getCoupon(Long couponId) {
        log.info("쿠폰 단건 조회 요청: couponId={}", couponId);

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        return CouponResponse.from(coupon);
    }

    public List<CouponResponse> getCoupons() {
        log.info("쿠폰 목록 조회 요청");

        return couponRepository.findAll()
                .stream()
                .map(coupon -> CouponResponse.from(coupon))
                .toList();
    }

    private void validateCouponPeriod(CouponCreateRequest request) {
        if (!request.getStartsAt().isBefore(request.getEndsAt())) {
            throw new BusinessException(ErrorCode.INVALID_COUPON_PERIOD);
        }
    }
}
