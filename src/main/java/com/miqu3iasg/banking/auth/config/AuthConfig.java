package com.miqu3iasg.banking.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;

@Configuration
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig implements AsyncConfigurer {

    private final WebClient.Builder webClientBuilder;

    public AuthConfig(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Bean("hibpWebClient")
    public WebClient hibpWebClient() {
        return webClientBuilder.clone()
                .baseUrl("https://api.pwnedpasswords.com")
                .build();
    }

    @Bean(name = "authTaskExecutor")
    public Executor authTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("auth-async-");
        executor.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, java.util.concurrent.ThreadPoolExecutor pool) {
                org.slf4j.LoggerFactory.getLogger(AuthConfig.class)
                    .error("Audit log task rejected — queue full. Increase pool size or investigate DB performance.");
                // Run the task in the calling thread to ensure audit events are not lost
                r.run();
            }
        });
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return authTaskExecutor();
    }
}
