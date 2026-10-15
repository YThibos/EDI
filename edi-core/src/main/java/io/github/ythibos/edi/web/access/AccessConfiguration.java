package io.github.ythibos.edi.web.access;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.ythibos.edi.EdiHome;

@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

    @Bean
    ApiToken apiToken(EdiHome home) {
        return ApiToken.issuedInto(home.coreTokenFile());
    }

    @Bean
    FilterRegistrationBean<ApiTokenFilter> apiTokenFilter(ApiToken token) {
        return new FilterRegistrationBean<>(new ApiTokenFilter(token));
    }
}
