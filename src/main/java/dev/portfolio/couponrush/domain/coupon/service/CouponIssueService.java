package dev.portfolio.couponrush.domain.coupon.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueCreateRequest;
import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueResponse;
import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponIssueRepository;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponIssueService {
    /*
    처리 흐름:
    쿠폰 조회 -> 사용자 조회 -> 쿠폰 상태 확인 -> 발급 기간 확인 -> 중복 발급 확인 -> 쿠폰 수량 증가 -> CouponIssue 저장 -> 응답 DTO 반환
     */

    private final CouponIssueRepository couponIssueRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    @Transactional
    public CouponIssueResponse issueCoupon(
            Long couponId,
            CouponIssueCreateRequest request
    ) {
        log.info("쿠폰 발급 요청: couponId={}, userId={}", couponId, request.getUserId());

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COUPON_NOT_FOUND
                ));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND
                ));

        validateCouponIssuable(coupon);

        boolean alreadyIssued = couponIssueRepository.existsByCoupon_IdAndUser_Id(couponId, request.getUserId());

        if (alreadyIssued) {
            throw new BusinessException(ErrorCode.DUPLICATE_COUPON_ISSUE);
        }

        coupon.increaseIssueQuantity();

        CouponIssue couponIssue = new CouponIssue(coupon, user);
        CouponIssue savedCouponIssue = couponIssueRepository.save(couponIssue);

        log.info("쿠폰 발급 완료: issuedId={}, couponId={}, userId={}, status={}",
                savedCouponIssue.getId(),
                couponId,
                request.getUserId(),
                savedCouponIssue.getStatus());

        return CouponIssueResponse.from(savedCouponIssue);
    }

    private void validateCouponIssuable(Coupon coupon) {
        if (coupon.getStatus() != CouponStatus.OPEN) {
            throw new BusinessException(ErrorCode.COUPON_NOT_OPEN);
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(coupon.getStartsAt()) || now.isAfter(coupon.getEndsAt())) {
            throw new BusinessException(ErrorCode.INVALID_COUPON_PERIOD);
        }
    }
}
