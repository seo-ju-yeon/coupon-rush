# TS-001: 초과 발급은 없는데 낙관적 락 충돌로 요청이 실패하는 이유

- 상태: `분석 및 비교 검증 완료`

## 문제 상황

수량이 5개인 쿠폰에 서로 다른 사용자 20명이 동시에 발급을 요청하는 테스트를 비교했습니다. `CouponIssueService`를 직접 호출하는 낙관적 락 테스트와 `CouponIssueRedisLockFacade`를 호출하는 Redis 락 테스트는 모두 DB 발급 수량과 발급 내역이 5개였지만, 실패 사유가 달랐습니다.

저장된 보고서에서 확인한 한 실행의 결과는 다음과 같습니다.

| 호출 경로 | 성공 | 품절 | 낙관적 락 충돌 | DB 발급 수량 / 발급 내역 |
| --- | --- | --- | --- | --- |
| Service 직접 호출 | 5 | 11 | 4 | 5 / 5 |
| Redis 락 Facade 호출 | 5 | 15 | 0 | 5 / 5 |

이 실행에서는 두 방식 모두 재고 5개를 소진했고, 차이는 나머지 15건의 실패 사유에 있었습니다. Redis 락 경로의 락 획득 시간 초과와 두 테스트의 예상 밖 예외는 모두 0건이었습니다. 전체 실행 범위는 [테스트 검증 기록](../testing.md)에 정리했습니다.

## 원인 분석

`Coupon`에는 `@Version`이 있고, `CouponIssueService`는 별도의 행 락 없이 쿠폰을 조회합니다. 같은 버전을 읽은 요청들이 수량을 변경하면 먼저 반영된 요청이 버전을 갱신하고, 뒤의 요청은 이전 버전으로 변경하려다가 충돌합니다.

Service는 발급 수량 증가와 발급 내역 저장을 하나의 트랜잭션에서 처리합니다. 충돌한 요청의 변경은 롤백되므로 발급 내역과 수량의 일관성을 지킬 수 있습니다. 다만 현재 코드에는 낙관적 락 충돌을 자동으로 재시도하는 처리가 없습니다. 충돌한 요청은 품절 여부를 다시 확인하는 단계까지 진행하지 못하고 실패할 수 있습니다.

Redis 락 경로는 같은 쿠폰 ID의 요청이 공통 락을 획득한 뒤 Service를 호출합니다. 현재 Controller 호출 경로에서는 별도 Service Bean의 트랜잭션이 끝난 후 Facade가 락을 해제하므로 다음 요청은 앞선 변경이 반영된 상태에서 수량을 확인합니다.

## 대응과 검증

실패를 한 종류로 합산하면 충돌과 품절의 차이를 놓칠 수 있습니다. 기존 테스트는 성공, 품절, 낙관적 락 충돌, 예상 밖 예외를 나누어 집계하고 DB에 남은 결과도 함께 확인합니다.

- `CouponConcurrencyTest`는 성공 건수가 1~5 범위이며 낙관적 락 충돌이 1건 이상인지 확인합니다. 또한 `성공 건수 = DB 발급 수량 = 발급 내역 수`인지 검증합니다. 매 실행에서 반드시 5건이 성공하도록 요구하지 않습니다.
- `CouponRedisLockConcurrencyTest`는 성공 5건, 품절 15건, 낙관적 락 충돌 0건, 락 획득 시간 초과 0건을 확인합니다. DB 발급 수량과 발급 내역 수가 각각 5인지도 검증합니다.

두 테스트 모두 한 JVM의 여러 스레드에서 Service 또는 Facade를 직접 호출합니다. PostgreSQL은 Testcontainers로 실행하고 Redis 락 테스트는 Redis 컨테이너도 사용합니다. HTTP 요청, 실제 서버 여러 대, 처리량이나 응답 시간은 이 비교의 검증 범위에 포함하지 않습니다.

이 문서는 기존 코드와 저장된 보고서를 분석한 기록이며, 문서 작성 과정에서 테스트를 새로 실행하지 않았습니다.

## 배운 점

초과 발급이 없다는 사실만으로 모든 요청이 의도한 결과를 받았다고 판단할 수는 없습니다. DB 정합성을 확인하는 조건과 요청이 성공·품절·충돌 중 어떤 결과로 끝나는지 확인하는 조건을 함께 설계해야 한다는 점을 배웠습니다.

또한 낙관적 락의 충돌 감지, 재시도 정책, Redis 락의 대기 처리는 서로 다른 역할입니다. 특정 락을 사용했다는 사실보다 어떤 실패를 허용하고 무엇을 검증할지 설명하는 것이 중요했습니다.

## 남은 과제

- 동일한 조건에서 반복 실행하여 낙관적 락의 성공 건수와 실패 사유가 어떻게 달라지는지 기록합니다.
- 같은 사용자의 중복 동시 요청과 서로 다른 쿠폰의 동시 요청을 추가로 검증합니다.
- Redis 락 획득 시간 초과와 장애 상황에서의 응답을 확인합니다. 현재 테스트의 시간 초과 0건은 시간 초과 처리 경로를 실행한 결과가 아닙니다.
- 낙관적 락에 재시도 정책을 추가할 경우, 재시도 횟수와 최종 실패 응답까지 검증 기준에 포함합니다.

## 관련 코드와 테스트

- [Coupon: 버전을 이용한 충돌 감지](../../src/main/java/dev/portfolio/couponrush/domain/coupon/entity/Coupon.java)
- [CouponIssueService: 수량 변경과 발급 내역 저장](../../src/main/java/dev/portfolio/couponrush/domain/coupon/service/CouponIssueService.java)
- [CouponIssueRedisLockFacade: 락과 트랜잭션의 실행 순서](../../src/main/java/dev/portfolio/couponrush/domain/coupon/service/CouponIssueRedisLockFacade.java)
- [CouponConcurrencyTest](../../src/test/java/dev/portfolio/couponrush/domain/coupon/service/CouponConcurrencyTest.java)
- [CouponRedisLockConcurrencyTest](../../src/test/java/dev/portfolio/couponrush/domain/coupon/service/CouponRedisLockConcurrencyTest.java)
- [ADR-003: Redis 분산 락 선택 근거와 제약](../adr/003-use-redis-lock-for-coupon-issuance.md)
- [프로젝트 README](../../README.md)
