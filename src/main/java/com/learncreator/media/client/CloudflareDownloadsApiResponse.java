package com.learncreator.media.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudflareDownloadsApiResponse(
        boolean success,
        Result result,
        List<CloudflareApiError> errors
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            DownloadInfo audio,
            @JsonProperty("default") DownloadInfo defaultDownload // "default" is a reserved word in Java
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DownloadInfo(
            String status, // "inprogress" | "ready"
            String url,
            Double percentComplete
    ) {}
}
