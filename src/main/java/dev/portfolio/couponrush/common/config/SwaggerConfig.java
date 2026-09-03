package dev.portfolio.couponrush.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    // Coupon Rush API 문서의 기본 정보를 설정함
    @Bean
    public OpenAPI couponRushOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Coupon Rush API")
                        .version("1.0.0")
                        .description("쿠폰 생성, 발급 및 사용 과정의 동시성 제어를 학습하기 위한 REST API"));
    }
}
