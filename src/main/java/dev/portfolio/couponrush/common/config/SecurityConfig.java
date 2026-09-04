package dev.portfolio.couponrush.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

                // JWT 구현 전까지 기존 API와 테스트가 동작하도록 임시 허용함
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll());

        return httpSecurity.build();
    }
}
