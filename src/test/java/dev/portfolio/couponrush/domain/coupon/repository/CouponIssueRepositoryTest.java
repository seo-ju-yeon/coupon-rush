package dev.portfolio.couponrush.domain.coupon.repository;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssueStatus;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@Testcontainers
@Log4j2
// 실제 PostgreSQL에서 쿠폰 발급 내역 저장과 제약조건을 검증함
class CouponIssueRepositoryTest {

    // 인증이 테스트 목적이 아니므로 고정된 임시 해시값을 사용함
    private static final String TEST_PASSWORD_HASH = "encoded-test-password";

    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    // 쿠폰 발급 내역을 저장할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    CouponIssueRepository couponIssueRepository;

    // 테스트용 쿠폰을 저장할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    CouponRepository couponRepository;

    // 테스트용 사용자를 저장할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    UserRepository userRepository;

    // JPA가 저장한 실제 DB 원본 값을 확인할 때 사용함
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void saveCouponIssue() {
        // 외래 키 관계를 만족하도록 사용자와 쿠폰을 먼저 저장함
        User user = userRepository.saveAndFlush(createUser("issue-save@example.com"));
        Coupon coupon = couponRepository.saveAndFlush(createCoupon("발급 저장 테스트 쿠폰"));
        CouponIssue couponIssue = new CouponIssue(coupon, user);

        // 쿠폰 발급 내역을 저장하고 즉시 DB에 반영함
        CouponIssue saved = couponIssueRepository.saveAndFlush(couponIssue);

        // JPA 객체 값과 DB에 저장된 원본 값을 비교하기 위해 조회함
        String savedStatus = jdbcTemplate.queryForObject(
                "select status from coupon_issues where id = ?",
                String.class,
                saved.getId()
        );
        Long savedUsedOrderId = jdbcTemplate.queryForObject(
                "select used_order_id from coupon_issues where id = ?",
                Long.class,
                saved.getId()
        );

        log.info("저장된 쿠폰 발급 내역: id={}, couponId={}, userId={}, status={}, dbStatus={}, issuedAt={}, usedAt={}, usedOrderId={}",
                saved.getId(),
                saved.getCoupon().getId(),
                saved.getUser().getId(),
                saved.getStatus(),
                savedStatus,
                saved.getIssuedAt(),
                saved.getUsedAt(),
                savedUsedOrderId
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCoupon().getId()).isEqualTo(coupon.getId());
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getStatus()).isEqualTo(CouponIssueStatus.ISSUED);
        assertThat(savedStatus).isEqualTo("ISSUED");
        assertThat(saved.getIssuedAt()).isNotNull();
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getUsedOrderId()).isNull();
        assertThat(savedUsedOrderId).isNull();
    }

    @Test
    void duplicateCouponIssueShouldFail() {
        // 동일한 쿠폰과 사용자의 첫 번째 발급 내역을 저장함
        User user = userRepository.saveAndFlush(createUser("issue-duplicate@example.com"));
        Coupon coupon = couponRepository.saveAndFlush(createCoupon("중복 발급 테스트 쿠폰"));

        CouponIssue firstIssue = new CouponIssue(coupon, user);
        couponIssueRepository.saveAndFlush(firstIssue);
        log.info("첫 번째 쿠폰 발급 저장 성공: couponId={}, userId={}", coupon.getId(), user.getId());

        CouponIssue secondIssue = new CouponIssue(coupon, user);

        // UNIQUE 제약조건으로 중복 발급이 차단되는지 확인함
        try {
            couponIssueRepository.saveAndFlush(secondIssue);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (DataIntegrityViolationException e) {
            log.info("중복 쿠폰 발급 저장 실패 확인: couponId={}, userId={}", coupon.getId(), user.getId());
        }
    }

    private User createUser(String email) {
        return new User(email, "tester", TEST_PASSWORD_HASH);
    }

    private Coupon createCoupon(String name) {
        return new Coupon(
                name,
                1000,
                100,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusDays(1),
                CouponStatus.OPEN
        );
    }
}
