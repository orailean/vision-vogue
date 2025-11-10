package com.visionvogue.analyzer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vision Vogue Analyzer API")
                        .description("Processes images via external analyzer, stores results, and manages file workflow.")
                        .version("0.0.1")
                        .license(new License().name("Proprietary"))
                        .contact(new Contact().name("Vision Vogue").email("support@example.com"))
                );
    }
}

