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
class PostRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    PostRepository postRepository;

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void savePostWithoutCoupon() {
        Post post = new Post("쿠폰 없는 이벤트 게시글", "쿠폰 없이 노출되는 게시글입니다.");

        Post saved = postRepository.saveAndFlush(post);

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

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCoupon()).isNull();
        assertThat(savedCouponId).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void savePostWithCoupon() {
        Coupon coupon = couponRepository.saveAndFlush(createCoupon());
        Post post = new Post("쿠폰 연결 이벤트 게시글", "쿠폰이 연결된 게시글입니다.", coupon);

        Post saved = postRepository.saveAndFlush(post);

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
