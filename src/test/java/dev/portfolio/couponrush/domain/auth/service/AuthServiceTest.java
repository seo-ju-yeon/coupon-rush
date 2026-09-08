package dev.portfolio.couponrush.domain.auth.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.auth.dto.LoginRequest;
import dev.portfolio.couponrush.domain.auth.dto.TokenResponse;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "security.jwt.issuer=coupon-rush",
        "security.jwt.access-token-expiration-seconds=3600"
})
@Log4j2
// 실제 PostgreSQL과 Spring Bean을 사용하여 로그인과 JWT 발급을 검증함
class AuthServiceTest {

    private static final String TEST_PASSWORD = "password123!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        // 테스트 사이에 사용자 데이터가 공유되지 않도록 기존 데이터를 삭제함
        userRepository.deleteAll();
    }

    @Test
    void login() {
        // 로그인에 사용할 사용자를 암호화된 비밀번호와 함께 저장함
        User user = new User(
                "login@example.com",
                "tester",
                passwordEncoder.encode(TEST_PASSWORD)
        );
        userRepository.saveAndFlush(user);

        LoginRequest request = createLoginRequest(
                "login@example.com",
                TEST_PASSWORD
        );

        TokenResponse response = authService.login(request);

        // 로그인 응답에 Access Token 정보가 올바르게 포함되는지 검증함
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);

        // 발급된 JWT를 검증하고 사용자 정보가 Claim에 포함되었는지 확인함
        Jwt jwt = jwtDecoder.decode(response.getAccessToken());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email"))
                .isEqualTo("login@example.com");
        assertThat(jwt.getClaimAsString("role"))
                .isEqualTo("USER");

        // 비밀번호와 Access Token은 로그에 출력하지 않음
        log.info(
                "로그인 성공 확인: userId={}, email={}",
                user.getId(),
                user.getEmail()
        );
    }

    @Test
    void loginWithNotFoundEmailFails() {
        LoginRequest request = createLoginRequest(
                "not-found@example.com",
                TEST_PASSWORD
        );

        try {
            authService.login(request);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            log.info("존재하지 않는 이메일 로그인 실패 확인");
        }
    }

    @Test
    void loginWithWrongPasswordFails() {
        User user = new User(
                "wrong-password@example.com",
                "tester",
                passwordEncoder.encode(TEST_PASSWORD)
        );
        userRepository.saveAndFlush(user);

        LoginRequest request = createLoginRequest(
                "wrong-password@example.com",
                "wrong-password"
        );

        try {
            authService.login(request);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            log.info(
                    "잘못된 비밀번호 로그인 실패 확인: userId={}",
                    user.getId()
            );
        }
    }

    private LoginRequest createLoginRequest(
            String email,
            String password
    ) {
        // setter 없이 요청 DTO의 private 필드에 테스트 값을 주입함
        LoginRequest request = new LoginRequest();

        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);

        return request;
    }
}
