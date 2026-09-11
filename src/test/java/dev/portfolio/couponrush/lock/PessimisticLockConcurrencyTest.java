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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Log4j2
@SpringBootTest
@Testcontainers
public class PessimisticLockConcurrencyTest {
    /*
    테스트 목적
        요청 A가 비관적 락을 보유하는 동안 요청 B가 대기하고,
        A의 커밋 이후 B가 최신 데이터를 조회하여 수정하는지 확인함

    비관적 락 테스트 흐름
        요청 A: 쿠폰 조회 및 DB 락 획득
        요청 B: 같은 쿠폰 조회 시도 후 대기
        요청 A: 수량 0 → 1 변경 후 커밋
        요청 A: 락 해제
        요청 B: 최신 수량 1 조회
        요청 B: 수량 1 → 2 변경 후 커밋
     */

    // 실제 PostgreSQL의 UPDATE와 version 비교를 확인함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CouponRepository couponRepository;

    /*
    테스트 코드에서 비관적 락 조회를 직접 실행하기 위해 사용함
    실제 EntityManager는 각 스레드의 트랜잭션에 맞게 연결됨
     */
    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;


    @Test
    void concurrentUpdateAreSerializedByPessimisticLock() throws InterruptedException {

        int requestCount = 2;

        Coupon coupon = new Coupon(
                "비관적 락 테스트 쿠폰",
                1000,
                10,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10),
                CouponStatus.OPEN
        );

        Coupon savedCoupon = couponRepository.saveAndFlush(coupon);

        // 두 요청을 서로 다른 스레드에서 실행함
        ExecutorService executorService =
                Executors.newFixedThreadPool(requestCount);

        // 두 스레드가 실행 준비를 마칠 때까지 기다림
        CountDownLatch readyLatch =
                new CountDownLatch(requestCount);

        // 두 스레드가 가능한 동시에 시작하도록 제어함
        CountDownLatch startLatch =
                new CountDownLatch(1);

        // 두 스레드의 작업이 모두 끝날 때까지 기다림
        CountDownLatch doneLatch =
                new CountDownLatch(requestCount);


        // 성공 및 실패한 요청 수를 여러 스레드에서 안전하게 기록함
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        try {
            for (int i = 0; i < requestCount; i++) {
                int requestNumber = i + 1;

                executorService.submit(() -> {
                    try {
                        // 현재 스레드가 실행 준비를 마쳤음을 알림
                        readyLatch.countDown();

                        // 메인 테스트가 시작 신호를 보낼 때까지 대기함
                        startLatch.await();

                        /*
                        @Transactional은 테스트를 실행하는 메인 스레드에만 적용됨
                        작업 스레드마다 별도의 트랜잭션이 필요하므로 TransactionTemplate 사용함
                         */
                        TransactionTemplate transactionTemplate =
                                new TransactionTemplate(transactionManager);

                        transactionTemplate.executeWithoutResult(status -> {

                            /*
                            쿠폰을 조회하면서 해당 DB 행에 쓰기 락을 설정함
                            다른 트랜잭션은 현재 트랜잭션이 종료될 때까지 대기함
                             */
                            Coupon lockedCoupon = entityManager.find(
                                    Coupon.class,
                                    savedCoupon.getId(),
                                    LockModeType.PESSIMISTIC_WRITE
                            );

                            if (lockedCoupon == null) {
                                throw new IllegalStateException(
                                        "쿠폰을 찾을 수 없습니다."
                                );
                            }

                            int beforeQuantity =
                                    lockedCoupon.getIssuedQuantity();

                            lockedCoupon.increaseIssueQuantity();

                            log.info(
                                    "--- 요청 처리: request={}, before={}, after={} ---",
                                    requestNumber,
                                    beforeQuantity,
                                    lockedCoupon.getIssuedQuantity()
                            );

                            /*
                            lockedCoupon은 JPA가 관리하는 엔티티임
                            트랜잭션이 커밋될 때 변경 감지를 통해 UPDATE SQL이 자동으로 실행됨
                             */
                        });

                        successCount.incrementAndGet();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failureCount.incrementAndGet();

                    } catch (Exception e) {
                        failureCount.incrementAndGet();

                        log.info(
                                "요청 실패: request={}, exception={}",
                                requestNumber,
                                e.getClass().getSimpleName()
                        );

                    } finally {
                        // 성공 여부와 관계없이 작업이 끝났음을 알림
                        doneLatch.countDown();
                    }
                });
            }

            boolean allReady =
                    readyLatch.await(3, TimeUnit.SECONDS);

            // 대기 중인 두 스레드에 시작 신호를 보냄
            startLatch.countDown();

            assertThat(allReady).isTrue();

            boolean allDone =
                    doneLatch.await(5, TimeUnit.SECONDS);

            assertThat(allDone).isTrue();
        } finally {
            // 테스트가 중간에 실패해도 대기 중인 스레드를 해제함
            startLatch.countDown();

            // 테스트 종료 후 스레드 풀을 정리함
            executorService.shutdownNow();
        }

        Coupon result = couponRepository
                .findById(savedCoupon.getId())
                .orElseThrow();

        log.info(
                "--- 최종 결과: successCount={}, failureCount={}, issuedQuantity={}, version={} ---",
                successCount.get(),
                failureCount.get(),
                result.getIssuedQuantity(),
                result.getVersion()
        );

        // 비관적 락을 통해 두 요청이 모두 순서대로 처리됐는지 검증함
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(failureCount.get()).isZero();
        assertThat(result.getIssuedQuantity()).isEqualTo(2);

        // 두 번의 UPDATE가 성공하여 @Version 값도 두 번 증가함
        assertThat(result.getVersion()).isEqualTo(2L);

    }

}
