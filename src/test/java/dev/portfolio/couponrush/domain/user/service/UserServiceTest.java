package dev.portfolio.couponrush.domain.user.service;

import dev.portfolio.couponrush.common.exception.BusinessException;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.domain.user.dto.UserCreateRequest;
import dev.portfolio.couponrush.domain.user.dto.UserResponse;
import dev.portfolio.couponrush.domain.user.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@Testcontainers
@Log4j2
class UserServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    // Service 테스트에 필요한 스프링 빈을 실제 애플리케이션 컨텍스트에서 주입받음
    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void createUser() {
        UserCreateRequest request = createRequest("service-create@example.com", "tester");

        UserResponse response = userService.createUser(request);

        log.info("사용자 생성 성공: id={}, email={}, nickname={}, createdAt={}",
                response.getId(),
                response.getEmail(),
                response.getNickname(),
                response.getCreatedAt()
        );

        assertThat(response.getId()).isNotNull();
        assertThat(response.getEmail()).isEqualTo("service-create@example.com");
        assertThat(response.getNickname()).isEqualTo("tester");
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    void createUserWithDuplicateEmailFails() {
        UserCreateRequest firstRequest = createRequest("service-duplicate@example.com", "tester1");
        userService.createUser(firstRequest);
        log.info("첫 번째 사용자 생성 성공: email={}", firstRequest.getEmail());

        UserCreateRequest secondRequest = createRequest("service-duplicate@example.com", "tester2");

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
        UserCreateRequest request = createRequest("service-get@example.com", "tester");
        UserResponse createdUser = userService.createUser(request);

        UserResponse foundUser = userService.getUser(createdUser.getId());

        log.info("사용자 단건 조회 성공: id={}, email={}, nickname={}, createdAt={}",
                foundUser.getId(),
                foundUser.getEmail(),
                foundUser.getNickname(),
                foundUser.getCreatedAt()
        );

        assertThat(foundUser.getId()).isEqualTo(createdUser.getId());
        assertThat(foundUser.getEmail()).isEqualTo("service-get@example.com");
        assertThat(foundUser.getNickname()).isEqualTo("tester");
        assertThat(foundUser.getCreatedAt()).isNotNull();
    }

    @Test
    void getUserWithNotFoundUserFails() {
        Long notFoundUserId = 999L;

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
        UserCreateRequest request = new UserCreateRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "nickname", nickname);
        return request;
    }
}
