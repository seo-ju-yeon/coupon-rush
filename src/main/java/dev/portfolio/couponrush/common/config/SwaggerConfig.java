package dev.portfolio.couponrush.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    // Swagger 문서에서 JWT 인증 방식을 식별할 이름
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    // Coupon Rush API 문서의 기본 정보와 JWT 인증 방식을 설정함
    @Bean
    public OpenAPI couponRushOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Coupon Rush API")
                        .version("1.0.0")
                        .description("쿠폰 생성, 발급 및 사용 과정의 동시성 제어를 학습하기 위한 REST API"))
                .components(new Components()
                        // Swagger UI에서 Authorization: Bearer JWT를 입력할 수 있도록 설정함
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}
