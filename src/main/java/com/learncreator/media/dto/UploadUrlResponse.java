package com.learncreator.media.dto;

public record UploadUrlResponse(
        // The frontend uploads the raw video file directly to this URL (browser -> Cloudflare,
        // never through our backend) — our server never touches the video bytes.
        String uploadUrl,
        // Save this on the Lesson's videoRef field once upload + creation succeed.
        String videoId
) {}
