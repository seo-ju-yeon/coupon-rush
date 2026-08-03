package dev.portfolio.couponrush.domain.coupon.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.coupon.dto.CouponCreateRequest;
import dev.portfolio.couponrush.domain.coupon.dto.CouponResponse;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@Testcontainers
@Log4j2
class CouponServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CouponService couponService;

    @Autowired
    CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
    }

    @Test
    void createCoupon() {
        // 쿠폰 생성 성공 확인
        CouponCreateRequest request = createRequest(
                "선착순 할인 쿠폰",
                1000,
                100,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusDays(1)
        );

        CouponResponse response = couponService.createCoupon(request);

        log.info(
                "쿠폰 생성 성공: id={}, name={}, status={}, issuedQuantity={}",
                response.getId(),
                response.getName(),
                response.getStatus(),
                response.getIssuedQuantity()
        );

        assertThat(response.getId()).isNotNull();
        assertThat(response.getName()).isEqualTo("선착순 할인 쿠폰");
        assertThat(response.getDiscountAmount()).isEqualTo(1000);
        assertThat(response.getTotalQuantity()).isEqualTo(100);
        assertThat(response.getIssuedQuantity()).isEqualTo(0);
        assertThat(response.getStatus()).isEqualTo(CouponStatus.READY);
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    void getCoupon() {
        // 쿠폰 단건 조회 확인
        CouponCreateRequest request = createRequest(
                "조회 테스트 쿠폰",
                2000,
                50,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusDays(1)
        );

        CouponResponse createdCoupon = couponService.createCoupon(request);
        CouponResponse foundCoupon =
                couponService.getCoupon(createdCoupon.getId());

        log.info(
                "쿠폰 조회 성공: id={}, name={}",
                foundCoupon.getId(),
                foundCoupon.getName()
        );

        assertThat(foundCoupon.getId()).isEqualTo(createdCoupon.getId());
        assertThat(foundCoupon.getName()).isEqualTo("조회 테스트 쿠폰");
        assertThat(foundCoupon.getDiscountAmount()).isEqualTo(2000);
    }

    @Test
    void getCoupons() {
        // 쿠폰 목록 조회 확인
        CouponCreateRequest firstRequest = createRequest(
                "첫 번째 쿠폰",
                1000,
                100,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusDays(1)
        );

        CouponCreateRequest secondRequest = createRequest(
                "두 번째 쿠폰",
                2000,
                200,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusDays(1)
        );

        couponService.createCoupon(firstRequest);
        couponService.createCoupon(secondRequest);

        List<CouponResponse> responses = couponService.getCoupons();

        log.info("쿠폰 목록 조회 성공: count={}", responses.size());

        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(CouponResponse::getName)
                .containsExactlyInAnyOrder("첫 번째 쿠폰", "두 번째 쿠폰");
    }

    @Test
    void getCouponWithNotFoundCouponFails() {
        // 없는 쿠폰 조회 시 예외 확인
        Long notFoundCouponId = 999L;

        try {
            couponService.getCoupon(notFoundCouponId);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "없는 쿠폰 조회 실패 확인: couponId={}, message={}",
                    notFoundCouponId,
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.COUPON_NOT_FOUND);
        }
    }

    @Test
    void createCouponWithInvalidPeriodFails() {
        // 시작일과 종료일이 잘못된 경우 예외 확인
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1);
        LocalDateTime endsAt = LocalDateTime.now();

        CouponCreateRequest request = createRequest(
                "잘못된 기간 쿠폰",
                1000,
                100,
                startsAt,
                endsAt
        );

        try {
            couponService.createCoupon(request);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info(
                    "잘못된 쿠폰 기간 검증 실패 확인: message={}",
                    e.getMessage()
            );

            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_COUPON_PERIOD);
        }
    }

    private CouponCreateRequest createRequest(
            String name,
            Integer discountAmount,
            Integer totalQuantity,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        CouponCreateRequest request = new CouponCreateRequest();

        // ReflectionTestUtils를 사용하는 이유는 CouponCreateRequest에 setter가 없기 때문
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "discountAmount", discountAmount);
        ReflectionTestUtils.setField(request, "totalQuantity", totalQuantity);
        ReflectionTestUtils.setField(request, "startsAt", startsAt);
        ReflectionTestUtils.setField(request, "endsAt", endsAt);

        return request;
    }
}