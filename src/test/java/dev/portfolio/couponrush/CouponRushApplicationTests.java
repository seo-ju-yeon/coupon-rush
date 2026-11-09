package dev.portfolio.couponrush;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class CouponRushApplicationTests {
    // @Testcontainers와 @Container는 테스트 동안 PostgreSQL 컨테이너를 시작하고 종료함
    // @ServiceConnection은 컨테이너의 접속 정보를 Spring Boot에 전달함
    //  그래서 Flyway와 JPA가 localhost:5432 대신 테스트 컨테이너에 연결함

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void contextLoads() {
    }

}
