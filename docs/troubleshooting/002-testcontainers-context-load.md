# TS-002: Testcontainers를 사용하는데 contextLoads가 로컬 DB에 연결하는 이유

- 상태: `원인 확인` · `개선 예정`

## 문제 상황

저장된 전체 테스트 보고서에서 Repository, Service와 동시성 테스트는 통과했지만 `CouponRushApplicationTests.contextLoads()`는 애플리케이션 컨텍스트를 초기화하지 못했습니다. 이 테스트의 실패 원인에는 다음 메시지가 남아 있었습니다.

```text
Unable to obtain connection from database:
Connection to localhost:5432 refused.
```

여러 통합 테스트에서 이미 Testcontainers를 사용하고 있었기 때문에, 실패한 테스트가 어느 데이터베이스에 연결하려 했는지부터 확인했습니다. 전체 실행 범위와 저장된 결과는 [테스트 검증 기록](../testing.md)에 정리했습니다.

## 원인 분석

`CouponRushApplicationTests`에는 `@SpringBootTest`만 선언되어 있습니다. PostgreSQL 컨테이너나 `@ServiceConnection` 설정은 없습니다. 따라서 애플리케이션의 기본 설정에 있는 `jdbc:postgresql://localhost:5432/coupon_rush`를 사용합니다.

반면 `CouponConcurrencyTest` 등은 테스트 클래스에서 PostgreSQL 컨테이너를 선언하고 `@ServiceConnection`으로 접속 정보를 전달합니다. 다른 테스트 클래스에 이 설정이 있다고 해서 `CouponRushApplicationTests`의 접속 정보까지 자동으로 바뀌지는 않습니다.

실패한 실행에서는 Flyway가 마이그레이션을 준비하는 과정에서 로컬 PostgreSQL 연결이 거절되었습니다. 이 보고서의 직접적인 실패 지점은 애플리케이션 초기화 중의 DB 연결이며, 쿠폰 발급이나 Redis 락 동작을 실행하기 전입니다.

## 대응과 검증

현재 구조로 전체 테스트를 실행하려면 Docker를 실행하고 로컬 PostgreSQL도 준비해야 합니다. 저장소의 `docker-compose.yml`에는 `postgres` 서비스가 정의되어 있으므로 다음과 같이 실행할 수 있습니다.

```bash
docker compose up -d postgres
docker compose exec postgres pg_isready -U couponrush -d coupon_rush
./gradlew test --rerun-tasks
```

`pg_isready`가 연결을 받을 준비가 되었다고 응답한 뒤 테스트를 실행합니다. 이는 현재 테스트가 요구하는 실행 환경을 준비하는 방법입니다. `CouponRushApplicationTests` 자체의 로컬 DB 의존성은 남아 있습니다.

이번 문서화에서는 테스트 클래스의 설정, 기본 데이터베이스 주소, 저장된 실패 로그를 대조하여 원인을 확인했습니다. 위 명령을 새로 실행하거나 성공 결과를 확인하지 않았으므로 상태를 개선 예정으로 남겼습니다.

## 배운 점

테스트 메서드가 비어 있어도 `@SpringBootTest`가 애플리케이션을 시작하면서 데이터베이스 연결을 요구할 수 있습니다. Testcontainers 의존성을 추가했는지뿐 아니라 각 테스트에 실제로 어떤 접속 정보가 적용되는지 확인해야 한다는 점을 배웠습니다.

또한 `Failed to load ApplicationContext`라는 바깥쪽 오류만으로 원인을 판단하기 어렵습니다. 예외의 원인을 따라가 연결 대상과 실패 지점을 확인하면 비즈니스 로직의 문제와 테스트 환경의 문제를 구분할 수 있습니다.

## 남은 과제

- `CouponRushApplicationTests`도 PostgreSQL Testcontainers와 `@ServiceConnection`을 사용하도록 테스트 환경을 통일합니다.
- 변경 후에는 Docker 엔진을 유지하되 로컬 PostgreSQL 서비스를 끈 조건에서 해당 테스트와 전체 테스트를 다시 실행합니다.
- 재실행 결과로 테스트가 로컬 DB 실행 여부에 의존하지 않는지 확인한 뒤 이 문서의 상태와 [테스트 검증 기록](../testing.md)을 갱신합니다.

## 관련 코드와 테스트

- [CouponRushApplicationTests: 기본 컨텍스트 로딩 테스트](../../src/test/java/dev/portfolio/couponrush/CouponRushApplicationTests.java)
- [CouponConcurrencyTest: PostgreSQL 컨테이너 연결 설정](../../src/test/java/dev/portfolio/couponrush/domain/coupon/service/CouponConcurrencyTest.java)
- [application.properties: 기본 데이터베이스 주소와 Flyway 설정](../../src/main/resources/application.properties)
- [docker-compose.yml: 로컬 PostgreSQL 서비스](../../docker-compose.yml)
- [ADR-001: PostgreSQL과 Flyway 사용](../adr/001-use-postgresql-and-flyway.md)
- [프로젝트 README: 실행 방법과 테스트 안내](../../README.md)
