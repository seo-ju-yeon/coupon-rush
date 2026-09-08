package dev.portfolio.couponrush.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "security.jwt.issuer=coupon-rush",
        "security.jwt.access-token-expiration-seconds=3600"
})
// 공개 API와 JWT 인증이 필요한 API가 올바르게 구분되는지 검증함
class SecurityConfigTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void accessPublicCouponApiWithoutToken() throws Exception {
        // 공개 API는 JWT가 없어도 접근할 수 있는지 검증함
        mockMvc.perform(get("/api/coupons"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void accessProtectedApiWithoutTokenFails() throws Exception {
        // 보호 API는 JWT가 없으면 Controller 실행 전에 401로 차단되는지 검증함
        mockMvc.perform(get("/api/users/{userId}", 999L))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessProtectedApiWithJwt() throws Exception {
        // 테스트용 JWT 인증 정보를 SecurityContext에 넣어 보호 API를 호출함
        mockMvc.perform(get("/api/users/{userId}", 999L)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("email", "security@example.com")
                                .claim("role", "USER")
                        )))
                .andDo(print())
                // 인증은 통과했지만 사용자가 없으므로 Controller에서 404를 반환함
                .andExpect(status().isNotFound());
    }

    @Test
    void accessPublicLoginApiWithoutToken() throws Exception {
        String invalidRequestBody = """
                {
                  "email": "",
                  "password": ""
                }
                """;

        // 로그인 API는 JWT 없이 Controller의 요청값 검증까지 도달하는지 확인함
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void convertRoleClaimToAuthority() {
        Instant issuedAt = Instant.now();

        // 권한 변환 테스트에 사용할 가짜 JWT 객체를 생성함
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("email", "security@example.com")
                .claim("role", "USER")
                .build();

        // JWT를 Spring Security 인증 객체로 변환함
        AbstractAuthenticationToken authentication =
                jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication).isNotNull();

        // JWT의 USER가 Spring Security의 ROLE_USER로 변환되었는지 검증함
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }
}
