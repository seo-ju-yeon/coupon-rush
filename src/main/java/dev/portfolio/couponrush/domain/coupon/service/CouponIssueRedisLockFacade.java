package dev.portfolio.couponrush.domain.coupon.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.coupon.dto.CouponIssueResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Log4j2
@Component
@RequiredArgsConstructor
public class CouponIssueRedisLockFacade {

    // 쿠폰별로 서로 다른 Redis 락을 만들기 위한 접두사
    private static final String LOCK_KEY_PREFIX = "coupon:issue:";

    // 락 획득을 기다릴 최대 시간
    private static final long LOCK_WAIT_TIME_SECONDS = 10;

    private final RedissonClient redissonClient;
    private final CouponIssueService couponIssueService;

    public CouponIssueResponse issueCoupon(
            Long couponId,
            Long userId
    ) {
        // 같은 쿠폰 ID를 사용하는 요청끼리 동일한 락을 공유함
        String lockKey = LOCK_KEY_PREFIX + couponId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean lockAcquired = false;

        try {
            log.info(
                    "Redis 락 획득 시도: lockKey={}, userId={}",
                    lockKey, userId
            );

            /*
            최대 10초 동안 락 획득을 기다림
            leaseTime을 지정하지 않아 Redisson Watchdog이 락 만료 시간을 갱신함
             */
            lockAcquired = lock.tryLock(
                    LOCK_WAIT_TIME_SECONDS,
                    TimeUnit.SECONDS
            );

            if (!lockAcquired) {
                throw new BusinessException(
                        ErrorCode.COUPON_ISSUE_LOCK_TIMEOUT
                );
            }

            log.info(
                    "Redis 락 획득 성공: lockKey={}, userId={}",
                    lockKey, userId
            );

            /*
            별도의 Spring Bean인 CouponIssueService를 호출하므로
            Redis 락을 획득한 뒤 Service의 트랜잭션이 시작됨
             */
            return couponIssueService.issueCoupon(
                    couponId,
                    userId
            );
        } catch (InterruptedException e) {
            // 인터럽트 상태를 복구하여 상위 실행 환경이 중단 사실을 확인할 수 있게 함
            Thread.currentThread().interrupt();

            throw new BusinessException(
                    ErrorCode.COUPON_ISSUE_LOCK_TIMEOUT
            );
        } finally {
            /*
            락 획득에 성공했고 현재 스레드가 소유한 경우에만 해제함
            다른 요청이 보유한 락을 잘못 해제하는 것을 방지함
             */
            // lockAcquired: 실제로 락을 얻었는지
            // isHeldByCurrentThread: 현재 요청이 해당 락 소유자인지 확인
            if (lockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();  // 락 해제

                log.info(
                        "Redis 락 해제 완료: lockKey={}, userId={}",
                        lockKey, userId
                );
            }
        }
    }
}
