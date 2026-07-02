package dev.portfolio.couponrush.domain.coupon.repository;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Log4j2
class CouponRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;  // DB에 저장된 원본 값 확인

    @Test
    void saveCoupon() {
        Coupon coupon = new Coupon(
                "선착순 할인 쿠폰",
                1000,
                100,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusDays(1),
                CouponStatus.OPEN
        );

        Coupon saved = couponRepository.saveAndFlush(coupon);

        // enum 타입이 상수인지, 문자열인지 확인
        String savedStatus = jdbcTemplate.queryForObject(
                "select status from coupons where id = ?",
                String.class,
                saved.getId()
        );

        log.info("저장된 쿠폰: id={}, name={}, status={}, dbStatus={}, issuedQuantity={}, createdAt={}, updatedAt={}",
                saved.getId(),
                saved.getName(),
                saved.getStatus(),
                savedStatus,
                saved.getIssuedQuantity(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(CouponStatus.OPEN);
        assertThat(savedStatus).isEqualTo("OPEN");
        assertThat(saved.getIssuedQuantity()).isEqualTo(0);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

}
