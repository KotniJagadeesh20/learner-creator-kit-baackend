package com.learncreator.media.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudflareDirectUploadApiResponse(
        boolean success,
        Result result,
        List<CloudflareApiError> errors
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("uploadURL") String uploadUrl,
            String uid
    ) {}
}
