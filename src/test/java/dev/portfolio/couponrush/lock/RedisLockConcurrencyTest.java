package dev.portfolio.couponrush.lock;

import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Log4j2
@SpringBootTest
@Testcontainers
class RedisLockConcurrencyTest {

    /*
    테스트 목적
        요청 A가 Redis 분산 락을 보유하는 동안 요청 B가 대기하고,
        A의 커밋 이후 B가 최신 데이터를 조회하여 수정하는지 확인함

    Redis 분산 락 테스트 흐름
        요청 A: Redis 락 획득 후 쿠폰 조회
        요청 B: 같은 Redis 락 획득 시도 후 대기
        요청 A: 수량 0 → 1 변경 후 DB 커밋
        요청 A: Redis 락 해제
        요청 B: Redis 락 획득 후 최신 수량 1 조회
        요청 B: 수량 1 → 2 변경 후 DB 커밋
        요청 B: Redis 락 해제
     */

    // 실제 PostgreSQL에서 JPA 트랜잭션과 UPDATE 결과를 확인함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    // 실제 Redis 컨테이너를 실행하여 Redisson 분산 락을 확인함
    @Container
    static GenericContainer<?> redisContainer =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.11-alpine")
            ).withExposedPorts(6379);

    /*
    Testcontainers는 Redis의 호스트 포트를 실행할 때마다 임의로 배정함
    배정된 주소와 포트를 Spring의 Redis 설정에 전달함
     */
    @DynamicPropertySource
    static void registerRedisProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.data.redis.host",
                redisContainer::getHost
        );

        registry.add(
                "spring.data.redis.port",
                () -> redisContainer.getMappedPort(6379)
        );
    }

    // Redis 락 객체인 RLock을 생성하고 관리함
    @Autowired
    RedissonClient redissonClient;

    // 테스트 쿠폰 저장과 최종 결과 조회에 사용함
    @Autowired
    CouponRepository couponRepository;

    // 작업 스레드마다 독립적인 DB 트랜잭션을 실행하기 위해 사용함
    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void concurrentUpdatesAreSerializedByRedisLock()
            throws InterruptedException {

        int requestCount = 2;

        // 두 요청이 함께 수정할 쿠폰을 준비함
        Coupon coupon = couponRepository.saveAndFlush(
                new Coupon(
                        "Redis 락 테스트 쿠폰",
                        1000,
                        10,
                        LocalDateTime.now().minusMinutes(1),
                        LocalDateTime.now().plusMinutes(10),
                        CouponStatus.OPEN
                )
        );

        // 두 요청을 서로 다른 스레드에서 실행함
        ExecutorService executorService =
                Executors.newFixedThreadPool(requestCount);

        // 두 스레드가 모두 출발 준비를 마칠 때까지 기다림
        CountDownLatch readyLatch =
                new CountDownLatch(requestCount);

        // 준비된 두 스레드를 가능한 한 같은 시점에 출발시킴
        CountDownLatch startLatch =
                new CountDownLatch(1);

        // 두 스레드의 작업이 모두 끝날 때까지 테스트 스레드가 기다림
        CountDownLatch doneLatch =
                new CountDownLatch(requestCount);

        // 여러 스레드가 동시에 결과를 기록하므로 원자적 정수 타입을 사용함
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        /*
        같은 couponId 요청은 반드시 같은 문자열을 락 키로 사용해야 함
        couponId가 다르면 서로 다른 락을 사용하므로 동시에 처리할 수 있음
         */
        String lockKey = "lock-lab:coupon:" + coupon.getId();

        try {
            for (int i = 0; i < requestCount; i++) {
                int requestNumber = i + 1;

                executorService.submit(() -> {
                    /*
                    getLock()은 지정한 키에 해당하는 락 객체를 가져옴
                    이 시점에는 아직 실제 락을 획득하지 않음
                     */
                    RLock lock = redissonClient.getLock(lockKey);
                    boolean lockAcquired = false;

                    try {
                        // 현재 스레드가 실행 준비를 마쳤음을 알림
                        readyLatch.countDown();

                        // 메인 테스트 스레드가 시작 신호를 보낼 때까지 대기함
                        startLatch.await();

                        log.info(
                                "Redis 락 획득 시도: request={}, lockKey={}",
                                requestNumber,
                                lockKey
                        );

                        /*
                        실제 Redis 락 획득을 최대 5초 동안 시도함
                        leaseTime을 생략했으므로 Redisson Watchdog이
                        작업 중 락이 만료되지 않도록 유효 시간을 자동 갱신함
                         */
                        lockAcquired = lock.tryLock(
                                5,
                                TimeUnit.SECONDS
                        );

                        // 제한 시간 동안 락을 얻지 못하면 DB 작업을 실행하지 않음
                        if (!lockAcquired) {
                            failureCount.incrementAndGet();

                            log.info(
                                    "Redis 락 획득 실패: request={}",
                                    requestNumber
                            );
                            return;
                        }

                        log.info(
                                "Redis 락 획득 성공: request={}",
                                requestNumber
                        );

                        /*
                        Redis 락을 먼저 획득한 후 DB 트랜잭션을 시작함
                        DB 커밋 전에 락을 해제하면 다음 요청이 변경 전 데이터를
                        조회할 수 있으므로 트랜잭션이 끝날 때까지 락을 유지함
                         */
                        TransactionTemplate transactionTemplate =
                                new TransactionTemplate(transactionManager);

                        transactionTemplate.executeWithoutResult(status -> {
                            Coupon foundCoupon = couponRepository
                                    .findById(coupon.getId())
                                    .orElseThrow();

                            int beforeQuantity =
                                    foundCoupon.getIssuedQuantity();

                            foundCoupon.increaseIssueQuantity();

                            log.info(
                                    "쿠폰 수량 변경: request={}, before={}, after={}",
                                    requestNumber,
                                    beforeQuantity,
                                    foundCoupon.getIssuedQuantity()
                            );

                            /*
                            foundCoupon은 현재 트랜잭션에서 관리되는 엔티티임
                            save()를 다시 호출하지 않아도 커밋 시 변경 감지를 통해
                            UPDATE SQL이 자동으로 실행됨
                             */
                        });

                        // executeWithoutResult가 정상 종료되면 DB 커밋까지 완료된 상태임
                        successCount.incrementAndGet();

                    } catch (InterruptedException e) {
                        // 대기 중 인터럽트가 발생하면 인터럽트 상태를 복구함
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
                        /*
                        락 획득에 성공했고 현재 스레드가 소유한 경우에만 해제함
                        다른 요청이 가진 락을 잘못 해제하는 것을 방지함
                         */
                        if (lockAcquired
                                && lock.isHeldByCurrentThread()) {
                            lock.unlock();

                            log.info(
                                    "Redis 락 해제 완료: request={}",
                                    requestNumber
                            );
                        }

                        // 성공 또는 실패 여부와 관계없이 작업 종료를 알림
                        doneLatch.countDown();
                    }
                });
            }

            // 일부 스레드가 준비되지 않은 상태로 시작하는 것을 방지함
            boolean allReady =
                    readyLatch.await(3, TimeUnit.SECONDS);

            // 대기 중인 두 스레드에 동시에 시작 신호를 보냄
            startLatch.countDown();

            assertThat(allReady).isTrue();

            // Redis 락 대기 시간을 고려하여 완료 시간을 넉넉하게 설정함
            boolean allDone =
                    doneLatch.await(10, TimeUnit.SECONDS);

            assertThat(allDone).isTrue();

        } finally {
            // 테스트가 중간에 실패해도 대기 중인 스레드를 해제함
            startLatch.countDown();

            // 테스트 종료 후 생성한 작업 스레드를 정리함
            executorService.shutdownNow();
        }

        // 두 트랜잭션이 끝난 후 실제 DB에 저장된 최종 값을 조회함
        Coupon result = couponRepository
                .findById(coupon.getId())
                .orElseThrow();

        log.info(
                "Redis 락 최종 결과: successCount={}, failureCount={}, "
                        + "issuedQuantity={}, version={}",
                successCount.get(),
                failureCount.get(),
                result.getIssuedQuantity(),
                result.getVersion()
        );

        // Redis 락을 사용한 두 요청이 모두 정상 처리됐는지 확인함
        assertThat(successCount.get()).isEqualTo(requestCount);
        assertThat(failureCount.get()).isZero();

        // 두 요청의 수량 증가가 모두 유실되지 않고 반영됐는지 확인함
        assertThat(result.getIssuedQuantity()).isEqualTo(requestCount);

        // 두 번의 UPDATE가 성공하여 @Version도 두 번 증가했는지 확인함
        assertThat(result.getVersion()).isEqualTo(2L);
    }
}
