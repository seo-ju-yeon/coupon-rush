package dev.portfolio.couponrush.lock;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import jakarta.persistence.*;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Log4j2
@SpringBootTest
@Testcontainers
class OptimisticLockConcurrencyTest {
    /*
    낙관적 락 테스트 흐름
        A 조회: version=0
        B 조회: version=0
        A 커밋: version=1
        B 커밋: WHERE version=0 불일치
        B 롤백
     */

    // 실제 PostgreSQL의 UPDATE와 version 비교를 확인함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void staleVersionUpdateFails() {
        Coupon savedCoupon = couponRepository.saveAndFlush(
                new Coupon(
                        "낙관적 락 테스트 쿠폰",
                        1000,
                        10,
                        LocalDateTime.now().minusMinutes(1),
                        LocalDateTime.now().plusDays(1),
                        CouponStatus.OPEN
                )
        );

        /* 두 요청이 서로 다른 영속성 컨텍스트를 사용하도록 EntityManager를 각각 생성함 */
        // EntityManager: 엔티티를 조회하고 관리하는 독립적인 영속성 컨텍스트
        EntityManager requestA =
                entityManagerFactory.createEntityManager();

        EntityManager requestB =
                entityManagerFactory.createEntityManager();

        // EntityTransaction: 트랜잭션의 시작, 커밋과 롤백을 직접 제어
        EntityTransaction transactionA = requestA.getTransaction();
        EntityTransaction transactionB = requestB.getTransaction();

        try {
            transactionA.begin();
            transactionB.begin();

            // 두 요청이 같은 쿠폰과 version을 조회함
            Coupon couponA =
                    requestA.find(Coupon.class, savedCoupon.getId());

            Coupon couponB =
                    requestB.find(Coupon.class, savedCoupon.getId());

            assertThat(couponA).isNotSameAs(couponB);
            assertThat(couponA.getVersion()).isEqualTo(couponB.getVersion());

            log.info(
                    "--- 조회 시점: versionA:{}, versionB:{} ----",
                    couponA.getVersion(),
                    couponB.getVersion()
            );

            // 관리 상태 엔티티를 변경하며 commit 시 Dirty Checking으로 UPDATE가 실행됨
            couponA.increaseIssueQuantity();
            couponB.increaseIssueQuantity();

            // A가 먼저 커밋하여 DB의 version을 1로 변경함
            transactionA.commit();

            log.info(
                    "--- A 커밋 완료: issuedQuantity={}, version={} ---",
                    couponA.getIssuedQuantity(),
                    couponA.getVersion()
            );

            /*
            B는 조회했던 version=0으로 UPDATE를 시도함
            DB는 이미 version=1이므로 수정된 행이 없어 예외가 발생함
             */
            // assertThatThrownBy: 실행한 코드에서 예외가 발생하는지 검증
            assertThatThrownBy(() -> transactionB.commit())
                    // RollbackException: 커밋 실패를 나타내는 바깥쪽 예외
                    .isInstanceOf(RollbackException.class)
                    // OptimisticLockException: version 충돌이라는 실제 원인을 나타내는 예외
                    .hasCauseInstanceOf(OptimisticLockException.class);
        } finally {
            // 테스트 실패 시에도 남아 있는 트랜잭션과 EntityManager를 정리함
            if (transactionA.isActive()) {
                transactionA.rollback();
            }

            if (transactionB.isActive()) {
                transactionB.rollback();
            }

            requestA.close();
            requestB.close();
        }

        // A 커밋과 B 롤백 이후 실제 DB에 남은 최종 상태를 다시 조회함
        Coupon result = couponRepository
                .findById(savedCoupon.getId())
                .orElseThrow();

        log.info(
                "낙관적 락 결과: issuedQuantity={}, version={}",
                result.getIssuedQuantity(),
                result.getVersion()
        );

        // A의 수정만 반영되고 B의 수정은 롤백되었는지 확인함
        assertThat(result.getIssuedQuantity()).isEqualTo(1);
        assertThat(result.getVersion()).isEqualTo(1L);
    }
}
