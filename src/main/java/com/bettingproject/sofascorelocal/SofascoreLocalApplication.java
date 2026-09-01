package com.bettingproject.sofascorelocal;

import com.bettingproject.sofascorelocal.adapter.bettingproject.transport.JdkHttpClientNoAutomaticRetryPolicy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
public class SofascoreLocalApplication {

    public static void main(String[] args) {
        JdkHttpClientNoAutomaticRetryPolicy.recordApplicationStartup();
        SpringApplication.run(SofascoreLocalApplication.class, args);
    }
}
