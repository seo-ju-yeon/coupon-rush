# ADR-001: PostgreSQL과 Flyway 기반 데이터베이스 환경 구성

- 상태: `승인`

## 배경과 문제

쿠폰 발급과 주문은 사용자, 쿠폰, 발급 내역 사이의 관계를 유지해야 합니다. 이메일 중복, 동일 사용자의 중복 발급, 같은 쿠폰의 중복 사용을 애플리케이션 검사만으로 막으면 동시 요청이나 다른 저장 경로에서 규칙을 놓칠 수 있습니다.

데이터베이스에도 필요한 제약을 두고, 실행 환경과 스키마 변경을 저장소에서 재현할 수 있도록 구성했습니다. 이는 특정 운영 장애를 해결한 기록이 아니라 프로젝트의 데이터 관리 기준을 정한 결정입니다.

## 선택 기준과 검토한 대안

관계와 제약을 실제 DB에서 확인할 수 있는지, 스키마 변경 이력을 추적할 수 있는지, 테스트가 개인 DB 상태에 의존하지 않는지를 기준으로 삼았습니다.

| 결정 대상 | 대안 | 선택과 이유 |
| --- | --- | --- |
| 데이터베이스 | PostgreSQL, MariaDB | PostgreSQL을 기준으로 SQL, 제약조건과 락 동작을 학습합니다. 제품 간 성능 우위를 비교한 선택은 아닙니다. |
| 로컬 실행 | 직접 설치, Docker Compose | Compose로 이미지, 포트와 볼륨을 공유합니다. |
| 스키마 변경 | 수동 SQL, Hibernate `create`·`update`, Flyway | Flyway 파일로 적용 순서를 관리하고 Hibernate는 `validate`로 매핑을 검사합니다. |
| 통합 테스트 | H2, 로컬 PostgreSQL, Testcontainers | Testcontainers의 PostgreSQL로 실제 사용할 DB의 동작을 확인하고 개인 데이터와 분리합니다. |

## 결정과 이유

PostgreSQL 16을 Docker Compose와 Testcontainers에서 사용합니다. Flyway가 스키마를 변경하고, Hibernate의 `ddl-auto=validate`는 엔티티 매핑과 스키마의 불일치를 확인하는 역할로 한정합니다.

DB 제약과 애플리케이션의 동시성 제어는 역할이 다릅니다.

| 수단 | 담당하는 규칙 |
| --- | --- |
| 외래 키 | 발급 내역과 주문이 존재하는 사용자·쿠폰을 참조하도록 합니다. |
| 고유 제약 | 이메일, 사용자별 동일 쿠폰 발급, 발급 쿠폰별 주문의 중복을 제한합니다. |
| CHECK 제약 | 허용된 상태와 금액 범위, `0 <= issued_quantity <= total_quantity`를 제한합니다. |
| 트랜잭션과 락 | 발급 수량 변경과 발급 내역 저장을 함께 처리하고 동시 변경을 제어합니다. |

수량 CHECK는 쿠폰 행의 값만 검사합니다. 발급 수량과 발급 내역 건수가 같은지까지 보장하지 않으므로, 두 값을 함께 확인하는 동시성 테스트를 별도로 둡니다. 락 구성은 [ADR-003](003-use-redis-lock-for-coupon-issuance.md)에 기록했습니다.

## 적용 흐름

```text
로컬: Docker Compose로 PostgreSQL 실행
테스트: Testcontainers로 PostgreSQL 실행
    → Spring Boot 시작
    → Flyway가 미적용 마이그레이션 실행
    → Hibernate가 엔티티 매핑 검증
    → 애플리케이션 또는 통합 테스트 실행
```

초기 테이블과 제약은 `V1`에 정의하고, 사용자 역할과 비밀번호 필드는 `V2`, `V3`로 추가했습니다. 적용된 마이그레이션을 고치는 대신 새 버전으로 변경을 이어갑니다.

## 검증 결과

아래 테스트는 PostgreSQL 컨테이너를 사용합니다. 공통 실행 조건과 저장된 실행 결과는 [테스트 실행 및 검증 기록](../testing.md)에서 확인할 수 있습니다.

| 테스트 | 확인하는 내용 |
| --- | --- |
| `UserRepositoryTest` | 사용자 저장과 동일 이메일 저장 거부를 확인합니다. |
| `CouponIssueRepositoryTest` | 발급 내역 저장과 동일 사용자·쿠폰 조합의 중복 저장 거부를 확인합니다. |
| `OrderRepositoryTest` | 주문 저장과 동일 발급 쿠폰을 사용한 중복 주문 거부를 확인합니다. |
| `CouponRedisLockConcurrencyTest` | 동시 발급 후 쿠폰의 발급 수량과 발급 내역이 모두 5인지 확인합니다. |

고유 제약을 직접 위반하는 테스트와 스키마에 제약이 정의되어 있다는 사실은 구분합니다. 모든 CHECK·외래 키를 위반하는 경우를 각각 테스트한 것은 아닙니다.

## 한계와 후속 과제

Docker가 필요하고 컨테이너 시작 비용이 발생합니다. 운영 데이터가 있는 상태의 마이그레이션 시간, 배포 절차와 복구 절차는 검증하지 않았습니다.

기본 `CouponRushApplicationTests`는 아직 Testcontainers를 사용하지 않아 로컬 PostgreSQL에 의존합니다. 전체 테스트 환경을 통일하는 과제는 [전체 테스트의 로컬 DB 의존](../troubleshooting/002-testcontainers-context-load.md)에 정리했습니다. `ddl-auto=validate`만으로 모든 DB 제약과 업무 규칙까지 검증했다고 보지 않습니다.

## 관련 코드와 테스트

- [docker-compose.yml](../../docker-compose.yml), [application.properties](../../src/main/resources/application.properties)
- [V1 초기 스키마](../../src/main/resources/db/migration/V1__init_schema.sql), [V2 사용자 역할](../../src/main/resources/db/migration/V2__add_user_role.sql), [V3 비밀번호](../../src/main/resources/db/migration/V3__add_user_password.sql)
- [UserRepositoryTest](../../src/test/java/dev/portfolio/couponrush/domain/user/repository/UserRepositoryTest.java)
- [CouponIssueRepositoryTest](../../src/test/java/dev/portfolio/couponrush/domain/coupon/repository/CouponIssueRepositoryTest.java), [OrderRepositoryTest](../../src/test/java/dev/portfolio/couponrush/domain/order/repository/OrderRepositoryTest.java)
- [CouponRedisLockConcurrencyTest](../../src/test/java/dev/portfolio/couponrush/domain/coupon/service/CouponRedisLockConcurrencyTest.java)
