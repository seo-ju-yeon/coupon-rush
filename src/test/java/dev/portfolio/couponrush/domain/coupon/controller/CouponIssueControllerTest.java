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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
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

    @Container
    @ServiceConnection
    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

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
                new User("controller-issue@example.com", "tester")
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon("Controller 쿠폰 발급 테스트 쿠폰")
        );

        String requestBody = """
                {
                  "userId": %d
                }
                """.formatted(user.getId());

        // 쿠폰 발급 API를 호출하고 응답을 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        coupon.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
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

        String requestBody = """
                {
                    "userId": 1
                }
                """;

        // 존재하지 않는 쿠폰 발급 요청이 404로 처리되는지 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        notFoundCouponId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
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

        String requestBody = """
                {
                    "userId": 999
                }
                """;

        // 존재하지 않는 사용자 발급 요청이 404로 처리되는지 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        coupon.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
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
                new User("controller-duplicate@example.com", "tester")
        );

        Coupon coupon = couponRepository.saveAndFlush(
                createOpenCoupon("중복 발급 테스트 쿠폰")
        );

        String requestBody = """
                {
                  "userId": %d
                }
                """.formatted(user.getId());

        // 첫 번째 쿠폰 발급을 성공시킴
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        coupon.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        // 동일한 사용자로 다시 발급 요청하여 중복 여부를 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        coupon.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
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
    void issueCouponWithInvalidRequestFails() throws Exception {
        String requestBody = """
                {
                  "userId": null
                }
                """;

        // 사용자 ID가 없는 요청이 400으로 처리되는지 검증함
        mockMvc.perform(post(
                        "/api/coupons/{couponId}/issues",
                        1L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        log.info("쿠폰 발급 요청값 검증 실패 테스트 성공");
    }

    private Coupon createOpenCoupon(String name) {
        // 현재 발급 간으한 OPEN 상태의 쿠폰을 생성함
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