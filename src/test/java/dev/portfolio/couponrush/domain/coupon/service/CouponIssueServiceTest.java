package dev.portfolio.couponrush.domain.coupon.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueCreateRequest;
import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueResponse;
import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssueStatus;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponIssueRepository;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class CouponIssueServiceTest {
    // 실제 Spring Context와 PostgreSQL을 사용하여 쿠폰 발급 Service를 검증함

    @Container
    @ServiceConnection
    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CouponIssueService couponIssueService;

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    CouponIssueRepository couponIssueRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 외래키 제약조건을 고려하여 발급 내역부터 삭제함
        couponIssueRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void issueCoupon() {
        // 정상 발급에 필요한 사용자와 쿠폰을 저장함
        User user = userRepository.saveAndFlush(
                new User("issue-service@example.com", "tester")
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon("쿠폰 발급 테스트 쿠폰", 100)
        );

        CouponIssueCreateRequest request = createRequest(user.getId());

        // 쿠폰 발급 Service를 호출함
        CouponIssueResponse response = couponIssueService.issueCoupon(coupon.getId(), request);

        Coupon savedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();

        log.info("쿠폰 발급 성공: issueId={}, couponId={}, userId={}, status={}, issuedQuantity={}",
                response.getId(),
                response.getCouponId(),
                response.getUserId(),
                response.getStatus(),
                savedCoupon.getIssuedQuantity());

        // 발급 내역이 정상적으로 생성되었는지 검증함
        assertThat(response.getId()).isNotNull();
        assertThat(response.getCouponId()).isEqualTo(coupon.getId());
        assertThat(response.getUserId()).isEqualTo(user.getId());
        assertThat(response.getStatus()).isEqualTo(CouponIssueStatus.ISSUED);
        assertThat(response.getIssuedAt()).isNotNull();

        // 쿠폰의 발급 수량이 1 증가했는지 검증함
        assertThat(savedCoupon.getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    void issueCouponWithNotFoundCouponFails() {
        // 존재하지 않는 쿠폰 ID를 준비함
        Long notFoundCouponId = 999L;

        User user = userRepository.saveAndFlush(
                new User("not-found-coupon@example.com", "tester")
        );

        CouponIssueCreateRequest request =
                createRequest(user.getId());

        // 존재하지 않는 쿠폰 발급 시 예외가 발생하는지 확인함
        try {
            couponIssueService.issueCoupon(
                    notFoundCouponId,
                    request
            );

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "없는 쿠폰 발급 실패 확인: couponId={}, message={}",
                    notFoundCouponId,
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.COUPON_NOT_FOUND);
        }
    }

    @Test
    void issueCouponWithNotFoundUserFails() {
        // 정상적으로 발급 간으한 쿠폰을 저장함
        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon("없는 사용자 테스트 쿠폰", 100));

        // 존재하지 않는 사용자 ID를 준비함
        CouponIssueCreateRequest request = createRequest(999L);

        // 존재하지 않는 사용자 발급 시 예외가 발생하는지 확인함
        try {
            couponIssueService.issueCoupon(
                    coupon.getId(),
                    request
            );

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "없는 사용자 쿠폰 발급 실패 확인: userId={}, message={}",
                    request.getUserId(),
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Test
    void issueCouponWithNotOpenCouponFails() {
        // 발급 상태가 READY인 쿠폰을 저장함
        User user = userRepository.saveAndFlush(new User("not-open@example.com", "tester"));

        Coupon coupon = couponRepository.saveAndFlush(
                new Coupon(
                        "발급 불가 상태 쿠폰",
                        1000,
                        100,
                        LocalDateTime.now().minusMinutes(10),
                        LocalDateTime.now().plusDays(1),
                        CouponStatus.READY
                )
        );

        CouponIssueCreateRequest request = createRequest(user.getId());

        // OPEN 상태가 아닌 쿠폰 발급 시 예외가 발생하는지 확인함
        try {
            couponIssueService.issueCoupon(
                    coupon.getId(),
                    request
            );

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "발급 불가 상태 쿠폰 발급 실패 확인: message={}",
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.COUPON_NOT_OPEN);
        }
    }

    @Test
    void issueCouponWithInvalidPeriodFails() {
        // 아직 발급 시작 전인 쿠폰을 저장함
        User user = userRepository.saveAndFlush(
                new User("invalid-period@example.com", "tester")
        );

        Coupon coupon = couponRepository.saveAndFlush(
                new Coupon(
                        "발급 기간 오류 쿠폰",
                        1000,
                        100,
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(2),
                        CouponStatus.OPEN
                )
        );

        CouponIssueCreateRequest request = createRequest(user.getId());

        // 발급 기간이 아닌 쿠폰 발급 시 예외가 발생하는지 확인함
        try {
            couponIssueService.issueCoupon(
                    coupon.getId(),
                    request
            );

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "발급 기간이 아닌 쿠폰 발급 실패 확인: message={}",
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_COUPON_PERIOD);
        }
    }

    @Test
    void issueCouponWithSoldOutCouponFails() {
        // 수량이 1개인 쿠폰과 첫 번째 사용자를 저장함
        User firstUser = userRepository.saveAndFlush(
                new User("sold-out-first@example.com", "tester1")
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon(
                        "품절 테스트 쿠폰",
                        1
                )
        );

        // 첫 번째 발급으로 수량을 모두 소진함
        couponIssueService.issueCoupon(
                coupon.getId(),
                createRequest(firstUser.getId())
        );

        // 두 번째 사용자를 저장함
        User secondUser = userRepository.saveAndFlush(
                new User("sold-out-second@example.com", "tester2")
        );

        // 수량이 모두 소진된 쿠폰의 발급을 시도함
        try {
            couponIssueService.issueCoupon(
                    coupon.getId(),
                    createRequest(secondUser.getId())
            );

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "품절 쿠폰 발급 실패 확인: couponId={}, message={}",
                    coupon.getId(),
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.COUPON_SOLD_OUT);
        }
    }

    @Test
    void duplicateCouponIssueFails() {
        // 사용자와 쿠폰을 저장함
        User user = userRepository.saveAndFlush(
                new User("duplicate-issue@example.com", "tester")
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon("중복 발급 테스트 쿠폰", 100)
        );

        CouponIssueCreateRequest request =
                createRequest(user.getId());

        // 첫 번째 쿠폰 발급을 성공시킴
        couponIssueService.issueCoupon(
                coupon.getId(),
                request
        );

        // 동일한 사용자로 다시 쿠폰 발급을 시도함
        try {
            couponIssueService.issueCoupon(
                    coupon.getId(),
                    request
            );

            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "중복 쿠폰 발급 실패 확인: couponId={}, userId={}, message={}",
                    coupon.getId(),
                    user.getId(),
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_COUPON_ISSUE);
        }
    }

    private CouponIssueCreateRequest createRequest(Long userId) {
        // setter 없이 테스트 요청 DTO의 private 필드에 값을 주입함
        CouponIssueCreateRequest request = new CouponIssueCreateRequest();

        ReflectionTestUtils.setField(request, "userId", userId);

        return request;
    }

    private Coupon createOpenCoupon(String name, Integer totalQuantity) {
        // 현재 발급 가능한 OPEN 상태의 쿠폰을 생성함
        return new Coupon(
                name,
                1000,
                totalQuantity,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusDays(1),
                CouponStatus.OPEN
        );
    }

}