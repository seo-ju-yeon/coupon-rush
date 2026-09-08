package dev.portfolio.couponrush.domain.order.controller;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponIssue;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponIssueRepository;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import dev.portfolio.couponrush.domain.order.repository.OrderRepository;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Log4j2
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class OrderControllerTest {

    // 인증이 테스트 목적이 아니므로 고정된 임시 해시값을 사용함
    private static final String TEST_PASSWORD_HASH = "encoded-test-password";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    CouponIssueRepository couponIssueRepository;

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    UserRepository userRepository;

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
        couponIssueRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createOrder() throws Exception {
        // 주문 사용자와 쿠폰 발급 내역을 준비함
        User user = userRepository.saveAndFlush(
                new User(
                        "order-controller@example.com",
                        "tester",
                        TEST_PASSWORD_HASH
                )
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon()
        );

        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(
                new CouponIssue(coupon, user)
        );

        String requestBody = """
                {
                  "couponIssueId": %d,
                  "originalAmount": 10000
                }
                """.formatted(couponIssue.getId());

        // 주문 생성 API를 호출하고 응답을 검증함
        mockMvc.perform(post("/api/orders")
                        // 쿠폰을 발급받은 사용자가 로그인한 상황을 재현함
                        .with(loginAs(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.couponIssueId")
                        .value(couponIssue.getId()))
                .andExpect(jsonPath("$.originalAmount").value(10000))
                .andExpect(jsonPath("$.discountAmount").value(1000))
                .andExpect(jsonPath("$.finalAmount").value(9000))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        log.info(
                "주문 생성 API 테스트 성공: userId={}, couponIssueId={}",
                user.getId(),
                couponIssue.getId()
        );
    }

    @Test
    void createOrderWithNotFoundCouponIssueFails() throws Exception {
        User user = userRepository.saveAndFlush(
                new User(
                        "not-found-order-issue@example.com",
                        "tester",
                        TEST_PASSWORD_HASH
                )
        );

        String requestBody = """
                {
                  "couponIssueId": 999,
                  "originalAmount": 10000
                }
                """;

        // 존재하지 않는 쿠폰 발급 내역으로 주문 생성 시 404를 반환하는지 검증함
        mockMvc.perform(post("/api/orders")
                        .with(loginAs(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_ISSUE_NOT_FOUND"));

        log.info("없는 쿠폰 발급 내역 주문 실패 테스트 성공");
    }

    @Test
    void createOrderWithDifferentUserCouponIssueFails() throws Exception {
        User issueUser = userRepository.saveAndFlush(
                new User(
                        "issue-owner@example.com",
                        "owner",
                        TEST_PASSWORD_HASH
                )
        );

        User differentUser = userRepository.saveAndFlush(
                new User(
                        "different-order-user@example.com",
                        "different",
                        TEST_PASSWORD_HASH
                )
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon()
        );

        CouponIssue couponIssue = couponIssueRepository.saveAndFlush(
                new CouponIssue(coupon, issueUser)
        );

        String requestBody = """
                {
                  "couponIssueId": %d,
                  "originalAmount": 10000
                }
                """.formatted(couponIssue.getId());

        // 다른 사용자의 쿠폰으로 주문 생성 시 403을 반환하는지 검증함
        mockMvc.perform(post("/api/orders")
                        // 쿠폰 소유자가 아닌 사용자가 로그인한 상황을 재현함
                        .with(loginAs(differentUser.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_ISSUE_USER_MISMATCH"));

        log.info("다른 사용자 쿠폰 주문 실패 테스트 성공");
    }

    @Test
    void createOrderWithUsedCouponIssueFails() throws Exception {
        User user = userRepository.saveAndFlush(
                new User(
                        "used-order-issue@example.com",
                        "tester",
                        TEST_PASSWORD_HASH
                )
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon()
        );

        CouponIssue couponIssue = new CouponIssue(coupon, user);

        // 실제 주문 ID 없이 사용 상태만 만들어 테스트함
        couponIssue.markUsed(null);
        couponIssueRepository.saveAndFlush(couponIssue);

        String requestBody = """
                {
                  "couponIssueId": %d,
                  "originalAmount": 10000
                }
                """.formatted(couponIssue.getId());

        // 이미 사용된 쿠폰으로 주문 생성 시 409를 반환하는지 검증함
        mockMvc.perform(post("/api/orders")
                        .with(loginAs(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_ISSUE_NOT_USABLE"));

        log.info("이미 사용된 쿠폰 주문 실패 테스트 성공");
    }

    @Test
    void createOrderWithInvalidRequestFails() throws Exception {
        String requestBody = """
                {
                  "couponIssueId": null,
                  "originalAmount": -1
                }
                """;

        // 잘못된 요청값으로 주문 생성 시 400을 반환하는지 검증함
        mockMvc.perform(post("/api/orders")
                        // 요청값 검증이 목적이므로 임의의 로그인 사용자 ID를 사용함
                        .with(loginAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        log.info("주문 생성 요청값 검증 실패 테스트 성공");
    }

    private Coupon createOpenCoupon() {
        // 현재 발급 가능한 OPEN 상태의 쿠폰을 생성함
        return new Coupon(
                "주문 Controller 테스트 쿠폰",
                1000,
                100,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusDays(1),
                CouponStatus.OPEN
        );
    }

    // JWT의 subject에 로그인 사용자 ID를 설정함 (JWT생성 중복 줄이기 위함)
    private RequestPostProcessor loginAs(Long userId) {
        return jwt()
                .jwt(jwtBuilder ->
                        jwtBuilder.subject(userId.toString())
                )
                .authorities(
                        new SimpleGrantedAuthority("ROLE_USER")
                );
    }
}
