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
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    CouponIssueRepository couponIssueRepository;

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void saveOrder() {
        User user = userRepository.saveAndFlush(createUser("order-save@example.com"));
        Coupon coupon = couponRepository.saveAndFlush(createCoupon("주문 저장 테스트 쿠폰"));
        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(new CouponIssue(coupon, user));
        Order order = new Order(user, couponIssue, 10_000, 1_000);

        Order saved = orderRepository.saveAndFlush(order);

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
        User user = userRepository.saveAndFlush(createUser("order-duplicate@example.com"));
        Coupon coupon = couponRepository.saveAndFlush(createCoupon("주문 중복 테스트 쿠폰"));
        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(new CouponIssue(coupon, user));

        Order firstOrder = new Order(user, couponIssue, 10_000, 1_000);
        orderRepository.saveAndFlush(firstOrder);
        log.info("첫 번째 주문 저장 성공: couponIssueId={}", couponIssue.getId());

        Order secondOrder = new Order(user, couponIssue, 20_000, 2_000);

        try {
            orderRepository.saveAndFlush(secondOrder);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (DataIntegrityViolationException e) {
            log.info("중복 쿠폰 사용 주문 저장 실패 확인: couponIssueId={}", couponIssue.getId());
        }
    }

    private User createUser(String email) {
        return new User(email, "tester");
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
