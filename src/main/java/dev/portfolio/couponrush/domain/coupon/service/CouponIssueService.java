package dev.portfolio.couponrush.domain.coupon.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
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
    쿠폰 발급 처리 흐름
    1. 쿠폰 조회
    2. JWT에서 전달받은 사용자 ID로 사용자 조회
    3. 쿠폰 상태, 발급 기간, 중복 발급 여부 확인
    4. 발급 수량 증가 및 발급 내역 저장
    5. 발급 결과 반환
     */

    private final CouponIssueRepository couponIssueRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    @Transactional
    public CouponIssueResponse issueCoupon(
            Long couponId,
            Long userId
    ) {
        log.info("쿠폰 발급 요청: couponId={}, userId={}", couponId, userId);

        // DB 행을 잠그지 않고 조회하며 저장 시 @Version으로 변경 충돌을 감지함
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COUPON_NOT_FOUND
                ));

        // JWT에서 가져온 사용자 ID로 사용자 조회함
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND
                ));

        // 현재 발급할 수 있는 상태와 기간인지 확인함
        validateCouponIssuable(coupon);

        // 같은 사용자가 이미 발급받은 쿠폰인지 확인함
        boolean alreadyIssued =
                couponIssueRepository.existsByCoupon_IdAndUser_Id(
                        couponId, userId
                );

        if (alreadyIssued) {
            throw new BusinessException(ErrorCode.DUPLICATE_COUPON_ISSUE);
        }

        coupon.increaseIssueQuantity();

        // 쿠폰 발급 내역 저장함
        CouponIssue couponIssue = new CouponIssue(coupon, user);
        CouponIssue savedCouponIssue = couponIssueRepository.save(couponIssue);

        log.info("쿠폰 발급 완료: issuedId={}, couponId={}, userId={}, status={}",
                savedCouponIssue.getId(),
                couponId,
                userId,
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
