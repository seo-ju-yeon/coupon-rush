package dev.portfolio.couponrush.domain.order.repository;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponIssueRepository;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import dev.portfolio.couponrush.domain.order.entity.Order;
import dev.portfolio.couponrush.domain.order.entity.OrderStatus;
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
// 실제 PostgreSQL에서 주문 저장과 쿠폰 중복 사용 제약조건을 검증함
class OrderRepositoryTest {

    // 인증이 테스트 목적이 아니므로 고정된 임시 해시값을 사용함
    private static final String TEST_PASSWORD_HASH = "encoded-test-password";

    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    // 주문을 저장할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    OrderRepository orderRepository;

    // 주문에 사용할 쿠폰 발급 내역을 저장할 Repository를 주입받음
    @Autowired
    CouponIssueRepository couponIssueRepository;

    // 주문에 사용할 쿠폰을 저장할 Repository를 주입받음
    @Autowired
    CouponRepository couponRepository;

    // 주문에 사용할 사용자를 저장할 Repository를 주입받음
    @Autowired
    UserRepository userRepository;

    // JPA가 저장한 실제 DB 원본 값을 확인할 때 사용함
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void saveOrder() {
        // 외래 키 관계를 만족하도록 사용자, 쿠폰, 발급 내역을 먼저 저장함
        User user = userRepository.saveAndFlush(createUser("order-save@example.com"));
        Coupon coupon = couponRepository.saveAndFlush(createCoupon("주문 저장 테스트 쿠폰"));
        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(new CouponIssue(coupon, user));
        // 주문을 생성하고 저장함
        Order order = new Order(user, couponIssue, 10_000, 1_000);

        Order saved = orderRepository.saveAndFlush(order);

        // 주문 상태와 외래 키가 DB에 올바르게 저장되었는지 확인함
        String savedStatus = jdbcTemplate.queryForObject(
                "select status from orders where id = ?",
                String.class,
                saved.getId()
        );
        Long savedCouponIssueId = jdbcTemplate.queryForObject(
                "select coupon_issue_id from orders where id = ?",
                Long.class,
                saved.getId()
        );

        log.info("저장된 주문: id={}, userId={}, couponIssueId={}, originalAmount={}, discountAmount={}, finalAmount={}, status={}, dbStatus={}, createdAt={}",
                saved.getId(),
                saved.getUser().getId(),
                savedCouponIssueId,
                saved.getOriginalAmount(),
                saved.getDiscountAmount(),
                saved.getFinalAmount(),
                saved.getStatus(),
                savedStatus,
                saved.getCreatedAt()
        );

        // 주문 금액, 상태, 생성 일시를 검증함
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getCouponIssue().getId()).isEqualTo(couponIssue.getId());
        assertThat(savedCouponIssueId).isEqualTo(couponIssue.getId());
        assertThat(saved.getOriginalAmount()).isEqualTo(10_000);
        assertThat(saved.getDiscountAmount()).isEqualTo(1_000);
        assertThat(saved.getFinalAmount()).isEqualTo(9_000);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(savedStatus).isEqualTo("CREATED");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateCouponIssueOrderShouldFail() {
        // 하나의 발급 쿠폰을 사용한 첫 번째 주문을 저장함
        User user = userRepository.saveAndFlush(createUser("order-duplicate@example.com"));
        Coupon coupon = couponRepository.saveAndFlush(createCoupon("주문 중복 테스트 쿠폰"));
        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(new CouponIssue(coupon, user));

        Order firstOrder = new Order(user, couponIssue, 10_000, 1_000);
        orderRepository.saveAndFlush(firstOrder);
        log.info("첫 번째 주문 저장 성공: couponIssueId={}", couponIssue.getId());

        // 동일한 발급 쿠폰을 다시 사용하는 주문을 준비함
        Order secondOrder = new Order(user, couponIssue, 20_000, 2_000);

        // 발급 쿠폰 하나당 주문 하나만 허용하는 UNIQUE 제약조건을 확인함
        try {
            orderRepository.saveAndFlush(secondOrder);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (DataIntegrityViolationException e) {
            log.info("중복 쿠폰 사용 주문 저장 실패 확인: couponIssueId={}", couponIssue.getId());
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
