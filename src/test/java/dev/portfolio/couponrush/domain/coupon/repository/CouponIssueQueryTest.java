package dev.portfolio.couponrush.domain.coupon.repository;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Log4j2
// 실제 PostgreSQL에서 쿠폰 발급 내역 조회 메서드를 검증함
class CouponIssueQueryTest {

    // 인증이 테스트 목적이 아니므로 고정된 임시 해시값을 사용함
    private static final String TEST_PASSWORD_HASH = "encoded-test-password";

    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    // 중복 발급 여부를 확인할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    CouponIssueRepository couponIssueRepository;

    // 테스트용 쿠폰을 저장할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    CouponRepository couponRepository;

    // 테스트용 사용자를 저장할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 외래 키 제약조건을 고려하여 발급 내역부터 삭제함
        couponIssueRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void existsByCouponIdAndUserIdReturnsTrueWhenIssueExists() {
        // 쿠폰 발급 내역을 저장하기 위한 사용자와 쿠폰을 생성함
        User user = userRepository.saveAndFlush(
                new User("query@example.com", "queryUser", TEST_PASSWORD_HASH)
        );

        Coupon coupon = couponRepository.saveAndFlush(
                new Coupon(
                        "조회 테스트 쿠폰",
                        1000,
                        100,
                        LocalDateTime.now().minusMinutes(10),
                        LocalDateTime.now().plusDays(1),
                        CouponStatus.OPEN
                )
        );

        CouponIssue couponIssue = new CouponIssue(coupon, user);
        couponIssueRepository.saveAndFlush(couponIssue);

        // 저장된 쿠폰 발급 내역이 존재하는지 조회함
        boolean result = couponIssueRepository
                .existsByCoupon_IdAndUser_Id(
                        coupon.getId(),
                        user.getId()
                );

        log.info(
                "쿠폰 발급 내역 존재 여부: couponId={}, userId={}, result={}",
                coupon.getId(),
                user.getId(),
                result
        );

        assertThat(result).isTrue();
    }

    @Test
    void existsByCouponIdAndUserIdReturnsFalseWhenIssueDoesNotExist() {
        // 발급 내역을 저장하지 않은 사용자와 쿠폰을 생성함
        User user = userRepository.saveAndFlush(
                new User("query-false@example.com", "queryUser", TEST_PASSWORD_HASH)
        );

        Coupon coupon = couponRepository.saveAndFlush(
                new Coupon(
                        "미발급 테스트 쿠폰",
                        1000,
                        100,
                        LocalDateTime.now().minusMinutes(10),
                        LocalDateTime.now().plusDays(1),
                        CouponStatus.OPEN
                )
        );

        // 저장되지 않은 발급 내역을 조회하여 false인지 확인함
        boolean result = couponIssueRepository
                .existsByCoupon_IdAndUser_Id(
                        coupon.getId(),
                        user.getId()
                );

        log.info(
                "쿠폰 발급 내역 미존재 여부: couponId={}, userId={}, result={}",
                coupon.getId(),
                user.getId(),
                result
        );

        assertThat(result).isFalse();
    }
}
