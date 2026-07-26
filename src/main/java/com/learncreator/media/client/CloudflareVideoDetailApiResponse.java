package com.learncreator.media.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudflareVideoDetailApiResponse(
        boolean success,
        Result result,
        List<CloudflareApiError> errors
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            String uid,
            boolean readyToStream,
            Status status,
            String thumbnail,
            Playback playback,
            Double duration
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(String state, String errorReasonText) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Playback(String hls, String dash) {}
}
