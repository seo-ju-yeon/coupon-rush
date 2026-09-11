package dev.portfolio.couponrush.lock;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Log4j2
class SynchronizedConcurrencyTest {

    @Test
    void updateWithSynchronizedKeepsEveryResult() throws InterruptedException {

        int requestCount = 2;

        // 모든 스레드가 같은 객체를 공유함
        CouponStack couponStack = new CouponStack();

        ExecutorService executorService =
                Executors.newFixedThreadPool(requestCount);

        // 두 스레드가 실행 준비를 마칠 때까지 기다림
        CountDownLatch readyLatch =
                new CountDownLatch(requestCount);

        // 준비된 두 스레드를 동시에 출발시킴
        CountDownLatch startLatch =
                new CountDownLatch(1);

        // 두 스레드의 작업이 끝날 때까지 기다림
        CountDownLatch doneLatch =
                new CountDownLatch(requestCount);

        try {
            for (int i = 0; i < requestCount; i++) {
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();

                        // 메인 테스트가 출발 신호를 보낼 때까지 기다림
                        startLatch.await();

                        couponStack.increase();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean allReady =
                    readyLatch.await(3, TimeUnit.SECONDS);

            assertThat(allReady).isTrue();

            // 두 스레드에 동시에 출발 신호를 보냄
            startLatch.countDown();

            boolean allDone =
                    doneLatch.await(3, TimeUnit.SECONDS);

            assertThat(allDone).isTrue();
        } finally {
            // 테스트 실패 시에도 대기 중인 스레드가 종료되도록 함
            startLatch.countDown();
            executorService.shutdownNow();
        }

        log.info(
                "synchronized 적용 결과: expected={}, actual={}",
                requestCount,
                couponStack.getIssuedQuantity()
        );

        // 두 요청의 수정 결과가 모두 반영되었는지 검증함
        assertThat(couponStack.getIssuedQuantity()).isEqualTo(requestCount);
    }

    // synchronized의 상호 배제 동작을 확인하기 위한 테스트 객체임
    private static class CouponStack {

        private int issuedQuantity;

        /*
        같은 CouponStack 객체에서는 한 번에 한 스레드만 실행할 수 있음
        읽기와 수정 전체를 하나의 임계 영역으로 보호함
         */
        synchronized void increase() {  // 임계영역 메서드
            int currentQuantity = issuedQuantity;
            issuedQuantity = currentQuantity + 1;
        }

        synchronized int getIssuedQuantity() {
            return issuedQuantity;
        }
    }
}
