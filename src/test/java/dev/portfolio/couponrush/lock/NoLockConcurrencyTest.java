package dev.portfolio.couponrush.lock;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Log4j2
class NoLockConcurrencyTest {
    /*
    [테스트]
        Lock이 없을 때 Lost Update 재현

    [발생시킬 상황]
        초기 발급 수량: 0

        요청 A가 0을 읽음
        요청 B도 0을 읽음

        요청 A가 0 + 1을 저장함
        요청 B도 0 + 1을 저장함

        두 번 발급했지만 최종 수량은 1
     */

    @Test
    void updateWithoutLockLosesOneResult() throws InterruptedException {

        int requestsCount = 2;

        // DB를 사용하기 전에 동시 수정 문제만 단순하게 재현하기 위한 객체
        CouponStack couponStack = new CouponStack();

        // 두 요청을 서로 다른 스레드에서 실행함
        // ExecutorService: API 요청을 처리하는 여러 서버 스레드를 흉내 냄
        ExecutorService executorService =
                Executors.newFixedThreadPool(requestsCount);

        // 두 스레드가 기존 수량을 모두 읽을 때까지 기다림
        // readDoneLatch: 두 요청이 모두 기존 값 0을 읽었는지 확인함
        CountDownLatch readDoneLatch =
                new CountDownLatch(requestsCount);

        // 두 스레드가 값을 모두 읽은 뒤 동시에 저장하도록 제어함
        // writeStartLatch: 두 요청이 읽기를 끝낸 후 저장을 시작시킴
        CountDownLatch writeStartLatch =
                new CountDownLatch(1);

        // 두 스레드의 작업이 모두 끝날 때까지 기다림
        // doneLatch: 모든 작업이 끝나기 전에 테스트가 종료되는 것을 방지함
        CountDownLatch doneLatch =
                new CountDownLatch(requestsCount);

        try {
            for (int i = 0; i < requestsCount; i++) {
                executorService.submit(() -> {
                    try {
                        // 두 스레드가 모두 0을 읽도록 혅재 값을 지역 변수에 저장함
                        // currentQuantity: 각 스레드가 읽은 값을 따로 보관함
                        int currentQuantity = couponStack.getIssuedQuantity();

                        readDoneLatch.countDown();  // 다른 스레드에 신호를 보냄

                        // 메인 테스트가 저장 시작 신호를 보낼 때까지 대기함
                        writeStartLatch.await();  // 신호를 기다림

                        // 두 스레드가 자신이 읽었던 0을 기준으로 1을 저장함
                        couponStack.setIssuedQuantity(
                                currentQuantity + 1
                        );
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // 두 스레드가 값을 모두 읽었는지 확인함
            boolean allRead =
                    readDoneLatch.await(3, TimeUnit.SECONDS);

            assertThat(allRead).isTrue();

            // 두 스레드가 저장을 시작하도록 신호를 보냄
            writeStartLatch.countDown();

            // 두 스레드의 저장 작업이 모두 끝났는지 확인함
            boolean allDone =
                    doneLatch.await(3, TimeUnit.SECONDS);

            assertThat(allDone).isTrue();
        } finally {
            // 테스트 실패 시에도 대기 중인 스레드가 종료될 수 있도록 함
            writeStartLatch.countDown();
            executorService.shutdownNow();  // 테스트 종료 후 생성한 스레드를 정리
        }

        /*
        Lost Update
         -> 두 요청이 실행되었으므로 기대 값은 2 이지만,
            요청 A의 수정 결과를 요청 B가 덮어써 실제 값은 1
         */
        log.info(
                "--- 락 없는 동시 수정 결과: expected={}, actual={} ---",
                requestsCount,
                couponStack.getIssuedQuantity()
        );

        // 두 번 증가시켰지만 Lost Update로 최종 결과는 1이 됨
        assertThat(couponStack.getIssuedQuantity())
                .isEqualTo(1);
        assertThat(couponStack.getIssuedQuantity())
                .isNotEqualTo(requestsCount);

    }

    // DB를 사용하기 전에 동시 수정 문제만 단순하게 재현하기 위한 객체
    private static class CouponStack {

        private int issuedQuantity;

        int getIssuedQuantity() {
            return issuedQuantity;
        }

        void setIssuedQuantity(int issuedQuantity) {
            this.issuedQuantity = issuedQuantity;
        }
    }
}
