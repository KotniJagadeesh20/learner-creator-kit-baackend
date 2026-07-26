package com.learncreator.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CloudflareStreamProperties.class)
public class MediaConfig {

    @Bean
    public RestClient cloudflareRestClient(CloudflareStreamProperties properties) {
        return RestClient.builder()
                .baseUrl("https://api.cloudflare.com/client/v4/accounts/" + properties.accountId() + "/stream")
                .defaultHeader("Authorization", "Bearer " + properties.apiToken())
                .build();
    }
}
