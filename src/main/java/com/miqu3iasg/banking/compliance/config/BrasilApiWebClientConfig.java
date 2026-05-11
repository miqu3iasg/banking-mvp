package com.miqu3iasg.banking.compliance.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(BrasilApiProperties.class)
public class BrasilApiWebClientConfig {

    private final ObjectProvider<WebClient.Builder> webClientBuilderProvider;

    public BrasilApiWebClientConfig(ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        this.webClientBuilderProvider = webClientBuilderProvider;
    }

    @Bean
    public WebClient brasilApiWebClient (BrasilApiProperties props) {
        WebClient.Builder builder = webClientBuilderProvider.getIfAvailable(() -> WebClient.builder());
        var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.connectTimeoutSeconds() * 1000)
                .doOnConnected(connection -> connection.addHandlerLast(
                        new ReadTimeoutHandler(props.readTimeoutSeconds(), TimeUnit.SECONDS)
                ));

        return builder.clone()
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
