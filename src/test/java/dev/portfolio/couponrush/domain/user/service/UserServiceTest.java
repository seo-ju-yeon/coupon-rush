package dev.portfolio.couponrush.domain.user.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.user.dto.UserCreateRequest;
import dev.portfolio.couponrush.domain.user.dto.UserResponse;
import dev.portfolio.couponrush.domain.user.entity.User;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@Testcontainers
@Log4j2
// 실제 Spring Context와 PostgreSQL을 사용하여 사용자 Service를 검증함
class UserServiceTest {

    private static final String TEST_PASSWORD = "password123!";

    // 테스트 클래스 실행 동안 사용할 PostgreSQL 컨테이너를 정의함
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    // Service 테스트에 필요한 Spring Bean을 실제 애플리케이션 컨텍스트에서 주입받음
    @Autowired
    UserService userService;

    // 테스트 데이터 정리에 사용할 Repository를 실제 Spring Bean으로 주입받음
    @Autowired
    UserRepository userRepository;

    // 저장된 비밀번호가 BCrypt 해시와 일치하는지 검증하기 위해 주입받음
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // 테스트 간 사용자 데이터가 섞이지 않도록 기존 데이터를 삭제함
        userRepository.deleteAll();
    }

    @Test
    void createUser() {
        // 사용자 생성 요청을 준비함
        UserCreateRequest request = createRequest(
                "service-create@example.com",
                "tester"
        );

        // Service를 호출하여 사용자를 생성함
        UserResponse response = userService.createUser(request);

        // 실제 DB에 저장된 User 엔티티를 조회함
        // 생성 결과의 id로 DB에서 User를 다시 조회한 뒤, savedUser.getPasswordHash()를 검증하는 것
        User savedUser = userRepository.findById(response.getId()).orElseThrow();

        log.info("사용자 생성 성공: id={}, email={}, nickname={}, createdAt={}",
                response.getId(),
                response.getEmail(),
                response.getNickname(),
                response.getCreatedAt()
        );

        // 생성된 사용자 정보와 응답 변환 결과를 검증함
        assertThat(response.getId()).isNotNull();
        assertThat(response.getEmail()).isEqualTo("service-create@example.com");
        assertThat(response.getNickname()).isEqualTo("tester");
        assertThat(response.getCreatedAt()).isNotNull();

        // 평문 비밀번호가 그대로 저장되지 않았는지 검증함
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(TEST_PASSWORD);

        // 저장된 BCrypt 해시가 입력한 비밀번호가 일치하는지 검증함
        assertThat(
                passwordEncoder.matches(
                        TEST_PASSWORD,
                        savedUser.getPasswordHash()
                )
        ).isTrue();
    }

    @Test
    void createUserWithDuplicateEmailFails() {
        // 동일한 이메일을 사용할 첫 번째 사용자와 두 번째 사용자를 준비함
        UserCreateRequest firstRequest = createRequest("service-duplicate@example.com", "tester1");
        userService.createUser(firstRequest);
        log.info("첫 번째 사용자 생성 성공: email={}", firstRequest.getEmail());

        UserCreateRequest secondRequest = createRequest("service-duplicate@example.com", "tester2");

        // 중복 이메일로 사용자 생성 시 비즈니스 예외가 발생하는지 확인함
        try {
            userService.createUser(secondRequest);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info("중복 이메일 사용자 생성 실패 확인: email={}, message={}",
                    secondRequest.getEmail(),
                    e.getMessage()
            );
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Test
    void getUser() {
        // 조회할 사용자를 먼저 생성함
        UserCreateRequest request = createRequest("service-get@example.com", "tester");
        UserResponse createdUser = userService.createUser(request);

        // 생성된 사용자의 ID로 단건 조회함
        UserResponse foundUser = userService.getUser(createdUser.getId());

        log.info("사용자 단건 조회 성공: id={}, email={}, nickname={}, createdAt={}",
                foundUser.getId(),
                foundUser.getEmail(),
                foundUser.getNickname(),
                foundUser.getCreatedAt()
        );

        // 생성한 사용자와 조회한 사용자의 값이 일치하는지 검증함
        assertThat(foundUser.getId()).isEqualTo(createdUser.getId());
        assertThat(foundUser.getEmail()).isEqualTo("service-get@example.com");
        assertThat(foundUser.getNickname()).isEqualTo("tester");
        assertThat(foundUser.getCreatedAt()).isNotNull();
    }

    @Test
    void getUserWithNotFoundUserFails() {
        // 존재하지 않는 사용자 ID를 준비함
        Long notFoundUserId = 999L;

        // 없는 사용자 조회 시 비즈니스 예외가 발생하는지 확인함
        try {
            userService.getUser(notFoundUserId);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (BusinessException e) {
            log.info("없는 사용자 조회 실패 확인: userId={}, message={}",
                    notFoundUserId,
                    e.getMessage()
            );
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    private UserCreateRequest createRequest(String email, String nickname) {
        // setter 없이 테스트 요청 DTO의 private 필드에 값을 주입함
        UserCreateRequest request = new UserCreateRequest();

        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "nickname", nickname);
        ReflectionTestUtils.setField(request, "password", TEST_PASSWORD);

        return request;
    }
}
