package dev.portfolio.couponrush.common.config;

import dev.portfolio.couponrush.common.security.JwtAccessDeniedHandler;
import dev.portfolio.couponrush.common.security.JwtAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // 비밀번호를 BCrypt 방식으로 단방향 암호화함
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // JWT의 role Claim을 Spring Security 권한으로 변환함
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // JwtGrantedAuthoritiesConverter: JWT에서 권한만 변환함
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

        // JWT에서 권한으로 사용할 Claim 이름을 지정함
        authoritiesConverter.setAuthoritiesClaimName("role");
        // USER를 ROLE_USER 형식으로 변환함
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        // JwtAuthenticationConverter: JWT 전체를 인증된 사용자 객체로 변환함
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return authenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler
    ) throws Exception {
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

                // 인증 및 권한 검사 실패 시 공통 JSON 응답을 반환하도록 설정함
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
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

                        // 쿠폰 생성은 관리자만 접근 가능함
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/coupons"
                        ).hasRole("ADMIN")

                        // 쿠폰 발급은 일반 사용자와 관리자 모두 접근 가능함
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/coupons/*/issues"
                        ).hasAnyRole("USER", "ADMIN")

                        // 주문 생성은 일반 사용자와 관리자 모두 접근 가능함
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/orders"
                        ).hasAnyRole("USER", "ADMIN")

                        // Spring Boot 오류 처리 경로를 허용함
                        .requestMatchers("/error").permitAll()

                        // 위에서 허용하지 않은 나머지 API는 JWT 인증이 필요함
                        .anyRequest().authenticated()
                )

                // Authorization: Bearer 헤더의 JWT를 검증함
                // SpringSecurity에게 현재 서버가 Bearer Token을 받는 ResourceServer라고 설정
                .oauth2ResourceServer(resourceServer -> {
                    // JWT가 잘못되었거나 만료된 경우 401 JSON 응답을 반환함
                    resourceServer.authenticationEntryPoint(
                            jwtAuthenticationEntryPoint
                    );

                    // JWT는 유효하지만 권한이 부족한 경우 403 JSON 응답을 반환함
                    resourceServer.accessDeniedHandler(
                            jwtAccessDeniedHandler
                    );

                    // Bearer Token 중 JWT 방식을 사용한다고 설정
                    resourceServer.jwt(jwtConfigurer -> {
                        // 검증된 JWT를 인증 객체로 변환기 사용하도록 설정
                        jwtConfigurer.jwtAuthenticationConverter(
                                jwtAuthenticationConverter
                        );
                    });
                });

        return httpSecurity.build();
    }
}
