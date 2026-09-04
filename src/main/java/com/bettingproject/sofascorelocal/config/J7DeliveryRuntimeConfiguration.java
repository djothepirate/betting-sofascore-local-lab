package com.bettingproject.sofascorelocal.config;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Pure composition; creating these beans performs no certificate-store or network I/O. */
@Configuration(proxyBeanMethods = false)
public class J7DeliveryRuntimeConfiguration {

    @Bean
    J7DeliveryPolicy j7DeliveryPolicy(OptionalLocalPushProperties properties) {
        return new J7DeliveryPolicy(properties);
    }
}
