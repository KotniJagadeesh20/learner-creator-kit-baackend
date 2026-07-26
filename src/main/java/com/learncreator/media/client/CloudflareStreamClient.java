package com.learncreator.media.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Thin wrapper around Cloudflare Stream's HTTP API. Nothing outside this package should ever
 * see a Cloudflare*ApiResponse type — MediaService translates these into our own DTOs, so a
 * future switch to Bunny/Mux/self-hosted only touches this file and MediaService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CloudflareStreamClient {

    private final RestClient cloudflareRestClient;

    private static final int MAX_DURATION_SECONDS = 3 * 60 * 60; // 3 hours — generous ceiling for a single lesson video

    public CloudflareDirectUploadApiResponse createDirectUpload() {
        try {
            CloudflareDirectUploadApiResponse response = cloudflareRestClient.post()
                    .uri("/direct_upload")
                    .body(Map.of(
                            "maxDurationSeconds", MAX_DURATION_SECONDS,
                            "requireSignedURLs", false
                    ))
                    .retrieve()
                    .body(CloudflareDirectUploadApiResponse.class);

            if (response == null || !response.success()) {
                log.error("Cloudflare direct_upload failed: {}", response != null ? response.errors() : "null response");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create video upload slot. Please try again.");
            }
            return response;

        } catch (RestClientException e) {
            log.error("Cloudflare direct_upload request failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Video upload service is currently unavailable.");
        }
    }

    public CloudflareVideoDetailApiResponse getVideoDetail(String videoId) {
        try {
            CloudflareVideoDetailApiResponse response = cloudflareRestClient.get()
                    .uri("/{videoId}", videoId)
                    .retrieve()
                    .body(CloudflareVideoDetailApiResponse.class);

            if (response == null || !response.success()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
            }
            return response;

        } catch (RestClientException e) {
            log.error("Cloudflare video detail request failed for videoId={}", videoId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Video upload service is currently unavailable.");
        }
    }
}
