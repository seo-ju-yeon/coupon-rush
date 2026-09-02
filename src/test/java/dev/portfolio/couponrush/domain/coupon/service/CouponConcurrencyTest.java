package dev.portfolio.couponrush.domain.coupon.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueCreateRequest;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers
@SpringBootTest
class CouponConcurrencyTest {
    // 여러 사용자의 동시 쿠폰 발급 요청이 현재 로직에서 어떻게 처리되는지 확인함

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CouponIssueService couponIssueService;  // 동시 발급 요청 실행할 서비스

    @Autowired
    CouponRepository couponRepository;  // 쿠폰을 저장하고 조회

    @Autowired
    CouponIssueRepository couponIssueRepository;  // 실제 발급된 쿠폰 내역 개수 확인

    @Autowired
    UserRepository userRepository;  // 동시 요청에 사용할 사용자 저장

    @BeforeEach
    void setUp() {
        // 외래 키 제약조건을 고려하여 발급 내역부터 삭제함
        couponIssueRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void issueCouponConcurrentlyWithOptimisticLock() throws InterruptedException {
        int totalQuantity = 5;
        int requestCount = 20;

        // 발급 수량이 5개인 쿠폰을 생성함
        Coupon coupon = couponRepository.saveAndFlush(
                new Coupon(
                        "동시성 테스트 쿠폰",
                        1000,
                        totalQuantity,
                        LocalDateTime.now().minusMinutes(10),
                        LocalDateTime.now().plusDays(1),
                        CouponStatus.OPEN
                ));

        // 중복 발급 제약조건 영향을 피하기 위해 서로 다른 사용자를 생성
        List<User> users = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            User user = userRepository.saveAndFlush(
                    new User(
                            "concurrency-" + i + "@example.com",
                            "user" + i
                    )
            );

            users.add(user);
        }

        // 동시 요청을 처리할 스레드 풀을 생성함
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);

        // 모든 스레드가 준비될 때까지 대기하기 위한 카운터임
        CountDownLatch readyLatch = new CountDownLatch(requestCount);

        // 모든 스레드를 동시에 시작시키기 위한 신호임
        CountDownLatch startLatch = new CountDownLatch(1);

        // 모든 스레드의 작업 완료를 기다리기 위한 카운터임
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        // 성공한 요청 수를 여러 스레드에서 안전하게 집계함
        AtomicInteger successCount = new AtomicInteger();

        // 품절과 같은 비즈니스 규칙으로 실패한 요청 수를 집계함
        AtomicInteger businessFailureCount = new AtomicInteger();

        // @Version 값의 충돌로 실패한 요청 수를 집계함
        AtomicInteger optimisticLockFailureCount = new AtomicInteger();

        // 예상하지 못한 예외로 실패한 요청 수를 집계함
        AtomicInteger unexpectedFailureCount = new AtomicInteger();

        // 품절과 같은 비즈니스 예외의 에러 코드를 여러 스레드에서 안전하게 기록함
        List<ErrorCode> businessFailureCodes = Collections.synchronizedList(new ArrayList<>());

        // 예상하지 못한 기술 예외의 클래스명을 여러 스레드에서 안전하게 기록함
        List<String> unexpectedFailureTypes = Collections.synchronizedList(new ArrayList<>());

        try {
            for (User user : users) {
                executorService.submit(() -> {
                    try {
                        // 각 스레드가 시작 준비를 마쳤음을 알림
                        readyLatch.countDown();

                        // 시작 신호가 올 때까지 대기함
                        startLatch.await();

                        CouponIssueCreateRequest request =
                                createRequest(user.getId());

                        couponIssueService.issueCoupon(
                                coupon.getId(),
                                request);

                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        businessFailureCount.incrementAndGet();
                        businessFailureCodes.add(e.getErrorCode());

                        log.info(
                                "쿠폰 발급 비즈니스 예외: userId={}, errorCode={}",
                                user.getId(),
                                e.getErrorCode()
                        );
                    } catch (OptimisticLockingFailureException e) {
                        optimisticLockFailureCount.incrementAndGet();

                        log.info(
                                "낙관적 락 충돌 발생: userId={}, exception={}",
                                user.getId(),
                                e.getClass().getSimpleName()
                        );
                    } catch (Exception e) {
                        unexpectedFailureCount.incrementAndGet();
                        unexpectedFailureTypes.add(e.getClass().getSimpleName());

                        log.info(
                                "예상하지 못한 쿠폰 발급 실패: userId={}, exception={}",
                                user.getId(),
                                e.getClass().getSimpleName()
                        );
                    } finally {
                        // 현재 스레드의 작업이 끝났음을 알림
                        doneLatch.countDown();
                    }
                });
            }

            // 모든 스레드가 준비될 때까지 최대 5초 대기함
            boolean allReady = readyLatch.await(5, TimeUnit.SECONDS);
            assertThat(allReady).isTrue();

            // 모든 스레드에 동시에 시작 신호를 전달함
            startLatch.countDown();

            // 모든 발급 요청이 끝날 때까지 최대 10초 대기함
            boolean allDone = doneLatch.await(10, TimeUnit.SECONDS);
            assertThat(allDone).isTrue();
        } finally {
            // 준비 대기 중 예외가 발생해도 작업 스레드가 계속 대기하지 않도록 시작 신호를 전달함
            startLatch.countDown();
            executorService.shutdown();
            executorService.awaitTermination(
                    10,
                    TimeUnit.SECONDS
            );
        }

        // 모든 요청이 끝난 뒤 DB의 쿠폰 상태를 다시 조회함
        Coupon updatedCoupon = couponRepository
                .findById(coupon.getId())
                .orElseThrow();

        long issuedCouponCount = couponIssueRepository.count();

        int totalFailureCount =
                businessFailureCount.get()
                        + optimisticLockFailureCount.get()
                        + unexpectedFailureCount.get();

        log.info(
                "낙관적 락 동시 발급 결과: requestCount={}, successCount={}, " +
                        "businessFailureCount={}, optimisticLockFailureCount={}, " +
                        "unexpectedFailureCount={}, issuedQuantity={}, issueCount={}, " +
                        "businessFailureCodes={}, unexpectedFailureTypes={}",
                requestCount,
                successCount.get(),
                businessFailureCount.get(),
                optimisticLockFailureCount.get(),
                unexpectedFailureCount.get(),
                updatedCoupon.getIssuedQuantity(),
                issuedCouponCount,
                businessFailureCodes.stream().distinct().toList(),
                unexpectedFailureTypes.stream().distinct().toList()
        );

        /*
         * 낙관적 락은 요청을 순서대로 대기시키지 않음
         * 같은 version을 수정한 요청 중 하나만 성공하고 나머지는 충돌로 실패할 수 있음
         * 따라서 정확히 5건 성공하는지가 아니라 최종 데이터의 일관성을 검증함
         */

        // 모든 요청이 성공 또는 실패로 처리되었는지 검증함
        assertThat(successCount.get() + totalFailureCount)
                .isEqualTo(requestCount);

        // 적어도 하나는 성공하고 총 발급 가능 수량을 초과하지 않는지 검증함
        assertThat(successCount.get())
                .isBetween(1, totalQuantity);

        // 쿠폰 발급 수량이 총 발급 가능 수량을 초과하지 않는지 검증함
        assertThat(updatedCoupon.getIssuedQuantity())
                .isLessThanOrEqualTo(totalQuantity);

        // 쿠폰의 발급 수량과 실제 발급 내역 수가 같은지 검증함
        assertThat((long) updatedCoupon.getIssuedQuantity())
                .isEqualTo(issuedCouponCount);

        // 성공한 요청 수와 실제 저장된 발급 내역 수가 같은지 검증함
        assertThat(successCount.get())
                .isEqualTo((int) issuedCouponCount);

        // 비즈니스 예외가 발생했다면 모두 품절 예외인지 검증함
        assertThat(businessFailureCodes.stream()
                .allMatch(errorCode -> errorCode == ErrorCode.COUPON_SOLD_OUT))
                .isTrue();

        // 동시 요청에서 실제 낙관적 락 충돌이 발생했는지 검증함
        assertThat(optimisticLockFailureCount.get())
                .isGreaterThan(0);

        // 분류되지 않은 예상 밖의 예외가 없어야 함
        assertThat(unexpectedFailureCount.get())
                .isEqualTo(0);

        assertThat(unexpectedFailureTypes)
                .isEmpty();
    }

    private CouponIssueCreateRequest createRequest(Long userId) {
        // setter 없이 테스트 요청 DTO의 private 필드에 값을 주입함
        CouponIssueCreateRequest request = new CouponIssueCreateRequest();

        ReflectionTestUtils.setField(
                request,
                "userId",
                userId
        );

        return request;
    }

}
