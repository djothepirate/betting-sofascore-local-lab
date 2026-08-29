package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class ChildJvmPlaywrightProviderSupervisorSpringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SupervisorConfiguration.class);

    @Test
    void springUsesTheProductionConstructorAndPublishesBothSupervisorPorts() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ChildJvmPlaywrightProviderSupervisor.class);
            assertThat(context).hasSingleBean(PlaywrightProviderCampaignFactory.class);
            assertThat(context).hasSingleBean(PlaywrightProviderSupervisor.class);

            ChildJvmPlaywrightProviderSupervisor supervisor = context.getBean(
                    ChildJvmPlaywrightProviderSupervisor.class);
            assertThat(context.getBean(PlaywrightProviderCampaignFactory.class))
                    .isSameAs(supervisor);
            assertThat(context.getBean(PlaywrightProviderSupervisor.class))
                    .isSameAs(supervisor);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProviderPlaywrightProperties.class)
    @Import(ChildJvmPlaywrightProviderSupervisor.class)
    static class SupervisorConfiguration {
    }
}
