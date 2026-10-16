package io.github.ythibos.edi.web.access;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.application.worker.WorkerTokens;

@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

    @Bean
    ApiToken apiToken(EdiHome home) {
        return ApiToken.issuedInto(home.coreTokenFile());
    }

    @Bean
    FilterRegistrationBean<ApiTokenFilter> apiTokenFilter(ApiToken token, WorkerTokens workerTokens) {
        return new FilterRegistrationBean<>(new ApiTokenFilter(token, workerTokens));
    }

    @Bean
    WebMvcConfigurer callerArguments() {
        return new WebMvcConfigurer() {

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CallerArgument());
            }
        };
    }
}
