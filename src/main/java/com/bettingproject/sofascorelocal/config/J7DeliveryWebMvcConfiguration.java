package com.bettingproject.sofascorelocal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the resolved-handler boundary before any J7 delivery controller invocation. */
@Configuration(proxyBeanMethods = false)
public class J7DeliveryWebMvcConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new J7DeliveryLocalRequestBoundaryInterceptor())
                .order(Ordered.HIGHEST_PRECEDENCE);
    }
}
