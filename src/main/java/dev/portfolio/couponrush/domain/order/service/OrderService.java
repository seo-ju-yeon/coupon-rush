package dev.portfolio.couponrush.domain.order.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssueStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponIssueRepository;
import dev.portfolio.couponrush.domain.order.dto.OrderCreateRequest;
import dev.portfolio.couponrush.domain.order.dto.OrderResponse;
import dev.portfolio.couponrush.domain.order.entity.Order;
import dev.portfolio.couponrush.domain.order.repository.OrderRepository;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    /*
    주문 생성 흐름
    1. 사용자 조회
    2. 쿠폰 발급 내역 조회
    3. 쿠폰 발급 사용자와 주문 사용자가 같은지 확인
    4. 쿠폰 발급 상태가 ISSUED인지 확인
    5. 할인 금액과 주문 금액 비교
    6. Order 생성
    7. Order 저장
    8. CouponIssue를 USED 상태로 변경
    9. OrderResponse 반환
     */

    private final OrderRepository orderRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final UserRepository userRepository;

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {
        log.info("주문 생성 요청: userId={}, couponIssueId={}, originalAmount={}",
                request.getUserId(),
                request.getCouponIssueId(),
                request.getOriginalAmount());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND
                ));

        CouponIssue couponIssue =
                couponIssueRepository.findById(request.getCouponIssueId())
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.COUPON_ISSUE_NOT_FOUND
                        ));

        validateCouponIssueUser(couponIssue, user);
        validateCouponIssueUsable(couponIssue);

        Integer discountAmount = couponIssue.getCoupon().getDiscountAmount();

        validateOrderAmount(request.getOriginalAmount(), discountAmount);

        Order order = new Order(
                user,
                couponIssue,
                request.getOriginalAmount(),
                discountAmount
        );

        // 주문 ID를 먼저 생성하기 위해 주문을 저장함
        Order savedOrder = orderRepository.save(order);

        // 생성된 주문 ID를 쿠폰 발급 내역에 기록하고 사용 처리함
        couponIssue.markUsed(savedOrder.getId());

        log.info(
                "주문 생성 완료: orderId={}, userId={}, couponIssueId={}, finalAmount={}",
                savedOrder.getId(),
                user.getId(),
                couponIssue.getId(),
                savedOrder.getFinalAmount()
        );

        return OrderResponse.from(savedOrder);
    }

    private void validateCouponIssueUser(
            CouponIssue couponIssue,
            User user
    ) {
        if (!couponIssue.getUser().getId().equals(user.getId())) {
            throw new BusinessException(
                    ErrorCode.COUPON_ISSUE_USER_MISMATCH
            );
        }
    }

    private void validateCouponIssueUsable(
            CouponIssue couponIssue
    ) {
        if (couponIssue.getStatus() != CouponIssueStatus.ISSUED) {
            throw new BusinessException(
                    ErrorCode.COUPON_ISSUE_NOT_USABLE
            );
        }
    }

    private void validateOrderAmount(
            Integer originalAmount,
            Integer discountAmount
    ) {
        if (originalAmount < discountAmount) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_AMOUNT
            );
        }
    }
}
