package dev.portfolio.couponrush.domain.auth.controller;

import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "security.jwt.issuer=coupon-rush",
        "security.jwt.access-token-expiration-seconds=3600"
})
@Log4j2
// MockMvc로 로그인 API 요청을 보내 응답 상태와 JSON 형식을 검증함
class AuthControllerTest {

    private static final String TEST_PASSWORD = "password123!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // 각 테스트가 독립적으로 실행되도록 기존 사용자를 삭제함
        userRepository.deleteAll();
    }

    @Test
    void login() throws Exception {
        // 로그인할 사용자를 암호화된 비밀번호와 함께 저장함
        User user = new User(
                "controller-login@example.com",
                "tester",
                passwordEncoder.encode(TEST_PASSWORD)
        );
        userRepository.saveAndFlush(user);

        String requestBody = """
                {
                  "email": "controller-login@example.com",
                  "password": "password123!"
                }
                """;

        // 로그인 성공 시 JWT 응답이 반환되는지 검증함
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        log.info("로그인 API 성공 테스트 확인: userId={}", user.getId());
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        User user = new User(
                "wrong-password@example.com",
                "tester",
                passwordEncoder.encode(TEST_PASSWORD)
        );
        userRepository.saveAndFlush(user);

        String requestBody = """
                {
                  "email": "wrong-password@example.com",
                  "password": "wrong-password"
                }
                """;

        // 비밀번호가 일치하지 않으면 401 공통 오류 응답을 반환하는지 검증함
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("이메일 또는 비밀번호가 올바르지 않습니다."));

        log.info("잘못된 비밀번호 로그인 API 실패 테스트 확인");
    }

    @Test
    void loginWithNotFoundEmailFails() throws Exception {
        String requestBody = """
                {
                  "email": "not-found@example.com",
                  "password": "password123!"
                }
                """;

        // 존재하지 않는 이메일도 비밀번호 오류와 같은 401 응답으로 처리되는지 검증함
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("이메일 또는 비밀번호가 올바르지 않습니다."));

        log.info("존재하지 않는 이메일 로그인 API 실패 테스트 확인");
    }

    @Test
    void loginWithInvalidEmailFails() throws Exception {
        String requestBody = """
                {
                  "email": "invalid-email",
                  "password": "password123!"
                }
                """;

        // 이메일 형식이 잘못되면 Service 호출 전에 요청값 검증에서 차단되는지 확인함
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("이메일 형식이 올바르지 않습니다."));

        log.info("로그인 API 이메일 형식 검증 실패 테스트 확인");
    }
}
