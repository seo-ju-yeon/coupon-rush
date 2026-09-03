package dev.portfolio.couponrush.domain.order.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssueStatus;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponIssueRepository;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import dev.portfolio.couponrush.domain.order.dto.OrderCreateRequest;
import dev.portfolio.couponrush.domain.order.dto.OrderResponse;
import dev.portfolio.couponrush.domain.order.entity.OrderStatus;
import dev.portfolio.couponrush.domain.order.repository.OrderRepository;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Log4j2
@SpringBootTest
@Testcontainers
class OrderServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    OrderService orderService;

    @Autowired
    OrderRepository orderRepository;  // 주문 저장

    @Autowired
    CouponIssueRepository couponIssueRepository;  // 쿠폰 발급 내역 저장

    @Autowired
    CouponRepository couponRepository;  // 쿠폰 저장

    @Autowired
    UserRepository userRepository;  // 사용자 저장

    @Autowired
    JdbcTemplate jdbcTemplate; // 테스트 준비 과정에서 SQL을 직접 실행하기 위해 사용

    @BeforeEach
    void setUp() {
        // 기존에는 외래 키 제약조건을 고려해 주문부터 삭제했으나 순환 참조로 실패함
        // used_order_id 참조를 먼저 해제한 후 주문부터 역순으로 테스트 데이터를 삭제함
        jdbcTemplate.update(
                "UPDATE coupon_issues SET used_order_id = NULL"
        );
        orderRepository.deleteAll();
        // 쿠폰 발급 내역에 연결된 데이터를 삭제함
        couponIssueRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createOrder() {
        // 주문 사용자, 쿠폰, 쿠폰 발급 내역을 준비함
        User user = userRepository.saveAndFlush(
                new User("order-service@example.com", "tester")
        );

        Coupon coupon = couponRepository.saveAndFlush(createOpenCoupon());

        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(new CouponIssue(coupon, user));

        OrderCreateRequest request = createRequest(
                user.getId(),
                couponIssue.getId(),
                10_000
        );

        // 주문 생성 Service를 호출함
        OrderResponse response = orderService.createOrder(request);

        CouponIssue usedCouponIssue = couponIssueRepository
                .findById(couponIssue.getId())
                .orElseThrow();

        log.info(
                "주문 생성 성공: orderId={}, userId={}, couponIssueId={}, " +
                        "originalAmount={}, discountAmount={}, finalAmount={}, status={}",
                response.getId(),
                response.getUserId(),
                response.getCouponIssueId(),
                response.getOriginalAmount(),
                response.getDiscountAmount(),
                response.getFinalAmount(),
                response.getStatus()
        );

        // 주문 응답 정보를 검증함
        assertThat(response.getId()).isNotNull();
        assertThat(response.getUserId()).isEqualTo(user.getId());
        assertThat(response.getCouponIssueId())
                .isEqualTo(couponIssue.getId());
        assertThat(response.getOriginalAmount()).isEqualTo(10_000);
        assertThat(response.getDiscountAmount()).isEqualTo(1_000);
        assertThat(response.getFinalAmount()).isEqualTo(9_000);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.getCreatedAt()).isNotNull();

        // 주문 생성 후 쿠폰 발급 내역이 사용 처리되었는지 검증함
        assertThat(usedCouponIssue.getStatus())
                .isEqualTo(CouponIssueStatus.USED);
        assertThat(usedCouponIssue.getUsedAt()).isNotNull();
        assertThat(usedCouponIssue.getUsedOrderId())
                .isEqualTo(response.getId());
    }

    @Test
    void createOrderWithNotFoundCouponIssueFails() {
        User user = userRepository.saveAndFlush(
                new User("not-found-issue@example.com", "tester")
        );

        OrderCreateRequest request = createRequest(
                user.getId(),
                999L,
                10_000
        );

        // 존재하지 않는 쿠폰 발급 내역으로 주문 생성 시 예외가 발생하는지 확인함
        try {
            orderService.createOrder(request);

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "없는 쿠폰 발급 내역 주문 실패 확인: couponIssueId={}, message={}",
                    request.getCouponIssueId(),
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.COUPON_ISSUE_NOT_FOUND);
        }
    }

    @Test
    void createOrderWithNotFoundUserFails() {
        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon()
        );

        User issueUser = userRepository.saveAndFlush(
                new User("issue-user@example.com", "issueUser")
        );

        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(
                new CouponIssue(coupon, issueUser)
        );

        OrderCreateRequest request = createRequest(
                999L,
                couponIssue.getId(),
                10_000
        );

        // 존재하지 않는 사용자로 주문 생성 시 예외가 발생하는지 확인함
        try {
            orderService.createOrder(request);

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "없는 사용자 주문 실패 확인: userId={}, message={}",
                    request.getUserId(),
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Test
    void createOrderWithDifferentUserCouponIssueFails() {
        User issueUser = userRepository.saveAndFlush(
                new User("owner@example.com", "owner")
        );

        User differentUser = userRepository.saveAndFlush(
                new User("different@example.com", "different")
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon()
        );

        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(
                new CouponIssue(coupon, issueUser)
        );

        OrderCreateRequest request = createRequest(
                differentUser.getId(),
                couponIssue.getId(),
                10_000
        );

        // 다른 사용자의 쿠폰 발급 내역으로 주문 시 예외가 발생하는지 확인함
        try {
            orderService.createOrder(request);

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "다른 사용자 쿠폰 사용 실패 확인: userId={}, couponIssueId={}",
                    differentUser.getId(),
                    couponIssue.getId()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.COUPON_ISSUE_USER_MISMATCH);
        }
    }

    @Test
    void createOrderWithUsedCouponIssueFails() {
        User user = userRepository.saveAndFlush(
                new User("used-issue@example.com", "tester")
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon()
        );

        CouponIssue couponIssue = new CouponIssue(coupon, user);

        // 이미 사용된 쿠폰 발급 내역으로 만들어 테스트함
        couponIssue.markUsed(null);

        couponIssueRepository.saveAndFlush(couponIssue);

        OrderCreateRequest request = createRequest(
                user.getId(),
                couponIssue.getId(),
                10_000
        );

        // 이미 사용된 쿠폰 발급 내역으로 주문 시 예외가 발생하는지 확인함
        try {
            orderService.createOrder(request);

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "이미 사용된 쿠폰 주문 실패 확인: couponIssueId={}, message={}",
                    couponIssue.getId(),
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.COUPON_ISSUE_NOT_USABLE);
        }
    }

    @Test
    void createOrderWithInvalidAmountFails() {
        User user = userRepository.saveAndFlush(
                new User("invalid-amount@example.com", "tester")
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon()
        );

        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(
                new CouponIssue(coupon, user)
        );

        OrderCreateRequest request = createRequest(
                user.getId(),
                couponIssue.getId(),
                500
        );

        // 주문 금액보다 할인 금액이 큰 경우 예외가 발생하는지 확인함
        try {
            orderService.createOrder(request);

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "주문 금액 검증 실패 확인: originalAmount={}, message={}",
                    request.getOriginalAmount(),
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ORDER_AMOUNT);
        }
    }

    private OrderCreateRequest createRequest(
            Long userId,
            Long couponIssueId,
            Integer originalAmount
    ) {
        // setter 없이 테스트 요청 DTO의 private 필드에 값을 주입함
        OrderCreateRequest request = new OrderCreateRequest();

        ReflectionTestUtils.setField(request, "userId", userId);
        ReflectionTestUtils.setField(
                request,
                "couponIssueId",
                couponIssueId
        );
        ReflectionTestUtils.setField(
                request,
                "originalAmount",
                originalAmount
        );

        return request;
    }

    private Coupon createOpenCoupon() {
        // 현재 발급 가능한 OPEN 상태의 쿠폰을 생성함
        return new Coupon(
                "주문 테스트 쿠폰",
                1_000,
                100,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusDays(1),
                CouponStatus.OPEN
        );
    }

}