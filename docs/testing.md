# 테스트 실행과 검증 기록

테스트가 확인하는 조건과 실제 실행에서 관측한 결과를 구분해 기록합니다. README와 ADR에서 공통으로 참고하는 실행 기록입니다.

## 실행 환경과 명령

프로젝트 설정은 Java 21, Spring Boot 3.5, PostgreSQL 16, Redis 7을 사용합니다. 테스트 전용 JWT 키는 `build.gradle`의 `test` 작업에서 주입합니다.

| 테스트 | 데이터베이스·Redis 준비 방식 |
| --- | --- |
| Repository·Service의 DB 통합 테스트 | Testcontainers PostgreSQL |
| `lock` 패키지의 낙관적·비관적 락 테스트 | Testcontainers PostgreSQL |
| Redis 락 테스트 2종 | Testcontainers PostgreSQL과 Redis |
| `CouponRushApplicationTests` | 기본 설정의 `localhost:5432` PostgreSQL |
| 락 없음·`synchronized` 실험 | Java 메모리 객체 |

전체 테스트를 실행하려면 Docker를 시작하고 로컬 PostgreSQL이 준비되었는지 확인합니다.

```bash
docker compose up -d
docker compose exec postgres pg_isready -U couponrush -d coupon_rush
./gradlew test --rerun-tasks
```

동시성 실험만 실행할 때는 Docker가 실행 중이면 해당 테스트가 컨테이너를 준비합니다.

```bash
./gradlew test --rerun-tasks \
  --tests 'dev.portfolio.couponrush.lock.*' \
  --tests 'dev.portfolio.couponrush.domain.coupon.service.CouponConcurrencyTest' \
  --tests 'dev.portfolio.couponrush.domain.coupon.service.CouponRedisLockConcurrencyTest'
```

Gradle HTML 보고서는 실행 후 `build/reports/tests/test/index.html`, XML 보고서는 `build/test-results/test/`에 생성됩니다. 다음 테스트 실행으로 보고서가 바뀔 수 있으므로, 비교할 결과는 실행 조건과 함께 별도로 보관해야 합니다.

## 보관된 실행 기록

- 보고서 실행 시각: 2026-10-19 14:19~14:20 KST
- 자료: 문서 정리 시점에 남아 있던 `build/test-results/test/TEST-*.xml` 26개
- 코드 대조 기준: `lock-lab`, `038a7a6`
- 이번 문서 정리에서는 테스트를 재실행하지 않았습니다. 원본 XML에는 실행 커밋이 기록되어 있지 않아 위 커밋에서 생성된 결과라고 확정하지 않습니다.

| 전체 테스트 수 | 성공 | 실패 | 건너뜀 |
| ---: | ---: | ---: | ---: |
| 81 | 80 | 1 | 0 |

실패한 테스트는 `CouponRushApplicationTests.contextLoads()`입니다. 다른 80개 테스트가 성공했다는 기록을 전체 테스트 통과로 해석하지 않습니다.

### 쿠폰 발급 동시성

두 테스트 모두 서로 다른 사용자 20명, 발급 가능 수량 5개, 한 JVM의 20개 작업 스레드를 사용합니다. 요청의 시작을 `CountDownLatch`로 맞추고, 작업 종료 후 DB 상태를 조회합니다. HTTP 요청을 보내는 테스트는 아닙니다.

| 호출 경로 | 성공 | 품절 | 낙관적 락 충돌 | Redis 락 타임아웃 | 예상 밖 예외 | DB 발급 수량 / 내역 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `CouponIssueService` | 5 | 11 | 4 | 해당 없음 | 0 | 5 / 5 |
| `CouponIssueRedisLockFacade` | 5 | 15 | 0 | 0 | 0 | 5 / 5 |

관측값의 원본은 다음 두 보고서의 `system-out`에 있는 결과 로그입니다. 생성 파일은 Git에서 제외되어 있으므로 핵심 필드를 아래에 보존합니다.

- `TEST-dev.portfolio.couponrush.domain.coupon.service.CouponConcurrencyTest.xml`
- `TEST-dev.portfolio.couponrush.domain.coupon.service.CouponRedisLockConcurrencyTest.xml`

```text
낙관적 락 동시 발급 결과:
requestCount=20, successCount=5, businessFailureCount=11,
optimisticLockFailureCount=4, unexpectedFailureCount=0,
issuedQuantity=5, issueCount=5, businessFailureCodes=[COUPON_SOLD_OUT]

Redis 락 동시 발급 결과:
requestCount=20, successCount=5, soldOutCount=15, lockTimeoutCount=0,
optimisticLockFailureCount=0, unexpectedFailureCount=0,
issuedQuantity=5, issueCount=5
```

낙관적 락 테스트가 요구하는 성공 건수는 1~5건이며, 실행마다 5건을 보장하지 않습니다. 이 기록에서는 5건이 성공했으므로 재고가 남는 현상을 관측한 자료로 사용할 수 없습니다. Redis 테스트의 타임아웃 0건은 정상 조건의 결과이며, 타임아웃 예외 처리 경로를 실행했다는 뜻은 아닙니다.

실패 사유와 정합성 검증을 구분한 과정은 [TS-001](troubleshooting/001-optimistic-lock-conflict.md)에 정리했습니다.

### 애플리케이션 컨텍스트 로딩 실패

`TEST-dev.portfolio.couponrush.CouponRushApplicationTests.xml`에는 다음 연결 실패가 남아 있습니다.

```text
Connection to localhost:5432 refused.
Caused by: java.net.ConnectException: Connection refused
```

해당 테스트에는 Testcontainers 연결 설정이 없어 기본 datasource를 사용합니다. 로컬 PostgreSQL을 준비한 뒤의 재실행 결과는 이 기록에 포함되어 있지 않습니다. 원인 분석과 테스트 환경을 통일할 계획은 [TS-002](troubleshooting/002-testcontainers-context-load.md)를 참고합니다.

## 아직 검증하지 않은 범위

- 여러 애플리케이션 인스턴스 사이의 락 공유와 HTTP 요청을 통한 전체 발급 경로
- Redis 장애·네트워크 단절·failover·락 타임아웃·인터럽트 처리 경로
- 동일 사용자 중복 동시 요청과 서로 다른 쿠폰의 독립적인 진행
- 동일한 조건에서의 락 방식별 처리량·지연 시간 비교와 비관적 락 대기 시간 계측
- 만료된 JWT 및 잘못된 발급자 JWT의 거부 테스트
- README의 로컬 수동 데모 절차를 따른 전체 API 흐름의 실행 확인

## 관련 코드와 문서

- [Gradle 테스트 설정](../build.gradle)
- [기본 애플리케이션 설정](../src/main/resources/application.properties)
- [CouponConcurrencyTest](../src/test/java/dev/portfolio/couponrush/domain/coupon/service/CouponConcurrencyTest.java)
- [CouponRedisLockConcurrencyTest](../src/test/java/dev/portfolio/couponrush/domain/coupon/service/CouponRedisLockConcurrencyTest.java)
- [CouponRushApplicationTests](../src/test/java/dev/portfolio/couponrush/CouponRushApplicationTests.java)
- [ADR 목록](adr/README.md)
- [트러블슈팅 목록](troubleshooting/README.md)
