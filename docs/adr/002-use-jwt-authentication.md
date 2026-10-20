# ADR-002: Spring Security 기반 JWT 인증·인가 적용

- 상태: `승인`

## 배경과 문제

초기 발급·주문 API는 요청에서 사용자 ID를 받았습니다. 이 값만으로는 요청자가 해당 사용자인지 확인할 수 없습니다. 로그인한 사용자를 식별하고, 쿠폰 생성은 관리자에게만 허용하는 인증·인가가 필요했습니다.

이 프로젝트에서는 세션을 저장하지 않는 API 인증 흐름을 학습하기 위해 JWT를 적용했습니다. 비밀번호를 안전하게 저장하는 일과 토큰으로 요청자를 확인하는 일도 구분했습니다.

## 선택 기준과 검토한 대안

인증 처리를 직접 확장하기보다 Spring Security의 검증 흐름을 사용하고, 인증 실패와 권한 부족 응답을 API의 JSON 형식으로 통일하는 것을 기준으로 삼았습니다.

| 결정 대상 | 대안 | 선택과 이유 |
| --- | --- | --- |
| 인증 상태 관리 | 서버 세션, JWT | JWT로 세션을 저장하지 않는 요청 인증을 학습합니다. 세션 인증도 유효한 대안입니다. |
| 토큰 검증 | 자체 JWT 필터, OAuth2 Resource Server | 프레임워크의 Bearer 토큰 처리와 검증·예외 처리 흐름을 사용합니다. |
| 서명 키 | HS256 대칭 키, RSA 공개 키 | 발급과 검증을 같은 애플리케이션이 맡으므로 HS256으로 구성합니다. 검증 주체가 늘어나면 키 분리를 다시 검토합니다. |
| 로그인 연동 | 자체 로그인, 외부 로그인 제공자 | 현재 범위에서는 이메일·비밀번호 로그인과 JWT 발급을 구현합니다. 외부 제공자 연동은 포함하지 않습니다. |

## 결정과 이유

Spring Security OAuth2 Resource Server로 JWT를 검증합니다. 여기서 Resource Server는 외부 소셜 로그인이나 별도 Authorization Server를 뜻하지 않습니다. 이 애플리케이션이 발급한 토큰을 받는 기능으로 사용합니다.

- `AuthService`는 BCrypt 해시와 입력 비밀번호를 비교한 뒤 액세스 토큰을 발급합니다.
- HS256 키는 Base64 형식의 `JWT_SECRET` 환경변수로 주입하고, Nimbus 기반 인코더·디코더를 사용합니다.
- `JwtConfig`는 서명, 발급자와 만료 시간 검증을 설정합니다.
- 사용자 ID는 `subject`에 담고, `role`은 `ROLE_USER` 또는 `ROLE_ADMIN`으로 변환합니다.
- `SecurityConfig`에서 URL별 접근 권한을 관리하고, 인증 실패는 401, 권한 부족은 403 JSON 응답으로 처리합니다.

발급·주문 API는 요청자가 지정한 사용자 ID 대신 검증된 JWT의 사용자 ID를 사용합니다. 주문할 때는 별도로 발급 쿠폰의 소유자도 확인합니다. 토큰 검증과 개별 자원의 소유권 검사는 서로 다른 책임입니다.

## 적용 흐름

```text
로그인 요청
    → 이메일로 사용자 조회
    → BCrypt로 비밀번호 비교
    → HS256 액세스 토큰 발급

Bearer 토큰을 포함한 API 요청
    → Resource Server에서 JWT 검증
    → role을 Spring Security 권한으로 변환
    → URL 접근 권한 확인
    → Controller와 Service에서 요청 처리
```

Swagger UI에는 Bearer 인증 스키마를 등록했습니다. 로그인 응답의 토큰을 입력하면 보호된 API를 호출할 수 있습니다.

## 검증 결과

| 테스트 | 확인하는 내용 |
| --- | --- |
| `JwtTokenProviderTest` | 실제 인코더·디코더로 클레임과 `exp - iat = 3600초`를 확인하고, 변조한 서명의 토큰을 거부하는지 검사합니다. |
| `AuthServiceTest` | 로그인 성공, 비밀번호 불일치와 존재하지 않는 사용자 처리를 확인합니다. |
| `SecurityConfigTest` | 공개 API 접근, 미인증·잘못된 형식의 토큰에 대한 401, 역할 변환과 권한 부족 403을 확인합니다. |
| `UserServiceTest` | 저장된 비밀번호가 평문과 다르고 BCrypt 비교로 원문과 일치하는지 확인합니다. |

MockMvc의 `jwt()`는 인증 객체를 만들어 인가 규칙을 검사하는 도구입니다. 이를 실제 서명 검증으로 보지 않으며, 서명 검증은 `JwtTokenProviderTest`에서 별도로 확인합니다. 테스트 실행 조건과 저장된 결과는 [테스트 실행 및 검증 기록](../testing.md)에 있습니다.

## 한계와 후속 과제

만료 시간 클레임의 설정은 테스트하지만, 만료된 토큰과 잘못된 발급자의 토큰이 거부되는 경로는 별도 테스트로 확인하지 않았습니다. 실제 로그인에서 받은 토큰으로 보호 API까지 호출하는 전체 흐름의 검증도 보강할 대상입니다.

현재 액세스 토큰만 지원합니다. Refresh Token, 로그아웃과 개별 토큰 폐기는 구현하지 않았으므로 발급한 토큰을 만료 전에 개별 무효화할 수 없습니다. HS256의 비밀 키 보호가 필요하며, 운영 비밀 관리와 키 교체 절차도 검증하지 않았습니다.

## 관련 코드와 테스트

- [JwtConfig](../../src/main/java/dev/portfolio/couponrush/common/security/JwtConfig.java), [JwtTokenProvider](../../src/main/java/dev/portfolio/couponrush/common/security/JwtTokenProvider.java)
- [SecurityConfig](../../src/main/java/dev/portfolio/couponrush/common/config/SecurityConfig.java), [AuthService](../../src/main/java/dev/portfolio/couponrush/domain/auth/service/AuthService.java), [SwaggerConfig](../../src/main/java/dev/portfolio/couponrush/common/config/SwaggerConfig.java)
- [JwtTokenProviderTest](../../src/test/java/dev/portfolio/couponrush/common/security/JwtTokenProviderTest.java), [SecurityConfigTest](../../src/test/java/dev/portfolio/couponrush/common/config/SecurityConfigTest.java)
- [AuthServiceTest](../../src/test/java/dev/portfolio/couponrush/domain/auth/service/AuthServiceTest.java), [UserServiceTest](../../src/test/java/dev/portfolio/couponrush/domain/user/service/UserServiceTest.java)
