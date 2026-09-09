package dev.portfolio.couponrush.domain.coupon.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.coupon.entity.Coupon;
import dev.portfolio.couponrush.domain.coupon.entity.CouponStatus;
import dev.portfolio.couponrush.domain.coupon.repository.CouponIssueRepository;
import dev.portfolio.couponrush.domain.coupon.repository.CouponRepository;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Log4j2
@SpringBootTest
@Testcontainers
@TestPropertySource(properties =
        "security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class CouponRedisLockConcurrencyTest {

    // 인증이 테스트 목적이 아니므로 고정된 임시 해시값을 사용함
    private static final String TEST_PASSWORD_HASH = "encoded-test-password";

    // 실제 PostgreSQL에서 JPA 트랜잭션과 DB 제약조건까지 함께 검증함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    // 실제 Redis에서 여러 스레드가 같은 분산 락을 공유하는지 검증함
    @Container
    static GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7.4.11-alpine"))
                    .withExposedPorts(6379);

    /*
    Testcontainers가 매번 임의로 배정한 Redis 주소와 포트를
    애플리케이션의 Redis 설정값으로 전달함
     */
    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> redisContainer.getMappedPort(6379)
        );
    }

    @Autowired
    CouponIssueRedisLockFacade couponIssueRedisLockFacade;

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    CouponIssueRepository couponIssueRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 외래 키 제약조건을 고려하여 발급 내역부터 삭제함
        couponIssueRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void issueCouponConcurrentlyWithRedisLock() throws InterruptedException {
        int totalQuantity = 5;
        int requestCount = 20;

        // 발급 가능한 수량이 5개인 쿠폰을 준비함
        Coupon coupon = couponRepository.saveAndFlush(
                new Coupon(
                        "Redis 락 테스트 쿠폰",
                        1000,
                        totalQuantity,
                        LocalDateTime.now().minusMinutes(10),
                        LocalDateTime.now().plusDays(1),
                        CouponStatus.OPEN
                )
        );

        // 사용자 중복 발급 검증에 걸리지 않도록 요청마다 다른 사용자를 준비함
        List<User> users = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            User user = userRepository.saveAndFlush(
                    new User(
                            "redis-lock-" + i + "@example.com",
                            "user" + i,
                            TEST_PASSWORD_HASH
                    )
            );

            users.add(user);
        }

        // 20개의 요청을 서로 다른 스레드에서 실행하기 위한 스레드 풀을 생성함
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);

        // 모든 요청 스레드가 출발선에 도착했는지 확인함
        CountDownLatch readyLatch = new CountDownLatch(requestCount);

        // 준비된 요청을 최대한 같은 시점에 출발시키는 신호로 사용함
        CountDownLatch startLatch = new CountDownLatch(1);

        // 모든 요청이 끝날 때까지 테스트 스레드가 기다리게 함
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        // 여러 스레드가 동시에 값을 변경하므로 AtomicInteger로 결과를 안전하게 집계함
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger soldOutCount = new AtomicInteger();
        AtomicInteger lockTimeoutCount = new AtomicInteger();
        AtomicInteger optimisticLockFailureCount = new AtomicInteger();
        AtomicInteger unexpectedFailureCount = new AtomicInteger();

        // 예상하지 못한 예외의 종류를 확인할 수 있도록 스레드 안전한 목록에 기록함
        List<String> unexpectedFailureTypes =
                Collections.synchronizedList(new ArrayList<>());

        try {
            for (User user : users) {
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        /*
                        Service를 직접 호출하지 않고 Facade를 호출함
                        같은 couponId의 요청은 Redis 락 안에서 하나씩 처리됨
                         */
                        couponIssueRedisLockFacade.issueCoupon(
                                coupon.getId(),
                                user.getId()
                        );

                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.COUPON_SOLD_OUT) {
                            soldOutCount.incrementAndGet();
                        } else if (e.getErrorCode() == ErrorCode.COUPON_ISSUE_LOCK_TIMEOUT) {
                            lockTimeoutCount.incrementAndGet();
                        } else {
                            unexpectedFailureCount.incrementAndGet();
                            unexpectedFailureTypes.add(e.getErrorCode().name());
                        }
                    } catch (OptimisticLockingFailureException e) {
                        /*
                        Redis 락이 요청을 순서대로 처리하므로
                        Coupon의 @Version 충돌은 발생하지 않아야 함
                         */
                        optimisticLockFailureCount.incrementAndGet();
                    } catch (Exception e) {
                        unexpectedFailureCount.incrementAndGet();
                        unexpectedFailureTypes.add(e.getClass().getSimpleName());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // 일부 스레드가 준비되지 않은 상태로 테스트가 진행되는 것을 방지함
            boolean allReady = readyLatch.await(5, TimeUnit.SECONDS);
            assertThat(allReady).isTrue();

            startLatch.countDown();

            // Redis 락으로 요청이 순차 처리되므로 완료 대기시간을 넉넉하게 설정함
            boolean allDone = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(allDone).isTrue();
        } finally {
            // 테스트 도중 예외가 발생해도 대기 중인 스레드와 스레드 풀을 정리함
            startLatch.countDown();
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        }

        // 영속성 컨텍스트의 기존 값이 아닌 실제 DB의 최종 상태를 다시 조회함
        Coupon updatedCoupon = couponRepository
                .findById(coupon.getId())
                .orElseThrow();

        long issuedCouponCount = couponIssueRepository.count();

        int handledRequestCount =
                successCount.get()
                        + soldOutCount.get()
                        + lockTimeoutCount.get()
                        + optimisticLockFailureCount.get()
                        + unexpectedFailureCount.get();

        log.info(
                "Redis 락 동시 발급 결과: requestCount={}, successCount={}, " +
                        "soldOutCount={}, lockTimeoutCount={}, " +
                        "optimisticLockFailureCount={}, unexpectedFailureCount={}, " +
                        "issuedQuantity={}, issueCount={}, unexpectedFailureTypes={}",
                requestCount,
                successCount.get(),
                soldOutCount.get(),
                lockTimeoutCount.get(),
                optimisticLockFailureCount.get(),
                unexpectedFailureCount.get(),
                updatedCoupon.getIssuedQuantity(),
                issuedCouponCount,
                unexpectedFailureTypes.stream().distinct().toList()
        );

        // 제출한 모든 요청이 성공 또는 실패 결과로 집계되었는지 검증함
        assertThat(handledRequestCount).isEqualTo(requestCount);

        // Redis 락 안에서 수량을 확인하므로 정확히 재고 수량만큼 성공해야 함
        assertThat(successCount.get()).isEqualTo(totalQuantity);
        assertThat(soldOutCount.get()).isEqualTo(requestCount - totalQuantity);

        // 모든 요청이 제한 시간 안에 락을 획득하여 처리되었는지 검증함
        assertThat(lockTimeoutCount.get()).isZero();

        // 요청이 직렬화되었으므로 낙관적 락 충돌이 발생하지 않아야 함
        assertThat(optimisticLockFailureCount.get()).isZero();

        // 분류하지 못한 예외가 발생하지 않았는지 검증함
        assertThat(unexpectedFailureCount.get()).isZero();
        assertThat(unexpectedFailureTypes).isEmpty();

        // 쿠폰의 발급 수량과 실제 발급 내역이 모두 재고 수량과 일치해야 함
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(totalQuantity);
        assertThat(issuedCouponCount).isEqualTo(totalQuantity);
    }
}
