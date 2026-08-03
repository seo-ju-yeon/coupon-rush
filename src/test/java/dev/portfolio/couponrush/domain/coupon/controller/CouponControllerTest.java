package dev.portfolio.couponrush.domain.coupon.controller;

import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Log4j2
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class CouponControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
    }

    @Test
    void createCoupon() throws Exception {
        String requestBody = """
                {
                "name": "선착순 할인 쿠폰",
                "discountAmount": 1000,
                "totalQuantity": 100,
                "startsAt": "2026-08-10T10:00:00",
                "endsAt": "2026-08-10T18:00:00"
                }
                """;

        mockMvc.perform(post("/api/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("선착순 할인 쿠폰"))
                .andExpect(jsonPath("$.discountAmount").value(1000))
                .andExpect(jsonPath("$.totalQuantity").value(100))
                .andExpect(jsonPath("$.issuedQuantity").value(0))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));

        log.info("쿠폰 생성 API 테스트 성공");
    }

    @Test
    void getCoupons() throws Exception {
        String firstRequest = """
                {
                  "name": "첫 번째 쿠폰",
                  "discountAmount": 1000,
                  "totalQuantity": 100,
                  "startsAt": "2026-08-10T10:00:00",
                  "endsAt": "2026-08-10T18:00:00"
                }
                """;

        String secondRequest = """
                {
                  "name": "두 번째 쿠폰",
                  "discountAmount": 2000,
                  "totalQuantity": 200,
                  "startsAt": "2026-08-10T10:00:00",
                  "endsAt": "2026-08-10T18:00:00"
                }
                """;

        mockMvc.perform(post("/api/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/coupons"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        log.info("쿠폰 목록 조회 API 테스트 성공");
    }

    @Test
    void createCouponWithInvalidRequestFails() throws Exception {
        String requestBody = """
                {
                  "name": "",
                  "discountAmount": 0,
                  "totalQuantity": -1,
                  "startsAt": null,
                  "endsAt": null
                }
                """;

        mockMvc.perform(post("/api/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        log.info("쿠폰 생성 요청값 검증 실패 테스트 성공");
    }

    @Test
    void getCouponWithNotFoundCouponFails() throws Exception {
        Long notFoundCouponId = 999L;

        mockMvc.perform(get("/api/coupons/{couponId}", notFoundCouponId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("쿠폰을 찾을 수 없습니다."));

        log.info("없는 쿠폰 조회 실패 테스트 성공: couponId={}", notFoundCouponId);
    }
}