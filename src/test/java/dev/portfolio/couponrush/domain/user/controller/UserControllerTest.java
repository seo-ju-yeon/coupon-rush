package dev.portfolio.couponrush.domain.user.controller;

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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Log4j2
class UserControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    // Controller 테스트에 필요한 스프링 빈을 실제 애플리케이션 컨텍스트에서 주입받음
    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void createUser() throws Exception {
        String requestBody = """
                {
                  "email": "controller-create@example.com",
                  "nickname": "tester"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email").value("controller-create@example.com"))
                .andExpect(jsonPath("$.nickname").value("tester"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        log.info("사용자 생성 API 테스트 성공: email={}", "controller-create@example.com");
    }

    @Test
    void getUser() throws Exception {
        User user = userRepository.saveAndFlush(new User("controller-get@example.com", "tester"));

        mockMvc.perform(get("/api/users/{userId}", user.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("controller-get@example.com"))
                .andExpect(jsonPath("$.nickname").value("tester"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        log.info("사용자 단건 조회 API 테스트 성공: userId={}", user.getId());
    }

    @Test
    void createUserWithInvalidEmailFails() throws Exception {
        String requestBody = """
                {
                  "email": "invalid-email",
                  "nickname": "tester"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("이메일 형식이 올바르지 않습니다."));

        log.info("사용자 생성 API 검증 실패 테스트 성공: email={}", "invalid-email");
    }

    @Test
    void createUserWithDuplicateEmailFails() throws Exception {
        userRepository.saveAndFlush(new User("controller-duplicate@example.com", "tester1"));

        String requestBody = """
                {
                  "email": "controller-duplicate@example.com",
                  "nickname": "tester2"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));

        log.info("사용자 생성 API 중복 이메일 실패 테스트 성공: email={}", "controller-duplicate@example.com");
    }

    @Test
    void getUserWithNotFoundUserFails() throws Exception {
        Long notFoundUserId = 999L;

        mockMvc.perform(get("/api/users/{userId}", notFoundUserId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));

        log.info("사용자 단건 조회 API 없는 사용자 실패 테스트 성공: userId={}", notFoundUserId);
    }
}
