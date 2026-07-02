package dev.portfolio.couponrush.domain.user.repository;

import dev.portfolio.couponrush.domain.user.entity.User;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@Testcontainers
@Log4j2
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    UserRepository userRepository;

    @Test
    void saveUser() {
        User user = new User("save@example.com", "tester");
        User saved = userRepository.save(user);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void duplicationEmailFails() {
        User user1 = new User("duplicate@example.com", "tester1");
        userRepository.saveAndFlush(user1);
        log.info("첫 번째 사용자 저장 성공: email={}", user1.getEmail());

        User user2 = new User("duplicate@example.com", "tester2");

        try {
            userRepository.saveAndFlush(user2);
            fail("예외가 발생해야 하는데 발생하지 않았습니다.");
        } catch (DataIntegrityViolationException e) {
            log.info("중복 이메일 저장 실패 확인: email={}", user2.getEmail());
        }
    }

    @Test
    void saveUserWithCreatedAt() {
        User user = new User("created@example.com", "tester");
        User saved = userRepository.saveAndFlush(user);
        log.info("저장된 사용자 생성 일시: {}", saved.getCreatedAt());

        assertThat(saved.getCreatedAt()).isNotNull();
    }

}
