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
// 실제 PostgreSQL에서 쿠폰 엔티티의 저장 결과를 검증함
class CouponRepositoryTest {

    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    // 쿠폰을 저장할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    CouponRepository couponRepository;

    // JPA가 저장한 실제 DB 원본 값을 확인할 때 사용함
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void saveCoupon() {
        // 저장에 필요한 쿠폰 엔티티를 생성함
        Coupon coupon = new Coupon(
                "선착순 할인 쿠폰",
                1000,
                100,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusDays(1),
                CouponStatus.OPEN
        );

        Coupon saved = couponRepository.saveAndFlush(coupon);

        // enum이 DB에 문자열로 저장되었는지 확인함
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

        // ID 생성, enum 변환, 기본값, 생성·수정 일시를 검증함
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(CouponStatus.OPEN);
        assertThat(savedStatus).isEqualTo("OPEN");
        assertThat(saved.getIssuedQuantity()).isEqualTo(0);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

}
