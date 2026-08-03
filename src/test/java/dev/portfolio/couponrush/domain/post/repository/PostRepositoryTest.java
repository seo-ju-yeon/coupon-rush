package dev.portfolio.couponrush.domain.post.repository;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import dev.portfolio.couponrush.domain.post.entity.Post;
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
// 실제 PostgreSQL에서 게시글과 쿠폰 연관관계 저장을 검증함
class PostRepositoryTest {

    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    // 게시글을 저장할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    PostRepository postRepository;

    // 게시글에 연결할 쿠폰을 저장할 Repository를 주입받음
    @Autowired
    CouponRepository couponRepository;

    // JPA가 저장한 실제 DB 원본 값을 확인할 때 사용함
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void savePostWithoutCoupon() {
        // 쿠폰과 연결되지 않은 게시글을 생성함
        Post post = new Post("쿠폰 없는 이벤트 게시글", "쿠폰 없이 노출되는 게시글입니다.");

        // 게시글을 저장하고 DB에 즉시 반영함
        Post saved = postRepository.saveAndFlush(post);

        // 게시글의 coupon_id가 null로 저장되었는지 확인함
        Long savedCouponId = jdbcTemplate.queryForObject(
                "select coupon_id from posts where id = ?",
                Long.class,
                saved.getId()
        );

        log.info("저장된 게시글: id={}, title={}, couponId={}, createdAt={}, updatedAt={}, deletedAt={}",
                saved.getId(),
                saved.getTitle(),
                savedCouponId,
                saved.getCreatedAt(),
                saved.getUpdatedAt(),
                saved.getDeletedAt()
        );

        // 게시글과 생성·수정·삭제 일시의 저장 결과를 검증함
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCoupon()).isNull();
        assertThat(savedCouponId).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void savePostWithCoupon() {
        // 게시글에 연결할 쿠폰을 먼저 저장함
        Coupon coupon = couponRepository.saveAndFlush(createCoupon());
        Post post = new Post("쿠폰 연결 이벤트 게시글", "쿠폰이 연결된 게시글입니다.", coupon);

        // 쿠폰이 연결된 게시글을 저장함
        Post saved = postRepository.saveAndFlush(post);

        // posts 테이블의 coupon_id가 연결된 쿠폰 ID인지 확인함
        Long savedCouponId = jdbcTemplate.queryForObject(
                "select coupon_id from posts where id = ?",
                Long.class,
                saved.getId()
        );

        log.info("저장된 게시글: id={}, title={}, couponId={}, createdAt={}, updatedAt={}, deletedAt={}",
                saved.getId(),
                saved.getTitle(),
                savedCouponId,
                saved.getCreatedAt(),
                saved.getUpdatedAt(),
                saved.getDeletedAt()
        );

        // 게시글과 쿠폰의 연관관계 저장 결과를 검증함
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCoupon().getId()).isEqualTo(coupon.getId());
        assertThat(savedCouponId).isEqualTo(coupon.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    private Coupon createCoupon() {
        return new Coupon(
                "게시글 연결 쿠폰",
                1000,
                100,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusDays(1),
                CouponStatus.OPEN
        );
    }
}
