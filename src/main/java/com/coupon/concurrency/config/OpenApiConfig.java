package com.coupon.concurrency.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI(Swagger) 설정 클래스
 */
@Configuration
public class OpenApiConfig {

    /**
     * OpenAPI 문서 설정을 정의한다.
     *
     * @return OpenAPI 설정 객체
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("선착순 쿠폰 발급 API")
                        .description("대규모 트래픽(TPS 10,000+) 환경에서 동시성을 제어하는 선착순 쿠폰 발급 시스템")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Coupon Team")
                                .email("coupon@example.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local Server")
                ));
    }
}