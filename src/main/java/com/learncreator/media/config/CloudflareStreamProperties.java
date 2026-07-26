package com.learncreator.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cloudflare")
public record CloudflareStreamProperties(
        String accountId,
        String apiToken,
        // Used to build public playback URLs, e.g. https://customer-<code>.cloudflarestream.com/<uid>/manifest/video.m3u8
        String customerSubdomain
) {}
