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
// 실제 Spring Context와 PostgreSQL을 사용하여 쿠폰 Service를 검증함
class CouponServiceTest {

    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    // 테스트 대상 Service를 실제 Spring Bean으로 주입받음
    @Autowired
    CouponService couponService;

    // 테스트 데이터 정리에 사용할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        // 테스트 간 쿠폰 데이터가 섞이지 않도록 기존 데이터를 삭제함
        couponRepository.deleteAll();
    }

    @Test
    void createCoupon() {
        // 쿠폰 생성 요청을 준비함
        CouponCreateRequest request = createRequest(
                "선착순 할인 쿠폰",
                1000,
                100,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusDays(1)
        );

        // Service를 호출하여 쿠폰을 생성함
        CouponResponse response = couponService.createCoupon(request);

        log.info(
                "쿠폰 생성 성공: id={}, name={}, status={}, issuedQuantity={}",
                response.getId(),
                response.getName(),
                response.getStatus(),
                response.getIssuedQuantity()
        );

        // 생성된 쿠폰의 기본값과 응답 변환 결과를 검증함
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
        // 조회할 쿠폰의 생성 요청을 준비함
        CouponCreateRequest request = createRequest(
                "조회 테스트 쿠폰",
                2000,
                50,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusDays(1)
        );

        // 쿠폰을 생성한 뒤 ID로 다시 조회함
        CouponResponse createdCoupon = couponService.createCoupon(request);
        CouponResponse foundCoupon =
                couponService.getCoupon(createdCoupon.getId());

        log.info(
                "쿠폰 조회 성공: id={}, name={}",
                foundCoupon.getId(),
                foundCoupon.getName()
        );

        // 생성한 쿠폰과 조회한 쿠폰의 값이 일치하는지 검증함
        assertThat(foundCoupon.getId()).isEqualTo(createdCoupon.getId());
        assertThat(foundCoupon.getName()).isEqualTo("조회 테스트 쿠폰");
        assertThat(foundCoupon.getDiscountAmount()).isEqualTo(2000);
    }

    @Test
    void getCoupons() {
        // 목록 조회를 위한 쿠폰 두 개를 준비함
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

        // 쿠폰 두 개를 생성함
        couponService.createCoupon(firstRequest);
        couponService.createCoupon(secondRequest);

        // 쿠폰 목록을 조회함
        List<CouponResponse> responses = couponService.getCoupons();

        log.info("쿠폰 목록 조회 성공: count={}", responses.size());

        // 생성한 쿠폰 두 개가 목록에 포함되었는지 검증함
        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(CouponResponse::getName)
                .containsExactlyInAnyOrder("첫 번째 쿠폰", "두 번째 쿠폰");
    }

    @Test
    void getCouponWithNotFoundCouponFails() {
        // 존재하지 않는 쿠폰 ID를 준비함
        Long notFoundCouponId = 999L;

        // 없는 쿠폰 조회 시 비즈니스 예외가 발생하는지 확인함
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
        // 종료일이 시작일보다 빠른 잘못된 기간을 준비함
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1);
        LocalDateTime endsAt = LocalDateTime.now();

        CouponCreateRequest request = createRequest(
                "잘못된 기간 쿠폰",
                1000,
                100,
                startsAt,
                endsAt
        );

        // 잘못된 기간으로 쿠폰 생성 시 예외가 발생하는지 확인함
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
        // setter 없이 테스트 요청 DTO의 private 필드에 값을 주입함
        CouponCreateRequest request = new CouponCreateRequest();

        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "discountAmount", discountAmount);
        ReflectionTestUtils.setField(request, "totalQuantity", totalQuantity);
        ReflectionTestUtils.setField(request, "startsAt", startsAt);
        ReflectionTestUtils.setField(request, "endsAt", endsAt);

        return request;
    }
}
