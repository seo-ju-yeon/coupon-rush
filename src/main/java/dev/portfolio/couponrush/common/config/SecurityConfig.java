package dev.portfolio.couponrush.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // 비밀번호를 BCrypt 방식으로 단방향 암호화함
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                // Authorization 헤더의 JWT를 사용하므로 CSRF 보호를 비활성화 함
                .csrf(AbstractHttpConfigurer::disable)

                // HTML 로그인 화면과 HTTP Basic 인증을 사용하지 않음
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // JWT 인증은 서버에 로그인 세션을 저장하지 않음
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS)
                )

                // 요청 URL과 HTTP 메서드에 따라 접근 권한을 설정함
                .authorizeHttpRequests(authorize -> authorize
                        // Swagger 문서는 인증 없이 접근 가능함
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // 회원가입과 로그인은 토큰 발급 전이므로 공개함
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users",
                                "/api/auth/login"
                        ).permitAll()

                        // 쿠폰 목록과 상세 조회는 인증 없이 허용함
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/coupons",
                                "/api/coupons/**"
                        ).permitAll()

                        // Spring Boot 오류 처리 경로를 허용함
                        .requestMatchers("/error").permitAll()

                        // 위에서 허용하지 않은 나머지 API는 JWT 인증이 필요함
                        .anyRequest().authenticated()
                )

                // Authorization: Bearer 헤더의 JWT를 검증함
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                );

        return httpSecurity.build();
    }
}
