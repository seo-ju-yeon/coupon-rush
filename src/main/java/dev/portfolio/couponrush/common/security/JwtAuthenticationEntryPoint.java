package dev.portfolio.couponrush.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.portfolio.couponrush.common.exception.ErrorCode;
import dev.portfolio.couponrush.common.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log4j2
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    // AuthenticationEntryPoint: 인증되지 않은 요청을 처리하는 Spring Security 인터페이스 (401)

    // ErrorResponse 객체를 JSON 문자열로 변환하기 위해 사용함
    private final ObjectMapper objectMapper;

    @Override
    // commence: JWT가 없거나 인증에 실패했을 때 Spring Security가 호출함
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        log.info("인증되지 않은 요청: method={}, uri={}",
                request.getMethod(),
                request.getRequestURI()
        );

        // HTTP 응답 상태를 401로 설정함
        response.setStatus(errorCode.getStatus().value());

        // 응답 본문이 JSON이며 UTF-8 인코딩을 사용한다고 설정함
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // ErrorResponse 객체를 JSON으로 변환하여 응답 본문에 작성함
        objectMapper.writeValue(
                // Controller 대신 HTTP 응답 본문에 직접 내용을 작성함
                response.getWriter(),
                ErrorResponse.of(errorCode)
        );
    }
}
