package com.pdipatria.sensibo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@Profile("poll")
@EnableScheduling
public class PollingConfiguration {
    public PollingConfiguration(Environment environment,
            @Value("${sensibo.poll.interval:PT5M}") Duration interval,
            @Value("${sensibo.poll.initial-delay:PT10S}") Duration initialDelay) {
        Assert.state(!environment.acceptsProfiles(Profiles.of("import")),
                "The import and poll profiles cannot be enabled together");
        Assert.isTrue(!interval.isNegative() && !interval.isZero(), "Polling interval must be positive");
        Assert.isTrue(!initialDelay.isNegative(), "Polling initial delay must not be negative");
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("sensibo-poll-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(70);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }
}
