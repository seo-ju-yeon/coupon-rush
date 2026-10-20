# ADR-003: Redisson 분산 락으로 쿠폰 발급 동시성 제어

- 상태: `승인`

## 배경과 문제

같은 쿠폰을 동시에 발급하면 여러 요청이 같은 발급 수량을 읽을 수 있습니다. `@Version`은 오래된 값으로 덮어쓰는 요청을 거부하지만, 충돌한 요청을 자동으로 재시도하지는 않습니다. 데이터 정합성을 지키는 것과 남은 수량만큼 발급을 완료하는 것은 서로 다른 문제입니다.

현재 발급 API는 Redis 락을 먼저 획득한 뒤 발급 서비스를 호출합니다. 구현 이력에서는 비관적 락에서 낙관적 락을 거쳐 Redis 락을 적용했고, `lock-lab`에서는 각 방식의 차이를 별도 학습 테스트로 비교했습니다.

락 없는 Lost Update 예시는 Java 메모리 객체를 사용한 개념 실험입니다. 실제 쿠폰 서비스에서 발급 내역과 수량이 어긋나는 장애를 재현했다는 의미는 아닙니다.

## 선택 기준과 검토한 대안

쿠폰별로 잠금 범위를 나누고, 여러 애플리케이션 인스턴스가 공유할 수 있는 잠금 수단을 사용하며, 발급 트랜잭션이 끝날 때까지 보호하는 것을 기준으로 삼았습니다.

| 방식 | 확인한 동작과 판단 |
| --- | --- |
| 락 없음 | 인메모리 실험에서 두 변경 중 하나가 사라지는 Lost Update를 재현합니다. |
| `synchronized` | 한 JVM의 공유 객체는 보호하지만 서로 다른 JVM의 요청은 함께 제어하지 못합니다. |
| JPA 낙관적 락 | 버전 충돌을 감지합니다. 충돌한 요청도 다시 처리하려면 재시도 정책이 필요합니다. |
| DB 비관적 락 | 같은 PostgreSQL을 사용하는 여러 서버에서도 행 락을 공유할 수 있습니다. 별도 Redis가 필요 없는 유효한 대안입니다. |
| Redis 분산 락 | 쿠폰별 공유 락과 Watchdog을 학습하고, 락 획득 책임을 트랜잭션 바깥의 Facade로 분리할 수 있습니다. |

## 결정과 이유

`CouponIssueRedisLockFacade`에서 Redisson의 `RLock`을 획득한 뒤 기존 `CouponIssueService`를 호출합니다. 분산 락의 사용 방법과 트랜잭션 경계를 학습하기 위해 채택했습니다. 현재 규모에서 비관적 락보다 빠르거나 반드시 필요한 방식이라는 결론은 내리지 않았습니다. DB 연결 대기나 처리량 차이도 측정하지 않았습니다.

- `coupon:issue:{couponId}` 키를 사용해 같은 쿠폰의 발급 요청을 직렬화합니다.
- `tryLock(10, TimeUnit.SECONDS)`로 락 획득 대기 시간을 제한합니다. 이 10초는 락 보유 시간이 아닙니다.
- 임대 시간을 직접 지정하지 않고 Watchdog의 만료 시간 갱신을 사용합니다.
- 별도 Spring Bean의 트랜잭션 메서드를 호출하여 커밋이 끝난 다음 락을 해제합니다.
- `finally`에서 현재 스레드가 보유한 락만 해제하고, 인터럽트 발생 시 상태를 복원한 뒤 비즈니스 예외로 변환합니다.

`@Version`과 DB의 고유·CHECK 제약도 유지합니다. Redis 락이 모든 저장 경로의 규칙을 대신하는 것은 아니며, 각 제약의 역할은 [ADR-001](001-use-postgresql-and-flyway.md)에 정리했습니다.

## 적용 흐름

```text
CouponIssueController
    → CouponIssueRedisLockFacade: 쿠폰별 락 획득
        → CouponIssueService의 트랜잭션 시작
            → 쿠폰·사용자·중복·기간·수량 확인
            → 발급 수량 증가와 발급 내역 저장
        → 트랜잭션 커밋 또는 롤백
    → finally에서 보유한 락 해제
```

Facade에는 트랜잭션을 선언하지 않고, Service를 별도 Bean으로 호출합니다. 현재 Controller → Facade 호출 경로에서 서비스 메서드 본문뿐 아니라 트랜잭션 종료까지 락 안에 포함시키기 위한 구조입니다.

## 검증 결과

학습 테스트와 실제 발급 서비스 테스트의 조건을 구분합니다. 공통 실행 방법과 저장된 결과는 [테스트 실행 및 검증 기록](../testing.md)에 있습니다.

| 테스트 | 조건과 확인하는 값 |
| --- | --- |
| `NoLockConcurrencyTest` | 메모리 객체를 두 스레드가 변경한 뒤 최종 수량이 1인지 확인합니다. |
| `SynchronizedConcurrencyTest` | 같은 메모리 객체를 동기화하여 최종 수량이 2인지 확인합니다. |
| `OptimisticLockConcurrencyTest` | PostgreSQL의 같은 버전을 읽은 두 트랜잭션 중 뒤의 커밋이 실패하고 최종 수량·버전이 1인지 확인합니다. |
| `PessimisticLockConcurrencyTest` | PostgreSQL 행 락을 사용하는 두 요청이 성공하고 최종 수량·버전이 2인지 확인합니다. |
| `RedisLockConcurrencyTest` | PostgreSQL·Redis 환경에서 같은 락을 사용하는 두 요청이 성공하고 최종 수량·버전이 2인지 확인합니다. |
| `CouponRedisLockConcurrencyTest` | 서로 다른 사용자 20명의 스레드가 수량 5인 쿠폰의 Facade를 호출합니다. 성공 5건, 품절 15건, 발급 수량 5와 발급 내역 5건을 확인합니다. |

발급 테스트는 락 타임아웃, 낙관적 락 충돌과 예상 밖 예외가 각각 0건인지도 확인합니다. 타임아웃이 발생하지 않았다는 검증이며, 타임아웃 처리 경로를 의도적으로 실행한 테스트는 아닙니다. 낙관적 락의 충돌 처리와 비교한 내용은 [낙관적 락과 발급 성공 수의 차이](../troubleshooting/001-optimistic-lock-conflict.md)에 정리했습니다.

## 한계와 후속 과제

현재 테스트는 한 JVM에서 실행합니다. 발급 동시성 테스트는 Facade를 직접 호출하므로 HTTP·인증을 포함한 동시 요청이나 실제 다중 서버 동작을 증명하지 않습니다. 방식별 데이터 저장 환경과 시나리오도 달라 성능을 직접 비교하지 않았습니다.

Redis가 추가되어 발급 API가 Redis 연결과 가용성에 의존합니다. Watchdog을 사용하더라도 네트워크 단절이나 장애 전환까지 안전하다고 보장할 수 없습니다. 장애·락 타임아웃·인터럽트 경로와 여러 애플리케이션 인스턴스의 동작을 별도로 검증해야 합니다. 대기 시간 10초의 적절성과 처리량·지연 시간도 후속 측정 대상입니다.

## 관련 코드와 테스트

- [CouponIssueRedisLockFacade](../../src/main/java/dev/portfolio/couponrush/domain/coupon/service/CouponIssueRedisLockFacade.java), [CouponIssueService](../../src/main/java/dev/portfolio/couponrush/domain/coupon/service/CouponIssueService.java)
- [CouponIssueController](../../src/main/java/dev/portfolio/couponrush/domain/coupon/controller/CouponIssueController.java), [Coupon](../../src/main/java/dev/portfolio/couponrush/domain/coupon/entity/Coupon.java)
- [NoLockConcurrencyTest](../../src/test/java/dev/portfolio/couponrush/lock/NoLockConcurrencyTest.java), [SynchronizedConcurrencyTest](../../src/test/java/dev/portfolio/couponrush/lock/SynchronizedConcurrencyTest.java)
- [OptimisticLockConcurrencyTest](../../src/test/java/dev/portfolio/couponrush/lock/OptimisticLockConcurrencyTest.java), [PessimisticLockConcurrencyTest](../../src/test/java/dev/portfolio/couponrush/lock/PessimisticLockConcurrencyTest.java)
- [RedisLockConcurrencyTest](../../src/test/java/dev/portfolio/couponrush/lock/RedisLockConcurrencyTest.java), [CouponRedisLockConcurrencyTest](../../src/test/java/dev/portfolio/couponrush/domain/coupon/service/CouponRedisLockConcurrencyTest.java)
