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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log4j2
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {
    // AccessDeniedHandler: 인증은 됐지만 권한이 부족한 요청을 처리 (403)

    // ErrorResponse 객체를 JSON 문자열로 변환하기 위해 사용함
    private final ObjectMapper objectMapper;


    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;

        log.info("접근 권한이 없는 요청: method={}, uri={}",
                request.getMethod(),
                request.getRequestURI()
        );

        // HTTP 응답 상태를 403으로 설정함
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
