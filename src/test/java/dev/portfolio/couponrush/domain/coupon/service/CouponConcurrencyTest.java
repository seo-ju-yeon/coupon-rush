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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
    void issueCouponConcurrently() throws InterruptedException {
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

        // 성공과 실패한 요청 수를 여러 스레드에서 안전하게 집계함
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        // 실패한 예외 유형을 여러 스레드에서 안전하게 기록함
        // failureTypes: 예상한 품절 실패인지 기록
        List<String> failureTypes = Collections.synchronizedList(new ArrayList<>());

        // 비즈니스 예외로 실패한 요청의 에러 코드를 안전하게 기록함
        // failureCodes: 품절 외에 예상하지 못한 기술 예외가 발생했는지 기록
        List<ErrorCode> failureCodes = Collections.synchronizedList(new ArrayList<>());

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
                        failureCount.incrementAndGet();
                        failureCodes.add(e.getErrorCode());

                        log.info(
                                "동시 발급 요청 실패: userId={}, errorCode={}",
                                user.getId(),
                                e.getErrorCode()
                        );
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        failureTypes.add(e.getClass().getSimpleName());

                        log.info(
                                "예상하지 못한 동시 발급 요청 실패: userId={}, exception={}",
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

        log.info(
                "비관적 락 동시 발급 결과: requestCount={}, successCount={}, " +
                        "failureCount={}, issuedQuantity={}, issueCount={}, " +
                        "failureCodes={}, failureTypes={}",
                requestCount,
                successCount.get(),
                failureCount.get(),
                updatedCoupon.getIssuedQuantity(),
                issuedCouponCount,
                failureCodes.stream().distinct().toList(),
                failureTypes.stream().distinct().toList()
        );

        /*
         * 비관적 락으로 같은 쿠폰의 발급 요청을 순차 처리함
         * 따라서 재고 수량만큼 정확히 성공하고, 나머지 요청은 품절로 실패해야 함
         */

        // 비관적 락으로 요청을 순차 처리하므로 재고 수량만큼 정확히 발급되어야 함
        assertThat(successCount.get())
                .isEqualTo(totalQuantity);

        // 재고를 초과한 요청은 모두 품절로 실패해야 함
        assertThat(failureCount.get())
                .isEqualTo(requestCount - totalQuantity);

        // 쿠폰 발급 수량과 실제 발급 내역 수가 총 수량과 같아야 함
        assertThat(updatedCoupon.getIssuedQuantity())
                .isEqualTo(totalQuantity);

        assertThat(issuedCouponCount)
                .isEqualTo((long) totalQuantity);

        // 실패한 비즈니스 예외는 모두 품절 예외여야 함
        assertThat(failureCodes.size())
                .isEqualTo(requestCount - totalQuantity);

        assertThat(failureCodes.stream()
                .allMatch(errorCode -> errorCode == ErrorCode.COUPON_SOLD_OUT))
                .isTrue();

        // 품절 외의 예상하지 못한 기술 예외가 없어야 함
        assertThat(failureTypes.size())
                .isEqualTo(0);
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
