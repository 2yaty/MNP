package com.fourgtss.mnp.config;

import java.util.List;

import com.fourgtss.mnp.auth.AuthenticatedOperatorArgumentResolver;
import com.fourgtss.mnp.auth.OrganizationHeaderInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Every API endpoint lives under this prefix; Actuator and Swagger stay outside it. */
    public static final String API_PATH_PATTERN = "/api/**";

    private final OrganizationHeaderInterceptor organizationHeaderInterceptor;

    public WebConfig(OrganizationHeaderInterceptor organizationHeaderInterceptor) {
        this.organizationHeaderInterceptor = organizationHeaderInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(organizationHeaderInterceptor).addPathPatterns(API_PATH_PATTERN);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedOperatorArgumentResolver());
    }
}
