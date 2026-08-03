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
// MockMvc로 사용자 API 요청을 보내 Controller의 응답을 검증함
class UserControllerTest {

    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    // Controller 테스트에 필요한 Spring Bean을 실제 애플리케이션 컨텍스트에서 주입받음
    @Autowired
    MockMvc mockMvc;

    // 테스트 간 사용자 데이터를 정리할 Repository를 주입받음
    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 각 테스트가 독립적으로 실행되도록 기존 사용자를 삭제함
        userRepository.deleteAll();
    }

    @Test
    void createUser() throws Exception {
        // 사용자 생성 API에 전달할 JSON 요청을 준비함
        String requestBody = """
                {
                  "email": "controller-create@example.com",
                  "nickname": "tester"
                }
                """;

        // POST 요청을 보내고 생성 응답을 검증함
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
        // 조회할 사용자를 데이터베이스에 먼저 저장함
        User user = userRepository.saveAndFlush(new User("controller-get@example.com", "tester"));

        // 사용자 단건 조회 API를 호출하고 응답을 검증함
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
        // 이메일 형식이 잘못된 JSON 요청을 준비함
        String requestBody = """
                {
                  "email": "invalid-email",
                  "nickname": "tester"
                }
                """;

        // 요청값 검증 실패 시 공통 오류 응답을 반환하는지 검증함
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
        // 중복 이메일을 가진 기존 사용자를 먼저 저장함
        userRepository.saveAndFlush(new User("controller-duplicate@example.com", "tester1"));

        // 동일한 이메일로 다시 생성 요청할 JSON을 준비함
        String requestBody = """
                {
                  "email": "controller-duplicate@example.com",
                  "nickname": "tester2"
                }
                """;

        // 중복 이메일 요청이 409 오류로 처리되는지 검증함
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
        // 존재하지 않는 사용자 ID를 준비함
        Long notFoundUserId = 999L;

        // 없는 사용자 조회 시 공통 오류 응답을 반환하는지 검증함
        mockMvc.perform(get("/api/users/{userId}", notFoundUserId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));

        log.info("사용자 단건 조회 API 없는 사용자 실패 테스트 성공: userId={}", notFoundUserId);
    }
}
