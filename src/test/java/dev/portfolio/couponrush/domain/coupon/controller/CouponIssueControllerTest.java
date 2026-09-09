package dev.portfolio.couponrush.domain.coupon.controller;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
// MockMvc로 쿠폰 발급 API의 요청과 응답을 검증함
class CouponIssueControllerTest {

    // 인증이 테스트 목적이 아니므로 고정된 임시 해시값을 사용함
    private static final String TEST_PASSWORD_HASH = "encoded-test-password";

    @Container
    @ServiceConnection
    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    @Container
    // Redis 컨테이너 정의
    static GenericContainer<?> redisContainer =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.11-alpine")
            )
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerRedisProperties(
            DynamicPropertyRegistry registry
    ) {
        // 테스트 Redis의 동적으로 할당된 접속 정보를 Spring 설정에 등록함
        registry.add(
                "spring.data.redis.host",
                redisContainer::getHost
        );
        registry.add(
                "spring.data.redis.port",
                () -> redisContainer.getMappedPort(6379)
        );

    }

    @Autowired
    // HTTP 요청을 보내는 테스트 도구를 주입받음
    MockMvc mockMvc;

    @Autowired
    CouponIssueRepository couponIssueRepository;

    @Autowired
    CouponRepository couponRepository;

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
    void issueCoupon() throws Exception {
        // 쿠폰을 발급받을 사용자와 쿠폰을 저장함
        User user = userRepository.saveAndFlush(
                new User("controller-issue@example.com", "tester", TEST_PASSWORD_HASH)
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon("Controller 쿠폰 발급 테스트 쿠폰")
        );

        // 쿠폰 발급 API를 호출하고 응답을 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        coupon.getId()
                )
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                )
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_USER")
                                )))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.couponId").value(coupon.getId()))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.status")
                        .value(CouponIssueStatus.ISSUED.name()))
                .andExpect(jsonPath("$.issuedAt", notNullValue()));

        log.info(
                "쿠폰 발급 API 테스트 성공: couponId={}, userId={}",
                coupon.getId(),
                user.getId()
        );
    }

    @Test
    void issueCouponWithNotFoundCouponFails() throws Exception {
        Long notFoundCouponId = 999L;

        // 존재하지 않는 쿠폰 발급 요청이 404로 처리되는지 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        notFoundCouponId
                )
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("1"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_USER")
                                )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_NOT_FOUND"));

        log.info(
                "없는 쿠폰 발급 실패 테스트 성공: couponId={}",
                notFoundCouponId
        );
    }

    @Test
    void issueCouponWithNotFoundUserFails() throws Exception {
        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon("없는 사용자 테스트 쿠폰")
        );

        // 존재하지 않는 사용자 발급 요청이 404로 처리되는지 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        coupon.getId()
                )
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("999"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_USER")
                                )))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("USER_NOT_FOUND"));

        log.info(
                "없는 사용자 발급 실패 테스트 성공: userId=999"
        );
    }

    @Test
    void duplicateCouponIssueFails() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("controller-duplicate@example.com", "tester", TEST_PASSWORD_HASH)
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon("중복 발급 테스트 쿠폰")
        );

        // 첫 번째 쿠폰 발급을 성공시킴
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        coupon.getId()
                )
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                )
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_USER")
                                )))
                .andExpect(status().isCreated());

        // 동일한 사용자로 다시 발급 요청하여 중복 여부를 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        coupon.getId()
                )
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                )
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_USER")
                                )))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_COUPON_ISSUE"));

        log.info(
                "중복 쿠폰 발급 실패 테스트 성공: couponId={}, userId={}",
                coupon.getId(),
                user.getId()
        );

    }

    @Test
    void issueCouponWithoutTokenFails() throws Exception {
        // 인증되지 않은 사용자의 쿠폰 발급 요청이 차단되는지 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        1L
                ))
                .andDo(print())
                .andExpect(status().isUnauthorized());

        log.info("인증되지 않은 쿠폰 발급 요청 실패 테스트 성공");
    }

    private Coupon createOpenCoupon(String name) {
        // 현재 발급 가능한 OPEN 상태의 쿠폰을 생성함
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
