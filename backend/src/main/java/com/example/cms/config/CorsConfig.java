package com.example.cms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CorsConfig {
    // 本機/測試用的固定來源，正式網域透過 cms.cors.allowed-origins（CMS_CORS_ALLOWED_ORIGINS）額外加入，
    // 不取代這份清單，避免正式環境設定疏漏時連本機開發都一起壞掉。
    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost:4200",
            "http://127.0.0.1:4200",
            "http://localhost:4301",
            "http://127.0.0.1:4301"
    );

    private final RoleAccessInterceptor roleAccessInterceptor;
    private final String extraAllowedOrigins;

    public CorsConfig(org.springframework.beans.factory.ObjectProvider<RoleAccessInterceptor> roleAccessInterceptor,
                       @Value("${cms.cors.allowed-origins:}") String extraAllowedOrigins) {
        this.roleAccessInterceptor = roleAccessInterceptor.getIfAvailable();
        this.extraAllowedOrigins = extraAllowedOrigins;
    }

    @Bean
    WebMvcConfigurer corsConfigurer() {
        List<String> allowedOrigins = new ArrayList<>(DEFAULT_ALLOWED_ORIGINS);
        if (extraAllowedOrigins != null) {
            for (String origin : extraAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    allowedOrigins.add(trimmed);
                }
            }
        }
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins.toArray(new String[0]))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }

            @Override
            public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                if (roleAccessInterceptor != null) registry.addInterceptor(roleAccessInterceptor);
            }
        };
    }
}
